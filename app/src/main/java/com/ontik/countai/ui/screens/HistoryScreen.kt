package com.ontik.countai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ontik.countai.data.local.CountSessionEntity
import com.ontik.countai.ui.theme.AppStrings
import com.ontik.countai.ui.theme.CardBorderGray
import com.ontik.countai.ui.theme.DangerRed
import com.ontik.countai.ui.theme.OntikGreenDark
import com.ontik.countai.ui.theme.OntikGreenLight
import com.ontik.countai.ui.theme.OntikGreenPrimary
import com.ontik.countai.ui.theme.OntikIcons
import com.ontik.countai.ui.theme.SoftGraySurface
import com.ontik.countai.ui.theme.TextPrimaryDark
import com.ontik.countai.ui.theme.TextSecondarySlate
import com.ontik.countai.ui.theme.WhiteBackground
import com.ontik.countai.ui.viewmodel.CountViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: CountViewModel,
    onBack: () -> Unit
) {
    val sessions by viewModel.historySessions.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val lang = settings.language
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm:ss a", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WhiteBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = AppStrings.historyTitle(lang),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = AppStrings.historySummary(lang, sessions.size, sessions.sumOf { it.finalCount }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondarySlate
                    )
                }
            }

            if (sessions.isNotEmpty()) {
                IconButton(onClick = viewModel::clearAllHistory) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear All History",
                        tint = DangerRed
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = viewModel::exportHistoryAsCsv,
                enabled = sessions.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
            ) {
                Icon(OntikIcons.Table, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppStrings.exportCsvBtn(lang), fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = viewModel::exportHistoryAsTxt,
                enabled = sessions.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, OntikGreenPrimary)
            ) {
                Icon(OntikIcons.Document, contentDescription = null, tint = OntikGreenPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppStrings.exportTxtBtn(lang), color = OntikGreenPrimary, fontWeight = FontWeight.Bold)
            }
        }

        if (sessions.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SoftGraySurface),
                border = BorderStroke(1.dp, CardBorderGray)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = AppStrings.emptyHistoryTitle(lang),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimaryDark,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = AppStrings.emptyHistoryDesc(lang),
                        style = MaterialTheme.typography.bodyMedium,
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
                items(sessions, key = { it.id }) { session ->
                    SessionHistoryCard(
                        session = session,
                        formattedDate = dateFormat.format(Date(session.timestampMs)),
                        onDelete = { viewModel.deleteSession(session) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(
    session: CountSessionEntity,
    formattedDate: String,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SoftGraySurface),
        border = BorderStroke(1.dp, CardBorderGray)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(6.dp),
                        colors = CardDefaults.cardColors(containerColor = OntikGreenLight)
                    ) {
                        Text(
                            text = session.mode,
                            color = OntikGreenDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondarySlate
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                val adjStr = if (session.manualDelta >= 0) "+${session.manualDelta}" else "${session.manualDelta}"
                Text(
                    text = "Detected: ${session.aiDetectedCount} • Manual: $adjStr • ${session.direction}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimaryDark
                )
                Text(
                    text = "Engine: ${session.modelName} (${(session.confidenceThreshold * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondarySlate
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${session.finalCount}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = OntikGreenPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                IconButton(onClick = onDelete) {
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
