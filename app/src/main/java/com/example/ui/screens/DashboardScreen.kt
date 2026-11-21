package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ChatMessageEntity
import com.example.data.preferences.JarvisLanguageMode
import com.example.data.preferences.JarvisSettings
import com.example.system.SystemTelemetry
import com.example.ui.components.JarvisArcOrb
import com.example.ui.components.JarvisVoiceWaveform
import com.example.ui.state.AiResponseState
import com.example.ui.theme.JarvisBorderGlow
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepSpace
import com.example.ui.theme.JarvisError
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisPanel
import com.example.ui.theme.JarvisPanelElevated
import com.example.ui.theme.JarvisSuccess
import com.example.ui.theme.JarvisTeal
import com.example.ui.theme.JarvisTextSecondary
import com.example.voice.VoiceState

@Composable
fun DashboardScreen(
    settings: JarvisSettings,
    voiceState: VoiceState,
    rmsDb: Float,
    partialTranscript: String,
    voiceError: String?,
    aiState: AiResponseState,
    telemetry: SystemTelemetry,
    isOnline: Boolean,
    latestJarvisMessage: ChatMessageEntity?,
    onOrbMicClick: () -> Unit,
    onSendCommand: (String) -> Unit,
    onToggleContinuous: (Boolean) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onCycleLanguage: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenBuilder: () -> Unit,
    modifier: Modifier = Modifier
) {
    var commandText by remember { mutableStateOf("") }
    val isThinking = aiState is AiResponseState.Thinking || aiState is AiResponseState.Streaming

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(JarvisObsidian, JarvisDeepSpace, JarvisObsidian)
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top HUD Telemetry Strip
            Surface(
                color = JarvisPanel.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, JarvisBorderGlow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "J.A.R.V.I.S CORE",
                            style = MaterialTheme.typography.titleLarge,
                            color = JarvisCyan
                        )
                        Text(
                            text = if (isOnline) "AI NEURAL LINK ONLINE" else "OFFLINE / LOCAL MODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOnline) JarvisSuccess else JarvisError
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TelemetryBadge(
                            icon = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                            label = if (isOnline) "NET" else "OFF",
                            tint = if (isOnline) JarvisCyan else JarvisError
                        )
                        TelemetryBadge(
                            icon = Icons.Default.Memory,
                            label = "${telemetry.availableRamMb}MB",
                            tint = JarvisTeal
                        )
                        TelemetryBadge(
                            icon = Icons.Default.BatteryChargingFull,
                            label = "${telemetry.batteryPercent}%",
                            tint = JarvisCyan
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Holographic Blue Glowing AI Circle
            JarvisArcOrb(
                voiceState = voiceState,
                isThinking = isThinking,
                rmsDb = rmsDb,
                lowRamMode = settings.lowRamOptimized,
                onClick = onOrbMicClick,
                size = 200.dp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Animated Voice Waveform
            JarvisVoiceWaveform(
                voiceState = voiceState,
                rmsDb = rmsDb,
                isThinking = isThinking,
                lowRamMode = settings.lowRamOptimized,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status / Live Transcript Readout
            val statusLine = when {
                partialTranscript.isNotBlank() -> "\"$partialTranscript\""
                voiceState == VoiceState.LISTENING -> "Listening in ${settings.languageMode.label}..."
                voiceState == VoiceState.WAKE_LISTENING -> "Wake-Word Armed: Say \"Hey JARVIS\""
                voiceState == VoiceState.SPEAKING -> "JARVIS is speaking..."
                aiState is AiResponseState.Thinking -> "Processing neural query..."
                aiState is AiResponseState.Streaming -> "Streaming response..."
                else -> "Tap the Glowing Core or say \"Hey JARVIS\""
            }

            Text(
                text = statusLine,
                style = MaterialTheme.typography.bodyLarge,
                color = JarvisCyan,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            if (!voiceError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = voiceError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisError,
                    textAlign = TextAlign.Center
                )
            }

            if (aiState is AiResponseState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = JarvisError.copy(alpha = 0.14f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, JarvisError.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = aiState.message,
                        color = JarvisError,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Mode Toggle Row (Continuous, Wake-Word, Language)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = settings.continuousVoiceMode,
                    onClick = { onToggleContinuous(!settings.continuousVoiceMode) },
                    label = { Text("Continuous") },
                    leadingIcon = {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                        selectedLabelColor = JarvisCyan
                    ),
                    modifier = Modifier.testTag("chip_continuous_mode")
                )

                FilterChip(
                    selected = settings.wakeWordEnabled,
                    onClick = { onToggleWakeWord(!settings.wakeWordEnabled) },
                    label = { Text("Hey JARVIS") },
                    leadingIcon = {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                        selectedLabelColor = JarvisCyan
                    ),
                    modifier = Modifier.testTag("chip_wake_word_mode")
                )

                FilterChip(
                    selected = true,
                    onClick = onCycleLanguage,
                    label = {
                        Text(
                            when (settings.languageMode) {
                                JarvisLanguageMode.BILINGUAL -> "HI + EN"
                                JarvisLanguageMode.HINDI -> "हिन्दी"
                                JarvisLanguageMode.ENGLISH -> "English"
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JarvisPanelElevated,
                        selectedLabelColor = JarvisTeal
                    ),
                    modifier = Modifier.testTag("chip_language_mode")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Command Input Bar
            OutlinedTextField(
                value = commandText,
                onValueChange = { commandText = it },
                placeholder = { Text("Ask in Hindi or English... (e.g. YouTube खोलो)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisBorderGlow,
                    focusedContainerColor = JarvisPanel,
                    unfocusedContainerColor = JarvisPanel
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOrbMicClick,
                            modifier = Modifier.testTag("dashboard_mic_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice command",
                                tint = JarvisCyan
                            )
                        }
                        IconButton(
                            onClick = {
                                if (commandText.isNotBlank()) {
                                    onSendCommand(commandText)
                                    commandText = ""
                                }
                            },
                            modifier = Modifier.testTag("dashboard_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send command",
                                tint = JarvisTeal
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_command_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Action Command Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { onSendCommand("भारत की ताज़ा विज्ञान और तकनीक खबरें इंटरनेट से खोजकर हिंदी में बताओ") },
                    label = { Text("Web Search (HI)") },
                    leadingIcon = { Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = JarvisPanel),
                    modifier = Modifier.testTag("quick_chip_web_search")
                )
                AssistChip(
                    onClick = onOpenBuilder,
                    label = { Text("AI App Builder") },
                    leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = JarvisPanel),
                    modifier = Modifier.testTag("quick_chip_app_builder")
                )
                AssistChip(
                    onClick = { onSendCommand("Open YouTube") },
                    label = { Text("Open YouTube") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = JarvisPanel),
                    modifier = Modifier.testTag("quick_chip_open_youtube")
                )
                AssistChip(
                    onClick = { onSendCommand("नमस्ते जार्विस, आज मैं तुम्हारी मदद से क्या बना सकता हूँ?") },
                    label = { Text("Hindi Talk") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = JarvisPanel),
                    modifier = Modifier.testTag("quick_chip_hindi_talk")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Latest AI Response Preview Card
            val liveText = when (aiState) {
                is AiResponseState.Streaming -> aiState.partialText
                else -> latestJarvisMessage?.content.orEmpty()
            }

            AnimatedVisibility(visible = liveText.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisPanel),
                    border = BorderStroke(1.dp, JarvisBorderGlow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (aiState is AiResponseState.Streaming) "JARVIS STREAMING..." else "LATEST RESPONSE",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisCyan
                            )
                            TextButton(onClick = onOpenChat) {
                                Text("Open Full Chat")
                            }
                        }
                        Text(
                            text = liveText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextSecondary,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}
