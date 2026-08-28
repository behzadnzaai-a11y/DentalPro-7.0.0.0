package com.example.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class AppointmentAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appointmentId = intent.getLongExtra("appointment_id", -1L)
        val patientName = intent.getStringExtra("patient_name") ?: "بیمار"
        val timeText = intent.getStringExtra("time_text") ?: "نزدیک است"

        if (appointmentId != -1L) {
            showAppointmentReminder(
                context = context,
                notificationId = (10000 + appointmentId % 50000).toInt(),
                patientName = patientName,
                timeText = timeText
            )
        }
    }

    companion object {
        fun scheduleExactReminder(
            context: Context,
            appointmentId: Long,
            patientName: String,
            appointmentTimeMillis: Long,
            leadMinutes: Int = 60
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val triggerTime = appointmentTimeMillis - (leadMinutes * 60 * 1000L)
            if (triggerTime <= System.currentTimeMillis()) return

            val intent = Intent(context, AppointmentAlarmReceiver::class.java).apply {
                putExtra("appointment_id", appointmentId)
                putExtra("patient_name", patientName)
                putExtra("time_text", "$leadMinutes دقیقه دیگر")
            }

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appointmentId.toInt(),
                intent,
                flags
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                Log.e("AlarmScheduler", "Exact alarm permission not granted", e)
            }
        }

        fun cancelReminder(context: Context, appointmentId: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, AppointmentAlarmReceiver::class.java)
            val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appointmentId.toInt(),
                intent,
                flags
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
