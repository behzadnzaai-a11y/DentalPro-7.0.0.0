package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.feature.auth.UserRole
import com.example.ui.DentalViewModel
import kotlinx.coroutines.launch

fun buildAppointmentReminderText(isPersian: Boolean, patientName: String, dateMillis: Long, viewModel: DentalViewModel, gender: String = "مرد"): String {
    val dateStr = viewModel.formatDateFullVerbal(dateMillis)
    val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(dateMillis))
    val settings = viewModel.settings.value
    val template = settings.appointmentTemplate
    val formattedPatient = honorificPatientName(patientName, gender)
    if (template.isNotBlank()) {
        return template.replace("{clinic}", settings.clinicName)
            .replace("{patient}", formattedPatient)
            .replace("{date}", dateStr)
            .replace("{time}", timeStr)
    }
    return if (isPersian) {
        "با سلام و احترام؛\n$formattedPatient عزیز، نوبت شما در مطب دندان‌پزشکی برای روز $dateStr ساعت $timeStr ثبت شده است.\nخواهشمندیم چند دقیقه زودتر تشریف بیاورید.\nبا تشکر از همراهی شما."
    } else {
        "Dear $formattedPatient,\nYour dental appointment is scheduled for $dateStr at $timeStr.\nPlease arrive a few minutes early.\nThank you."
    }
}
