package com.example

import android.app.Application
import android.util.Log
import com.example.util.Supabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        Supabase.init(applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pingResult = Supabase.ping()
                Log.d("SupabasePing", "Supabase ping result: $pingResult")
                val acc = Supabase.currentUser()
                Log.d("SupabasePing", "Session active for: ${acc.email.ifBlank { acc.id }}")
            } catch (e: Exception) {
                Log.d("SupabasePing", "Supabase connection checked: ${e.message}")
            }
        }
    }
}
