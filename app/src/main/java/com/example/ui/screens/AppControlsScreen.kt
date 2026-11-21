package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.system.InstalledAppShortcut
import com.example.system.JarvisAccessibilityService
import com.example.system.JarvisGlobalAction
import com.example.system.PermissionSnapshot
import com.example.ui.theme.JarvisBorderGlow
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisPanel
import com.example.ui.theme.JarvisPanelElevated
import com.example.ui.theme.JarvisSuccess
import com.example.ui.theme.JarvisTeal
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisWarning

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppControlsScreen(
    permissions: PermissionSnapshot,
    installedApps: List<InstalledAppShortcut>,
    accessibilityConnected: Boolean,
    onRequestMicPermission: () -> Unit,
    onRequestCallPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onRequestCallConfirm: (String, String) -> Unit,
    onRequestSmsConfirm: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var appFilter by remember { mutableStateOf("") }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var phoneInput by remember { mutableStateOf("") }
    var smsMessageInput by remember { mutableStateOf("") }

    val filteredApps = remember(installedApps, appFilter) {
        if (appFilter.isBlank()) installedApps.take(24)
        else installedApps.filter { it.label.contains(appFilter, ignoreCase = true) }.take(24)
    }

    if (showAccessibilityDisclosure) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosure = false },
            containerColor = JarvisPanel,
            icon = { Icon(Icons.Default.AccessibilityNew, contentDescription = null, tint = JarvisCyan) },
            title = { Text("Accessibility Permission Disclosure", color = JarvisCyan) },
            text = {
                Text(
                    "JARVIS Hands-Free Navigation uses Android Accessibility Service strictly to execute global navigation commands you trigger (Back, Home, Recents, Notifications, Quick Settings).\n\n" +
                        "Privacy guarantee: Window content reading is disabled (canRetrieveWindowContent = false). JARVIS never reads passwords, messages, or screen text.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityDisclosure = false
                        onOpenAccessibilitySettings()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Text("I Understand, Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDisclosure = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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
                text = "DEVICE CONTROL & PERMISSIONS",
                style = MaterialTheme.typography.titleLarge,
                color = JarvisCyan
            )

            // Permissions Matrix Card
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisPanel),
                border = BorderStroke(1.dp, JarvisBorderGlow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "PERMISSION GOVERNANCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTeal
                    )

                    PermissionStatusRow(
                        icon = Icons.Default.Mic,
                        title = "Microphone (Voice & Wake Word)",
                        granted = permissions.micGranted,
                        actionLabel = "Grant",
                        onAction = onRequestMicPermission,
                        testTag = "perm_mic_button"
                    )
                    PermissionStatusRow(
                        icon = Icons.Default.Call,
                        title = "Direct Phone Call (Optional)",
                        granted = permissions.callPhoneGranted,
                        actionLabel = "Grant",
                        onAction = onRequestCallPermission,
                        testTag = "perm_call_button"
                    )
                    PermissionStatusRow(
                        icon = Icons.Default.Notifications,
                        title = "Notifications (Wake Service)",
                        granted = permissions.notificationsGranted,
                        actionLabel = "Grant",
                        onAction = onRequestNotificationPermission,
                        testTag = "perm_notif_button"
                    )
                    PermissionStatusRow(
                        icon = Icons.Default.AccessibilityNew,
                        title = "Accessibility Hands-Free Nav",
                        granted = accessibilityConnected,
                        actionLabel = if (accessibilityConnected) "Manage" else "Enable",
                        onAction = { showAccessibilityDisclosure = true },
                        testTag = "perm_accessibility_button"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = JarvisSuccess, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Files use zero-permission Storage Access Framework",
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextSecondary
                            )
                        }
                        TextButton(onClick = onOpenAppSettings) {
                            Text("OS Settings")
                        }
                    }
                }
            }

            // Accessibility Global Actions (if user granted consent)
            if (accessibilityConnected) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisPanel),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "HANDS-FREE ACCESSIBILITY ACTIONS",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisCyan
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            JarvisGlobalAction.entries.forEach { action ->
                                AssistChip(
                                    onClick = { JarvisAccessibilityService.performGlobalNavigation(action) },
                                    label = { Text(action.label) }
                                )
                            }
                        }
                    }
                }
            }

            // Confirmed Call & SMS Console
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisPanel),
                border = BorderStroke(1.dp, JarvisBorderGlow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CALL & MESSAGE LAUNCHER (WITH CONFIRMATION)",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTeal
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Phone number or contact label") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("controls_phone_input")
                    )
                    OutlinedTextField(
                        value = smsMessageInput,
                        onValueChange = { smsMessageInput = it },
                        label = { Text("Optional SMS message text") },
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("controls_sms_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                onRequestCallConfirm(
                                    phoneInput.ifBlank { "Dialer" },
                                    phoneInput
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisPanelElevated),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("controls_call_button")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call", color = JarvisCyan)
                        }
                        Button(
                            onClick = {
                                onRequestSmsConfirm(
                                    phoneInput.ifBlank { "Contact" },
                                    phoneInput,
                                    smsMessageInput
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisPanelElevated),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("controls_sms_button")
                        ) {
                            Icon(Icons.Default.Sms, contentDescription = null, tint = JarvisTeal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send SMS", color = JarvisTeal)
                        }
                    }
                }
            }

            // Installed Applications Quick Launcher
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisPanel),
                border = BorderStroke(1.dp, JarvisBorderGlow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "INSTALLED APPS (${installedApps.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisTeal
                        )
                        Icon(Icons.Default.Apps, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(18.dp))
                    }
                    OutlinedTextField(
                        value = appFilter,
                        onValueChange = { appFilter = it },
                        placeholder = { Text("Search installed apps...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("controls_app_search_input")
                    )

                    if (filteredApps.isEmpty()) {
                        Text(
                            text = "No matching launchable apps found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextSecondary
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filteredApps.forEach { app ->
                                AssistChip(
                                    onClick = { onLaunchApp(app.packageName) },
                                    label = { Text(app.label) }
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
private fun PermissionStatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    Surface(
        color = JarvisPanelElevated,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (granted) JarvisSuccess else JarvisWarning,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (granted) "Active & Permitted" else "Permission Required",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (granted) JarvisSuccess else JarvisWarning
                    )
                }
            }
            if (granted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = JarvisSuccess, modifier = Modifier.size(18.dp))
                    TextButton(onClick = onAction, modifier = Modifier.testTag(testTag)) {
                        Text(actionLabel)
                    }
                }
            } else {
                OutlinedButton(onClick = onAction, modifier = Modifier.testTag(testTag)) {
                    Text(actionLabel)
                }
            }
        }
    }
}
