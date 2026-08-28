package com.example.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.DentalRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "dentalpro_reminder_check"

/**
 * Runs roughly once every 6 hours (WorkManager periodic work is inexact by
 * design — Android batches it with other apps' jobs to save battery, so
 * reminders fire "sometime in the window", not to the second).
 *
 * Checks:
 *  - Appointments starting within the next 24 hours → one notification each.
 *  - Cheques (payments with type == CHEQUE, status PENDING) due within the
 *    next 3 days → one notification each.
 */
class ReminderCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val repository = DentalRepository(db)

            val now = System.currentTimeMillis()
            val in24h = now + 24 * 60 * 60 * 1000L
            val in3days = now + 3 * 24 * 60 * 60 * 1000L

            val appointments = repository.allAppointments.first()
            appointments
                .filter { !it.isCompleted && !it.isArchived && it.date in now..in24h }
                .forEach { appt ->
                    val timeText = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(appt.date))
                    showAppointmentReminder(applicationContext, notificationId = "appt_${appt.id}".hashCode(), patientName = appt.patientName, timeText = timeText)
                }


            val payments = repository.allPayments.first()
            payments
                .filter { it.type == "CHEQUE" && it.chequeStatus == "PENDING" && it.chequeDueDate != null && it.chequeDueDate in now..in3days }
                .forEach { payment ->
                    val patients = repository.allPatients.first()
                    val patientName = patients.firstOrNull { it.id == payment.patientId }?.name ?: "—"
                    val dueText = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date(payment.chequeDueDate!!))
                    showChequeDueReminder(
                        applicationContext,
                        notificationId = "cheque_${payment.id}".hashCode(),
                        patientName = patientName,
                        amountText = "${payment.amount}",
                        dueText = dueText
                    )
                }

            val tomorrowJalali = com.example.util.PersianCalendarHelper.timestampToJalali(in24h)
            val events = repository.allCalendarEvents.first()
            events.filter { event ->
                val isPersonal = event.category == "شخصی" || event.personalType.isNotEmpty()
                isPersonal && event.shamsiMonth == tomorrowJalali.month && event.shamsiDay == tomorrowJalali.day
            }.forEach { event ->
                showOccasionReminder(
                    applicationContext,
                    notificationId = "occasion_${event.id}".hashCode(),
                    title = event.title,
                    typeName = event.personalType.ifEmpty { "شخصی" }
                )
            }


            Result.success().also {
                com.example.widget.AppointmentsWidgetProvider.requestUpdate(applicationContext)
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderCheckWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
