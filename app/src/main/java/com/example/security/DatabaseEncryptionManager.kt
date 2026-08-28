package com.example.security

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlatformSQLiteDatabase
import android.util.Base64
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * Owns the SQLCipher passphrase and the one-time conversion of legacy plaintext databases.
 *
 * The passphrase is random, wrapped by Android Keystore through [SecureSecretStore], and never
 * written to ordinary preferences. Portable backups deliberately contain a plaintext SQLite
 * snapshot inside their authenticated encrypted container, so they do not depend on this device.
 */
object DatabaseEncryptionManager {
    private const val TAG = "DatabaseEncryption"
    private const val PASSPHRASE_SECRET = "database_passphrase_v1"
    private const val SQLITE_HEADER = "SQLite format 3\u0000"

    @Volatile
    private var processPassphrase: ByteArray? = null

    @Synchronized
    fun passphrase(context: Context): ByteArray {
        processPassphrase?.let { return it.copyOf() }
        val existing = SecureSecretStore.get(context, PASSPHRASE_SECRET)
        if (!existing.isNullOrBlank()) {
            return existing.toByteArray(Charsets.US_ASCII).also { processPassphrase = it.copyOf() }
        }

        val generated = ByteArray(32).also(SecureRandom()::nextBytes)
        val encoded = Base64.encodeToString(generated, Base64.NO_WRAP)
        generated.fill(0)
        SecureSecretStore.put(context, PASSPHRASE_SECRET, encoded)
        return encoded.toByteArray(Charsets.US_ASCII).also { processPassphrase = it.copyOf() }
    }

    fun currentProcessPassphrase(): ByteArray {
        return processPassphrase?.copyOf() ?: ByteArray(32).also { processPassphrase = it.copyOf() }
    }

    /** Converts an existing plaintext Room database to SQLCipher before Room opens it. */
    @Synchronized
    fun prepareEncryptedDatabase(context: Context, databaseName: String, passphrase: ByteArray) {
        loadLibrary()
        val database = context.getDatabasePath(databaseName)
        val rollback = File(database.parentFile, "$databaseName.plaintext-rollback")
        val staged = File(database.parentFile, "$databaseName.encrypted-staged")

        try {
            // Recover a process interruption between staging and the atomic file replacement.
            if (!database.exists() && rollback.isFile) {
                rollback.renameTo(database)
            }
            if (database.isFile && !isPlaintextSqlite(database)) {
                try {
                    verify(database, passphrase)
                    rollback.delete()
                    staged.delete()
                    return
                } catch (e: Throwable) {
                    Log.e(TAG, "Encrypted database failed verification, archiving corrupted DB: ${e.message}", e)
                    // If decryption fails (e.g. key lost after clear data), back up corrupt db and allow fresh init
                    val corruptBackup = File(database.parentFile, "$databaseName.corrupted-${System.currentTimeMillis()}")
                    database.renameTo(corruptBackup)
                    File(database.path + "-wal").delete()
                    File(database.path + "-shm").delete()
                    rollback.delete()
                    staged.delete()
                    return
                }
            }
            if (!database.isFile) {
                rollback.delete()
                staged.delete()
                return
            }

            // Merge committed WAL frames before opening the file through SQLCipher's plaintext mode.
            try {
                PlatformSQLiteDatabase.openDatabase(
                    database.path,
                    null,
                    PlatformSQLiteDatabase.OPEN_READWRITE
                ).use { plain ->
                    plain.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "WAL checkpoint failed during plaintext migration: ${e.message}")
            }

            staged.delete()
            exportDatabase(database, ByteArray(0), staged, passphrase)
            verify(staged, passphrase)

            rollback.delete()
            if (database.renameTo(rollback)) {
                if (staged.renameTo(database)) {
                    verify(database, passphrase)
                    File(database.path + "-wal").delete()
                    File(database.path + "-shm").delete()
                    rollback.delete()
                } else {
                    rollback.renameTo(database)
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Error in prepareEncryptedDatabase: ${error.message}", error)
            if (rollback.isFile && !database.isFile) {
                rollback.renameTo(database)
            }
        } finally {
            staged.delete()
        }
    }

    /** Converts a consistent SQLCipher snapshot into plaintext for the outer encrypted backup. */
    fun exportPortablePlaintextSnapshot(
        encryptedSnapshot: File,
        plaintextDestination: File,
        passphrase: ByteArray
    ) {
        loadLibrary()
        require(encryptedSnapshot.isFile) { "Encrypted snapshot is missing" }
        plaintextDestination.delete()
        exportDatabase(encryptedSnapshot, passphrase, plaintextDestination, ByteArray(0))
        check(isPlaintextSqlite(plaintextDestination)) { "Portable SQLite snapshot was not created" }
        PlatformSQLiteDatabase.openDatabase(
            plaintextDestination.path,
            null,
            PlatformSQLiteDatabase.OPEN_READONLY
        ).use { database ->
            database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)) {
                    "Portable SQLite snapshot failed integrity validation"
                }
            }
        }
    }

    fun readEncryptedSchema(database: File, passphrase: ByteArray): Int {
        loadLibrary()
        if (isPlaintextSqlite(database)) return readPlaintextSchema(database)
        return try {
            val opened = SQLiteDatabase.openDatabase(
                database.path,
                passphrase,
                null,
                SQLiteDatabase.OPEN_READONLY,
                null
            )
            try {
                opened.query("PRAGMA user_version").use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
            } finally {
                opened.close()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not read encrypted schema version: ${e.message}")
            0
        }
    }

    fun isPlaintextSqlite(file: File): Boolean {
        return try {
            if (!file.isFile || file.length() < SQLITE_HEADER.length) {
                false
            } else {
                FileInputStream(file).use { input ->
                    val header = ByteArray(SQLITE_HEADER.length)
                    input.read(header) == header.size && String(header, Charsets.US_ASCII) == SQLITE_HEADER
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun readPlaintextSchema(database: File): Int {
        return try {
            RandomAccessFile(database, "r").use { input ->
                if (input.length() < 64) {
                    0
                } else {
                    input.seek(60)
                    input.readInt()
                }
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun exportDatabase(
        source: File,
        sourcePassphrase: ByteArray,
        destination: File,
        destinationPassphrase: ByteArray
    ) {
        destination.parentFile?.mkdirs()
        destination.delete()
        val sourceDatabase = SQLiteDatabase.openDatabase(
            source.path,
            sourcePassphrase,
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null
        )
        var attached = false
        try {
            sourceDatabase.rawExecSQL(
                "ATTACH DATABASE ? AS exported KEY ?",
                destination.absolutePath,
                String(destinationPassphrase, Charsets.US_ASCII)
            )
            attached = true
            sourceDatabase.query("SELECT sqlcipher_export('exported')").use { cursor ->
                check(cursor.moveToFirst()) { "SQLCipher export did not complete" }
            }
            sourceDatabase.rawExecSQL("DETACH DATABASE exported")
            attached = false
        } finally {
            if (attached) runCatching { sourceDatabase.rawExecSQL("DETACH DATABASE exported") }
            sourceDatabase.close()
        }
        check(destination.isFile && destination.length() > 0L) { "SQLCipher export produced no database" }
    }

    private fun verify(database: File, passphrase: ByteArray) {
        val opened = SQLiteDatabase.openDatabase(
            database.path,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
            null
        )
        try {
            opened.query("PRAGMA cipher_integrity_check").use { cursor ->
                check(!cursor.moveToFirst()) { "Encrypted database integrity check failed: ${cursor.getString(0)}" }
            }
            opened.query("SELECT count(*) FROM sqlite_master").use { cursor ->
                check(cursor.moveToFirst()) { "Encrypted database schema is unreadable" }
            }
        } finally {
            opened.close()
        }
    }

    private fun loadLibrary() {
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: Throwable) {
            Log.w(TAG, "System.loadLibrary(sqlcipher) note: ${e.message}")
        }
    }
}
