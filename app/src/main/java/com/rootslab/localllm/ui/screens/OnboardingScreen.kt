package com.rootslab.localllm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * First-launch wizard that walks the user through every permission and vendor
 * setting required to keep the server alive on HyperOS / MIUI.
 */
@Composable
fun OnboardingScreen(
    notificationsGranted: Boolean,
    batteryExempt: Boolean,
    isXiaomiDevice: Boolean,
    modelSelected: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenSecurityCenter: () -> Unit,
    onPickModel: () -> Unit,
    onFinish: () -> Unit
) {
    var notificationsState by remember { mutableStateOf(notificationsGranted) }
    var batteryState by remember { mutableStateOf(batteryExempt) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Welcome to LocalLLM", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "A few system permissions keep the local AI server running reliably " +
                "in the background. Everything stays on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        PermissionCard(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            description = "Required to show the persistent foreground-service notification.",
            granted = notificationsState,
            actionLabel = "Allow notifications",
            onAction = {
                onRequestNotifications()
                notificationsState = true
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionCard(
            icon = Icons.Default.BatteryAlert,
            title = "Battery optimisation exemption",
            description = "Prevents Android from restricting the server while the screen is off.",
            granted = batteryState,
            actionLabel = "Exempt app",
            onAction = {
                onRequestBatteryExemption()
                batteryState = true
            }
        )

        if (isXiaomiDevice) {
            Spacer(modifier = Modifier.height(12.dp))

            PermissionCard(
                icon = Icons.Default.PowerSettingsNew,
                title = "HyperOS / MIUI: Autostart",
                description = "Enable Autostart for LocalLLM so the service can be restored " +
                    "after a reboot or a memory-pressure kill.",
                granted = false,
                actionLabel = "Open Autostart settings",
                onAction = onOpenAutostart
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionCard(
                icon = Icons.Default.BatteryAlert,
                title = "HyperOS / MIUI: Battery saver",
                description = "Set the battery policy of LocalLLM to \"No restrictions\" in the " +
                    "Xiaomi security centre.",
                granted = false,
                actionLabel = "Open security centre",
                onAction = onOpenSecurityCenter
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        PermissionCard(
            icon = Icons.Default.FolderOpen,
            title = "Model file",
            description = "Select a .gguf model. The file is copied into the private app " +
                "storage so the engine can read it directly.",
            granted = modelSelected,
            actionLabel = "Choose .gguf file",
            onAction = onPickModel
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Finish setup")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip for now")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (granted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onAction,
                enabled = !granted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (granted) "Granted" else actionLabel)
            }
        }
    }
}
