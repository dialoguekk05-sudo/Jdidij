package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.state.PendingConfirmation
import com.example.ui.theme.JarvisBorderGlow
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisPanel
import com.example.ui.theme.JarvisPanelElevated
import com.example.ui.theme.JarvisWarning

@Composable
fun SensitiveActionConfirmationDialog(
    confirmation: PendingConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisPanel,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        icon = {
            Icon(
                imageVector = when (confirmation) {
                    is PendingConfirmation.ConfirmCall -> Icons.Default.Call
                    is PendingConfirmation.ConfirmSms -> Icons.Default.Sms
                },
                contentDescription = null,
                tint = JarvisWarning
            )
        },
        title = {
            Text(
                text = when (confirmation) {
                    is PendingConfirmation.ConfirmCall -> "Confirm Phone Call"
                    is PendingConfirmation.ConfirmSms -> "Confirm SMS Message"
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "JARVIS never places calls or sends messages without your explicit approval.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    color = JarvisPanelElevated,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, JarvisBorderGlow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        when (confirmation) {
                            is PendingConfirmation.ConfirmCall -> {
                                Text("Recipient: ${confirmation.recipientLabel}", color = JarvisCyan)
                                Text(
                                    "Number / Target: ${confirmation.phoneNumberOrQuery}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            is PendingConfirmation.ConfirmSms -> {
                                Text("To: ${confirmation.recipientLabel}", color = JarvisCyan)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Message: \"${confirmation.messageBody}\"",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                modifier = Modifier.testTag("confirm_sensitive_action_button")
            ) {
                Text("Confirm & Proceed")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_sensitive_action_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
