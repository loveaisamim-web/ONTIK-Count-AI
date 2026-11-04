package com.ontik.countai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ontik.countai.data.local.CountSessionEntity
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.ui.components.AdMobBannerCard
import com.ontik.countai.ui.theme.DangerRed
import com.ontik.countai.ui.theme.OntikAmberAccent
import com.ontik.countai.ui.theme.OntikGreenPrimary
import com.ontik.countai.ui.viewmodel.CountViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: CountViewModel,
    onBack: () -> Unit,
    onNavigateToPaywall: () -> Unit
) {
    val context = LocalContext.current
    val sessions by viewModel.historySessions.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val lang = settings.language
    val isBn = lang == AppLanguage.BANGLA

    var searchQuery by remember { mutableStateOf("") }
    var dateFilterIndex by remember { mutableIntStateOf(0) }
    var editingSession by remember { mutableStateOf<CountSessionEntity?>(null) }

    val nowMs = System.currentTimeMillis()
    val startOfTodayMs = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val sevenDaysAgoMs = nowMs - 7L * 24L * 3600L * 1000L

    val todayTotal = sessions.filter { it.timestampMs >= startOfTodayMs }.sumOf { it.finalCount }
    val weeklyTotal = sessions.filter { it.timestampMs >= sevenDaysAgoMs }.sumOf { it.finalCount }

    val filteredSessions = sessions.filter { s ->
        val matchesSearch = searchQuery.isBlank() ||
            s.label.contains(searchQuery, ignoreCase = true) ||
            s.presetName.contains(searchQuery, ignoreCase = true) ||
            s.notes.contains(searchQuery, ignoreCase = true)

        val matchesFilter = when (dateFilterIndex) {
            1 -> s.timestampMs >= startOfTodayMs
            2 -> s.timestampMs >= sevenDaysAgoMs
            3 -> s.isTestModeSession
            else -> true
        }
        matchesSearch && matchesFilter
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column {
                    Text(
                        text = if (isBn) "গণনার ইতিহাস (History)" else "Counting History",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isBn) "আনলিমিটেড সেশন হিস্ট্রি ও এক্সপোর্ট" else "Unlimited Session History & Export",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = {
                    viewModel.triggerManualCloudSync()
                    Toast.makeText(
                        context,
                        if (isBn) "ক্লাউড সিঙ্ক শুরু হয়েছে..." else "Cloud sync triggered...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync Now",
                    tint = OntikGreenPrimary
                )
            }
        }

        // Export Row: CSV, TXT, and PDF Report (All Free)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.exportHistoryCsv() },
                enabled = sessions.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isBn) "CSV শেয়ার" else "CSV", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = { viewModel.exportHistoryTxt() },
                enabled = sessions.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, OntikGreenPrimary),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (isBn) "TXT শেয়ার" else "TXT",
                    color = OntikGreenPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            OutlinedButton(
                onClick = { viewModel.exportHistoryPdfReport() },
                enabled = sessions.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, OntikGreenPrimary),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (isBn) "PDF রিপোর্ট" else "PDF",
                    color = OntikGreenPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // Today & 7-Day Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isBn) "আজকের মোট (Today)" else "Today's Total",
                        fontSize = 12.sp,
                        color = OntikGreenPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$todayTotal",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isBn) "সাপ্তাহিক মোট (7 Days)" else "7-Day Total",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$weeklyTotal",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Search Box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = {
                Text(if (isBn) "লেবেল, প্রিসেট বা নোট দিয়ে খুঁজুন..." else "Search by label, preset or notes...")
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Filter Chips
        val filterLabels = if (isBn) {
            listOf("সব", "আজ", "৭ দিন", "১০০-টেস্ট")
        } else {
            listOf("All", "Today", "7 Days", "100-Test")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterLabels.forEachIndexed { idx, title ->
                FilterChip(
                    selected = dateFilterIndex == idx,
                    onClick = { dateFilterIndex = idx },
                    label = { Text(title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OntikGreenPrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        // Session List
        if (filteredSessions.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isBn) "কোনো সেশন পাওয়া যায়নি।" else "No counting sessions found.",
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
                items(filteredSessions, key = { it.id }) { session ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingSession = session },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${dateFormat.format(Date(session.timestampMs))} • ${session.durationSeconds}s",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (session.notes.isNotBlank()) {
                                    Text(
                                        text = session.notes,
                                        fontSize = 12.sp,
                                        color = OntikAmberAccent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = OntikGreenPrimary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "${session.finalCount}",
                                        color = OntikGreenPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteHistorySession(session) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = DangerRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // AdMob Banner Ad on History Screen
        AdMobBannerCard(isVisible = true)
    }

    editingSession?.let { s ->
        var labelText by remember(s) { mutableStateOf(s.label) }
        var notesText by remember(s) { mutableStateOf(s.notes) }
        AlertDialog(
            onDismissRequest = { editingSession = null },
            title = { Text(if (isBn) "সেশন এডিট করুন" else "Edit Session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = { labelText = it },
                        label = { Text(if (isBn) "লেবেল (Label)" else "Label") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text(if (isBn) "নোট (Notes)" else "Notes") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateHistorySession(
                            s.copy(
                                label = labelText.trim().ifBlank { s.label },
                                notes = notesText.trim()
                            )
                        )
                        editingSession = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                ) {
                    Text(if (isBn) "সেভ" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSession = null }) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }
}
