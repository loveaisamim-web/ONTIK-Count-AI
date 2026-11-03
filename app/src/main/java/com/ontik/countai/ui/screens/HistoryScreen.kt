package com.ontik.countai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ontik.countai.data.local.CountSessionEntity
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.domain.model.GatedFeature
import com.ontik.countai.domain.model.SyncStatus
import com.ontik.countai.ui.components.AdMobBannerCard
import com.ontik.countai.ui.theme.DangerRed
import com.ontik.countai.ui.theme.OntikAmberAccent
import com.ontik.countai.ui.theme.OntikAmberDark
import com.ontik.countai.ui.theme.OntikGreenDark
import com.ontik.countai.ui.theme.OntikGreenLight
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
    val planState by viewModel.planState.collectAsState()
    val isBn = settings.language == AppLanguage.BANGLA

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
        // Top Header Bar + Sync Now Button
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
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (planState.isEffectivePro) {
                            if (isBn) "আনলিমিটেড প্রো হিস্ট্রি ও ক্লাউড সিঙ্ক চালু" else "Unlimited PRO History & Cloud Sync Active"
                        } else {
                            if (isBn) "ফ্রি প্ল্যান: সর্বশেষ ${planState.freeHistoryLimit}টি সেশন (প্রো-তে আনলিমিটেড)" else "Free Plan: Latest ${planState.freeHistoryLimit} sessions (Upgrade for unlimited)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = {
                    if (viewModel.canUseFeature(GatedFeature.CLOUD_BACKUP_SYNC)) {
                        viewModel.triggerManualCloudSync()
                        Toast.makeText(
                            context,
                            if (isBn) "ক্লাউড সিঙ্ক শুরু হয়েছে..." else "Cloud sync started...",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        onNavigateToPaywall()
                    }
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Sync Now", tint = OntikGreenPrimary)
            }
        }

        // Export CSV & Branded PDF Report Buttons (PRO Gated)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    viewModel.exportHistoryCsv(onBlockedByFreePlan = onNavigateToPaywall)
                },
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isBn) "CSV শেয়ার (PRO)" else "Export CSV (PRO)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = {
                    viewModel.exportHistoryPdfReport(onBlockedByFreePlan = onNavigateToPaywall)
                },
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, OntikGreenPrimary)
            ) {
                Text(
                    text = if (isBn) "PDF রিপোর্ট (PRO)" else "PDF Report (PRO)",
                    color = OntikGreenPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Summary Header Card (Today Total, Weekly Total & 7-Day Bar Chart)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isBn) "আজকের মোট (Today)" else "TODAY TOTAL",
                            style = MaterialTheme.typography.labelLarge,
                            color = OntikGreenPrimary
                        )
                        Text(
                            text = "$todayTotal",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (isBn) "সাপ্তাহিক মোট (7 Days)" else "WEEKLY TOTAL (7D)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$weeklyTotal",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                WeeklyMiniBarChart(sessions = sessions)
            }
        }

        // Search Box & Filter Chips
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = {
                Text(if (isBn) "লেবেল, প্রিসেট বা নোট দিয়ে খুঁজুন..." else "Search by label, preset or note...")
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = if (isBn) {
                listOf("সব", "আজ", "৭ দিন", "১০০-টেস্ট")
            } else {
                listOf("All", "Today", "7 Days", "100-Test")
            }
            filters.forEachIndexed { idx, title ->
                FilterChip(
                    selected = dateFilterIndex == idx,
                    onClick = { dateFilterIndex = idx },
                    label = { Text(title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OntikGreenPrimary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
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
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isBn) "কোনো সেশন পাওয়া যায়নি।" else "No matching counting sessions found.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredSessions, key = { it.id }) { session ->
                    SessionCardItem(
                        session = session,
                        formattedDate = dateFormat.format(Date(session.timestampMs)),
                        isBn = isBn,
                        onEditClick = { editingSession = session },
                        onDeleteClick = { viewModel.deleteHistorySession(session) }
                    )
                }
            }
        }

        // AdMob Banner ONLY on History & Settings for Free users (never on Camera screen)
        AdMobBannerCard(isVisible = !viewModel.canUseFeature(GatedFeature.AD_FREE))
    }

    // Rename / Add Note Dialog
    editingSession?.let { session ->
        var labelInput by remember(session) { mutableStateOf(session.label) }
        var notesInput by remember(session) { mutableStateOf(session.notes) }

        AlertDialog(
            onDismissRequest = { editingSession = null },
            title = {
                Text(
                    text = if (isBn) "সেশন রিনেম ও নোট যুক্ত করুন" else "Edit Session Label & Note",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text(if (isBn) "সেশনের নাম (Label)" else "Session Label") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text(if (isBn) "নোট (Notes)" else "Notes") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateHistorySession(
                            session.copy(label = labelInput.trim(), notes = notesInput.trim())
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

@Composable
private fun WeeklyMiniBarChart(sessions: List<CountSessionEntity>) {
    val dayTotals = remember(sessions) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        val dayMs = 24L * 3600L * 1000L

        IntArray(7) { idx ->
            val bucketStart = todayStart - (6 - idx) * dayMs
            val bucketEnd = bucketStart + dayMs
            sessions.filter { it.timestampMs in bucketStart until bucketEnd }.sumOf { it.finalCount }
        }
    }

    val maxVal = (dayTotals.maxOrNull() ?: 1).coerceAtLeast(1)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val barCount = 7
        val spacing = 12.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(8f)

        for (i in 0 until barCount) {
            val ratio = (dayTotals[i].toFloat() / maxVal).coerceIn(0.08f, 1f)
            val barH = size.height * ratio
            val x = i * (barWidth + spacing)
            val y = size.height - barH

            drawRoundRect(
                color = if (i == 6) OntikGreenPrimary else OntikGreenPrimary.copy(alpha = 0.38f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
        }
    }
}

@Composable
private fun SessionCardItem(
    session: CountSessionEntity,
    formattedDate: String,
    isBn: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val (syncLabel, syncColor) = when (session.syncStatus) {
        SyncStatus.SYNCED.name -> (if (isBn) "সিঙ্কড ✓" else "Synced ✓") to OntikGreenPrimary
        SyncStatus.PENDING_UPLOAD.name -> (if (isBn) "পেন্ডিং..." else "Pending") to OntikAmberDark
        SyncStatus.FAILED.name -> (if (isBn) "ব্যর্থ !" else "Sync Failed") to DangerRed
        else -> (if (isBn) "লোকাল" else "Local") to Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = session.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = syncColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = syncLabel,
                            color = syncColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (session.isTestModeSession) {
                        Card(
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(containerColor = OntikAmberAccent.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = "Err ${String.format(Locale.US, "%.1f%%", session.accuracyErrorPercent)}",
                                color = OntikAmberDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "$formattedDate • ${session.presetName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val adj = if (session.manualDelta >= 0) "+${session.manualDelta}" else "${session.manualDelta}"
                Text(
                    text = if (isBn) {
                        "অটো: ${session.autoCount} • ম্যানুয়াল: $adj • সময়: ${session.durationSeconds}s"
                    } else {
                        "Auto: ${session.autoCount} • Manual: $adj • ${session.durationSeconds}s"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (session.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = OntikGreenLight.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = session.notes,
                            color = OntikGreenDark,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${session.finalCount}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = OntikGreenPrimary
                )
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Session",
                        tint = DangerRed
                    )
                }
            }
        }
    }
}
