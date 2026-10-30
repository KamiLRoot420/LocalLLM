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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rootslab.localllm.models.ServerConfig
import com.rootslab.localllm.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val wakeLock by viewModel.wakeLock.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var ngl by remember { mutableIntStateOf(config.ngl) }
    var ctxSize by remember { mutableIntStateOf(config.ctxSize) }
    var threads by remember { mutableIntStateOf(config.threads) }
    var temperature by remember { mutableFloatStateOf(config.temperature) }
    var topK by remember { mutableIntStateOf(config.topK) }
    var topP by remember { mutableFloatStateOf(config.topP) }
    var repeatPenalty by remember { mutableFloatStateOf(config.repeatPenalty) }
    var maxTokens by remember { mutableIntStateOf(config.maxTokens) }
    var port by remember { mutableIntStateOf(config.port) }
    var presetName by remember { mutableStateOf("") }

    LaunchedEffect(config) {
        ngl = config.ngl
        ctxSize = config.ctxSize
        threads = config.threads
        temperature = config.temperature
        topK = config.topK
        topP = config.topP
        repeatPenalty = config.repeatPenalty
        maxTokens = config.maxTokens
        port = config.port
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeStatusMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SectionHeader("Performance")
                SliderRow(
                    label = "GPU offload layers (-ngl)",
                    value = ngl.toFloat(),
                    range = 0f..99f,
                    steps = 98,
                    valueLabel = ngl.toString(),
                    onValueChange = { ngl = it.roundToInt() }
                )
                SliderRow(
                    label = "Context size (-c)",
                    value = ctxSize.toFloat(),
                    range = 512f..131072f,
                    steps = 0,
                    valueLabel = ctxSize.toString(),
                    onValueChange = { ctxSize = (it / 512f).roundToInt() * 512 }
                )
                SliderRow(
                    label = "CPU threads (-t)",
                    value = threads.toFloat(),
                    range = 1f..16f,
                    steps = 15,
                    valueLabel = threads.toString(),
                    onValueChange = { threads = it.roundToInt() }
                )
                SliderRow(
                    label = "Max tokens",
                    value = maxTokens.toFloat(),
                    range = 64f..4096f,
                    steps = 0,
                    valueLabel = maxTokens.toString(),
                    onValueChange = { maxTokens = (it / 64f).roundToInt() * 64 }
                )
                OutlinedButton(
                    onClick = { viewModel.autoDetect() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auto-detect safe parameters")
                }
            }

            item {
                SectionHeader("Sampling")
                SliderRow(
                    label = "Temperature",
                    value = temperature,
                    range = 0f..2f,
                    steps = 0,
                    valueLabel = String.format("%.2f", temperature),
                    onValueChange = { temperature = it }
                )
                SliderRow(
                    label = "Top-K",
                    value = topK.toFloat(),
                    range = 0f..100f,
                    steps = 100,
                    valueLabel = topK.toString(),
                    onValueChange = { topK = it.roundToInt() }
                )
                SliderRow(
                    label = "Top-P",
                    value = topP,
                    range = 0f..1f,
                    steps = 0,
                    valueLabel = String.format("%.2f", topP),
                    onValueChange = { topP = it }
                )
                SliderRow(
                    label = "Repetition penalty",
                    value = repeatPenalty,
                    range = 1f..2f,
                    steps = 0,
                    valueLabel = String.format("%.2f", repeatPenalty),
                    onValueChange = { repeatPenalty = it }
                )
            }

            item {
                SectionHeader("Network")
                SliderRow(
                    label = "Port",
                    value = port.toFloat(),
                    range = 1024f..65535f,
                    steps = 0,
                    valueLabel = port.toString(),
                    onValueChange = { port = it.roundToInt() }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = wakeLock,
                        onCheckedChange = { viewModel.setWakeLock(it) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("CPU WakeLock", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Keeps the CPU awake while the screen is off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionHeader("Presets")
            }

            items(presets.keys.toList(), key = { it }) { name ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                            val preset = presets[name]
                            Text(
                                text = "ngl ${preset?.ngl} • ctx ${preset?.ctxSize} • t ${preset?.threads}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { viewModel.applyPreset(name) }) {
                            Text("Apply")
                        }
                        if (name !in ServerConfig.BUILT_IN_PRESETS) {
                            IconButton(onClick = { viewModel.deletePreset(name) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete preset"
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("New preset name") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.savePreset(presetName)
                            presetName = ""
                        },
                        enabled = presetName.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.updateConfig(
                            config.copy(
                                ngl = ngl,
                                ctxSize = ctxSize,
                                threads = threads,
                                temperature = temperature,
                                topK = topK,
                                topP = topP,
                                repeatPenalty = repeatPenalty,
                                maxTokens = maxTokens,
                                port = port
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Save configuration")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}
