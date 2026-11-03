package com.ontik.countai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ontik.countai.domain.model.CountingDirection
import com.ontik.countai.domain.model.ModelEngineState
import com.ontik.countai.domain.model.ModelProfile
import com.ontik.countai.ui.theme.CardBorderGray
import com.ontik.countai.ui.theme.OntikGreenDark
import com.ontik.countai.ui.theme.OntikGreenLight
import com.ontik.countai.ui.theme.OntikGreenPrimary
import com.ontik.countai.ui.theme.OntikIcons
import com.ontik.countai.ui.theme.SoftGraySurface
import com.ontik.countai.ui.theme.TextPrimaryDark
import com.ontik.countai.ui.theme.TextSecondarySlate
import com.ontik.countai.ui.theme.WhiteBackground
import com.ontik.countai.ui.viewmodel.CountViewModel

@Composable
fun SettingsScreen(
    viewModel: CountViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val liveState by viewModel.liveState.collectAsState()
    var importFeedback by remember { mutableStateOf<String?>(null) }

    val modelFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importCustomTfliteModel(uri) { _, message ->
                importFeedback = message
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WhiteBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text(
                    text = "Settings & AI Configuration",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimaryDark
                )
                Text(
                    text = "Configure small-object TFLite model, sensitivity & line crossing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondarySlate
                )
            }
        }

        // Section 1: Replaceable On-Device AI Model Architecture
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SoftGraySurface),
            border = BorderStroke(1.dp, CardBorderGray)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "On-Device AI Model Architecture (TensorFlow Lite / LiteRT)",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimaryDark
                )
                Text(
                    text = "Note: Generic COCO models cannot accurately detect tiny seeds or grains. Use a dedicated YOLOv8/v11 small-object .tflite model trained on seeds/grains/beans.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondarySlate
                )

                val engineStatusText = when (val st = liveState.modelEngineState) {
                    is ModelEngineState.Ready -> "Active: ${st.modelName} (${st.inputWidth}x${st.inputHeight}, ${st.outputShapeDescription})"
                    is ModelEngineState.MissingModelFile -> "Status: Waiting for .tflite model file (${st.expectedPath})"
                    is ModelEngineState.Error -> "Error: ${st.message}"
                    ModelEngineState.Uninitialized -> "Status: Uninitialized"
                }

                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = OntikGreenLight)
                ) {
                    Text(
                        text = engineStatusText,
                        color = OntikGreenDark,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                ModelProfile.entries.forEach { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateModelProfile(profile) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = settings.modelProfile == profile,
                            onClick = { viewModel.updateModelProfile(profile) },
                            colors = RadioButtonDefaults.colors(selectedColor = OntikGreenPrimary)
                        )
                        Column(modifier = Modifier.padding(start = 6.dp)) {
                            Text(
                                text = profile.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = profile.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondarySlate
                            )
                        }
                    }
                }

                Button(
                    onClick = { modelFilePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                ) {
                    Icon(OntikIcons.FileUpload, contentDescription = null)
                    Text(
                        text = "  Import Custom .tflite Model from Phone Storage",
                        fontWeight = FontWeight.Bold
                    )
                }

                importFeedback?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OntikGreenDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Section 2: Detection Confidence & Overlap Sensitivity
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SoftGraySurface),
            border = BorderStroke(1.dp, CardBorderGray)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Detection Sensitivity & Overlapping Objects",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimaryDark
                )

                Text(
                    text = "Minimum Confidence Threshold: ${(settings.confidenceThreshold * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = settings.confidenceThreshold,
                    onValueChange = viewModel::updateConfidenceThreshold,
                    valueRange = 0.10f..0.90f,
                    colors = SliderDefaults.colors(
                        thumbColor = OntikGreenPrimary,
                        activeTrackColor = OntikGreenPrimary
                    )
                )

                Text(
                    text = "NMS Overlap IoU Threshold: ${(settings.iouThreshold * 100).toInt()}% (Higher allows closer touching grains)",
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = settings.iouThreshold,
                    onValueChange = viewModel::updateIouThreshold,
                    valueRange = 0.20f..0.80f,
                    colors = SliderDefaults.colors(
                        thumbColor = OntikGreenPrimary,
                        activeTrackColor = OntikGreenPrimary
                    )
                )
            }
        }

        // Section 3: Virtual Counting Line Direction
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SoftGraySurface),
            border = BorderStroke(1.dp, CardBorderGray)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Counting Line Crossing Direction",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimaryDark
                )
                CountingDirection.entries.forEach { dir ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateCountingDirection(dir) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.countingDirection == dir,
                            onClick = { viewModel.updateCountingDirection(dir) },
                            colors = RadioButtonDefaults.colors(selectedColor = OntikGreenPrimary)
                        )
                        Column(modifier = Modifier.padding(start = 6.dp)) {
                            Text(text = dir.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(text = dir.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Section 4: Camera & Hardware Options
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SoftGraySurface),
            border = BorderStroke(1.dp, CardBorderGray)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Camera & Hardware Options",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimaryDark
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use Rear Camera", style = MaterialTheme.typography.titleMedium)
                        Text("Switch off to use front camera where supported", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = settings.useBackCamera,
                        onCheckedChange = viewModel::updateCameraSelection,
                        colors = SwitchDefaults.colors(checkedTrackColor = OntikGreenPrimary)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Haptic Tick on Line Crossing", style = MaterialTheme.typography.titleMedium)
                        Text("Subtle vibration feedback when an object is counted", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = viewModel::updateHapticFeedback,
                        colors = SwitchDefaults.colors(checkedTrackColor = OntikGreenPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
