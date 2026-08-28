package com.example.ui.components

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.DentalViewModel
import com.example.util.AppLocalization
import com.example.util.RegionalOccasionsProvider

data class SearchableOccasionItem(
    val title: String,
    val dateDisplay: String,
    val category: String,
    val isHoliday: Boolean,
    val description: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OccasionSearchDialog(
    viewModel: DentalViewModel,
    onDismiss: () -> Unit
) {
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val allDbEvents by viewModel.allCalendarEvents.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }

    // Aggregate occasions based on language
    val occasionsList = remember(selectedLanguage, allDbEvents, searchQuery) {
        val currentLang = selectedLanguage.lowercase()
        val query = searchQuery.trim().lowercase()

        if (currentLang == "fa") {
            allDbEvents.filter {
                if (query.isEmpty()) true
                else it.title.lowercase().contains(query) ||
                        it.category.lowercase().contains(query) ||
                        it.description.lowercase().contains(query)
            }.map { ev ->
                val dateStr = if (ev.shamsiDay != null && ev.shamsiMonth != null) {
                    val mName = com.example.util.PersianCalendarHelper.MONTH_NAMES.getOrElse(ev.shamsiMonth - 1) { "" }
                    "${ev.shamsiDay} $mName"
                } else if (ev.qamariDay != null && ev.qamariMonth != null) {
                    val qmName = com.example.util.PersianCalendarHelper.QAMARI_MONTH_NAMES.getOrElse(ev.qamariMonth - 1) { "" }
                    "${ev.qamariDay} $qmName (قمری)"
                } else if (ev.miladiDay != null && ev.miladiMonth != null) {
                    "${ev.miladiDay}/${ev.miladiMonth} (میلادی)"
                } else ""

                SearchableOccasionItem(
                    title = ev.title,
                    dateDisplay = dateStr,
                    category = ev.category,
                    isHoliday = ev.isHolidayInIran,
                    description = ev.description
                )
            }
        } else {
            val regional = RegionalOccasionsProvider.searchOccasions(currentLang, query)
            regional.map { reg ->
                val dateStr = if (reg.miladiDay != null && reg.miladiMonth != null) {
                    val mName = AppLocalization.getMonthName(currentLang, reg.miladiMonth, "MILADI")
                    "${reg.miladiDay} $mName"
                } else if (reg.qamariDay != null && reg.qamariMonth != null) {
                    val qmName = AppLocalization.getMonthName(currentLang, reg.qamariMonth, "QAMARI")
                    "${reg.qamariDay} $qmName (Hijri)"
                } else ""

                SearchableOccasionItem(
                    title = reg.title,
                    dateDisplay = dateStr,
                    category = reg.category,
                    isHoliday = reg.isHoliday,
                    description = reg.description
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(12.dp)
                .testTag("occasion_search_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = "Occasions",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = AppLocalization.get("occasions_events", selectedLanguage),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(AppLocalization.get("search_occasions", selectedLanguage), fontSize = 13.sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("occasion_search_input")
                )

                // Results Counter & Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${occasionsList.size} " + if (isPersian) "مناسبت یافت شد" else "Occasions found",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // List
                if (occasionsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = "No Results",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = AppLocalization.get("no_occasions_found", selectedLanguage),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(occasionsList) { item ->
                            OccasionResultCard(item = item, selectedLanguage = selectedLanguage)
                        }
                    }
                }

                // Footer Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(AppLocalization.get("close", selectedLanguage), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun OccasionResultCard(
    item: SearchableOccasionItem,
    selectedLanguage: String
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isHoliday) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (item.isHoliday) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                if (item.isHoliday) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = AppLocalization.get("holiday", selectedLanguage),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.dateDisplay.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = item.dateDisplay,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (item.category.isNotEmpty()) {
                    Text(
                        text = "• ${item.category}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.description.isNotEmpty()) {
                Text(
                    text = item.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    lineHeight = 15.sp
                )
            }
        }
    }
}
