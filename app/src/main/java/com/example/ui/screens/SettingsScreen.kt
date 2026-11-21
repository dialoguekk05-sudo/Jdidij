package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.MemoryFactEntity
import com.example.data.preferences.JarvisLanguageMode
import com.example.data.preferences.JarvisSettings
import com.example.data.preferences.VoiceGender
import com.example.system.SystemTelemetry
import com.example.ui.theme.JarvisBorderGlow
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisError
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisPanel
import com.example.ui.theme.JarvisPanelElevated
import com.example.ui.theme.JarvisSuccess
import com.example.ui.theme.JarvisTeal
import com.example.ui.theme.JarvisTextSecondary

@Composable
fun SettingsScreen(
    settings: JarvisSettings,
    memoryFacts: List<MemoryFactEntity>,
    telemetry: SystemTelemetry,
    onSelectLanguage: (JarvisLanguageMode) -> Unit,
    onSelectVoiceGender: (VoiceGender) -> Unit,
    onChangeSpeechRate: (Float) -> Unit,
    onChangeSpeechPitch: (Float) -> Unit,
    onTestVoice: () -> Unit,
    onToggleContinuousVoice: (Boolean) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onToggleBackgroundWakeService: (Boolean) -> Unit,
    onToggleSaveHistory: (Boolean) -> Unit,
    onToggleAutoMemory: (Boolean) -> Unit,
    onTogglePreferDirectCall: (Boolean) -> Unit,
    onToggleLowRamMode: (Boolean) -> Unit,
    onAddMemoryFact: (String) -> Unit,
    onToggleMemoryFact: (MemoryFactEntity) -> Unit,
    onDeleteMemoryFact: (Long) -> Unit,
    onClearChatHistory: () -> Unit,
    onWipeAllMemory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newFactText by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisObsidian),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SETTINGS, VOICE & PRIVACY",
                style = MaterialTheme.typography.titleLarge,
                color = JarvisCyan
            )

            // Voice & Language Section
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisPanel),
                border = BorderStroke(1.dp, JarvisBorderGlow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("LANGUAGE & VOICE PROFILE", style = MaterialTheme.typography.labelSmall, color = JarvisTeal)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JarvisLanguageMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.languageMode == mode,
                                onClick = { onSelectLanguage(mode) },
                                label = {
                                    Text(
                                        when (mode) {
                                            JarvisLanguageMode.BILINGUAL -> "HI + EN"
                                            JarvisLanguageMode.HINDI -> "हिन्दी"
                                            JarvisLanguageMode.ENGLISH -> "English"
                                        }
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = JarvisCyan
                                )
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceGender.entries.forEach { gender ->
                            FilterChip(
                                selected = settings.voiceGender == gender,
                                onClick = { onSelectVoiceGender(gender) },
                                label = { Text(gender.label) },
                                leadingIcon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = JarvisCyan
                                ),
                                modifier = Modifier.testTag("settings_voice_${gender.name.lowercase()}")
                            )
                        }
                    }

                    Text("Speech Speed: ${"%.2f".format(settings.speechRate)}x", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.speechRate,
                        onValueChange = onChangeSpeechRate,
                        valueRange = 0.6f..1.5f,
                        colors = SliderDefaults.colors(thumbColor = JarvisCyan, activeTrackColor = JarvisCyan)
                    )

                    Text("Voice Pitch: ${"%.2f".format(settings.speechPitch)}x", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.speechPitch,
                        onValueChange = onChangeSpeechPitch,
                        valueRange = 0.7f..1.4f,
                        colors = SliderDefaults.colors(thumbColor = JarvisTeal, activeTrackColor = JarvisTeal)
                    )

                    OutlinedButton(
                        onClick = onTestVoice,
                        modifier = Modifier.testTag("settings_test_voice_button")
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test JARVIS Voice")
                    }
                }
            }

            // Hands-Free & Wake Word Section
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisPanel),
                border = BorderStroke(1.dp, JarvisBorderGlow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("HANDS-FREE & CALL CONTROLS", style = MaterialTheme.typography.labelSmall, color = JarvisTeal)

                    SettingsSwitchRow(
                        title = "Continuous Conversation Mode",
                        subtitle = "Automatically listen again after JARVIS finishes speaking",
                        checked = settings.continuousVoiceMode,
                        onCheckedChange = onToggleContinuousVoice
                    )
                    SettingsSwitchRow(
                        title = "\"Hey JARVIS\" Wake Word",
                        subtitle = "Listen passively for \"Hey JARVIS\" / \"हे जार्विस\" while active",
                        checked = settings.wakeWordEnabled,
                        onCheckedChange = onToggleWakeWord
                    )
                    SettingsSwitchRow(
                        title = "Background Wake Notification Service",
                        subtitle = "Keep foreground mic indicator active when minimized",
                        checked = settings.backgroundWakeService,
                        onCheckedChange = onToggleBackgroundWakeService
                    )
                    SettingsSwitchRow(
                        title = "Prefer Direct Call after Confirmation",
                        subtitle = "Off = Opens Dialer safely; On = Places call directly if CALL_PHONE granted",
                        checked = settings.preferDirectCall,
                        onCheckedChange = onTogglePreferDirectCall
                    )
                }
            }

            // User-Controlled Memory & Privacy
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisPanel),
                border = BorderStroke(1.dp, JarvisBorderGlow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("USER-CONTROLLED MEMORY & PRIVACY", style = MaterialTheme.typography.labelSmall, color = JarvisTeal)

                    SettingsSwitchRow(
                        title = "Remember Conversation History",
                        subtitle = "Store chats locally on device for continuity",
                        checked = settings.saveConversationHistory,
                        onCheckedChange = onToggleSaveHistory
                    )
                    SettingsSwitchRow(
                        title = "Auto-Save Personal Preferences",
                        subtitle = "Let JARVIS save key facts you ask it to remember",
                        checked = settings.autoMemoryFacts,
                        onCheckedChange = onToggleAutoMemory
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newFactText,
                            onValueChange = { newFactText = it },
                            placeholder = { Text("Add memory note (e.g. Call me Boss)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_add_memory_input")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (newFactText.isNotBlank()) {
                                    onAddMemoryFact(newFactText)
                                    newFactText = ""
                                }
                            },
                            modifier = Modifier.testTag("settings_add_memory_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add memory", tint = JarvisCyan)
                        }
                    }

                    if (memoryFacts.isEmpty()) {
                        Text(
                            text = "No saved memory facts yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextSecondary
                        )
                    } else {
                        memoryFacts.forEach { fact ->
                            Surface(
                                color = JarvisPanelElevated,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(fact.fact, style = MaterialTheme.typography.bodyMedium)
                                        Text(fact.category, style = MaterialTheme.typography.labelSmall, color = JarvisCyan)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = fact.isEnabled,
                                            onCheckedChange = { onToggleMemoryFact(fact) }
                                        )
                                        IconButton(onClick = { onDeleteMemoryFact(fact.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete fact", tint = JarvisError)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onClearChatHistory,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_clear_history_button")
                        ) {
                            Text("Clear Chat")
                        }
                        Button(
                            onClick = onWipeAllMemory,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisError),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_wipe_memory_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Wipe Memory")
                        }
                    }
                }
            }

            // Low-RAM Optimization & Security
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisPanel),
                border = BorderStroke(1.dp, JarvisBorderGlow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("PERFORMANCE & SECURITY", style = MaterialTheme.typography.labelSmall, color = JarvisTeal)

                    SettingsSwitchRow(
                        title = "Low-RAM Optimization Mode",
                        subtitle = "Reduces waveform draw density & shader glow (${telemetry.availableRamMb}MB / ${telemetry.totalRamMb}MB free)",
                        checked = settings.lowRamOptimized,
                        onCheckedChange = onToggleLowRamMode
                    )

                    Surface(
                        color = JarvisSuccess.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = JarvisSuccess, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Firebase App Check Active", style = MaterialTheme.typography.bodyLarge, color = JarvisSuccess)
                                Text(
                                    "Zero client-side API keys exposed. All memory & generated code remain private on your device.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarvisTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = JarvisTextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
