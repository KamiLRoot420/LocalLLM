package com.rootslab.localllm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rootslab.localllm.BuildConfig
import com.rootslab.localllm.UpdateManager
import kotlinx.coroutines.launch

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var checking by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("LocalLLM AI Server", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Developer: Roots Lab",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        InfoCard(
            title = "Privacy policy",
            body = "LocalLLM AI Server works 100% offline. No personal data, prompts, " +
                "responses or telemetry are collected or transmitted. The only network " +
                "traffic the application can generate is an optional check for a new " +
                "release on GitHub. The embedded HTTP server binds exclusively to the " +
                "loopback interface (127.0.0.1) and is therefore unreachable from other " +
                "devices or the internet."
        )

        Spacer(modifier = Modifier.height(12.dp))

        InfoCard(
            title = "License",
            body = "This application is distributed under the MIT License. The embedded " +
                "inference engine is llama.cpp, Copyright (c) 2023-2024 The ggml authors, " +
                "also distributed under the MIT License. The full license text is included " +
                "in the LICENSE file of the source repository."
        )

        Spacer(modifier = Modifier.height(12.dp))

        InfoCard(
            title = "AI generated content disclaimer",
            body = "The output produced by the local language model may be inaccurate, " +
                "misleading or offensive. It reflects the training data of the model and " +
                "not the opinion of Roots Lab. The user is solely responsible for the use " +
                "of any generated content and should verify important information " +
                "independently."
        )

        Spacer(modifier = Modifier.height(12.dp))

        InfoCard(
            title = "Open source",
            body = "Source code and release builds are published on GitHub at " +
                "github.com/RootsLab/LocalLLM. Contributions, bug reports and feature " +
                "requests are welcome."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("Updates", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    checking = true
                    updateMessage = null
                    scope.launch {
                        val release = UpdateManager.checkForUpdate(BuildConfig.VERSION_NAME)
                        checking = false
                        if (release == null) {
                            updateMessage = "You are running the latest version."
                        } else {
                            updateMessage = "Version ${release.version} is available."
                            UpdateManager.downloadApk(context, release)
                            UpdateManager.openDownloads(context)
                        }
                    }
                },
                enabled = !checking
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Check for updates")
            }

            if (checking) {
                Spacer(modifier = Modifier.width(16.dp))
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        updateMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "MIT License • llama.cpp is embedded under the same terms",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
