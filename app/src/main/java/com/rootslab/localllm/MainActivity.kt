package com.rootslab.localllm

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rootslab.localllm.di.ServiceLocator
import com.rootslab.localllm.models.ServerConfig
import com.rootslab.localllm.ui.screens.AboutScreen
import com.rootslab.localllm.ui.screens.ChatScreen
import com.rootslab.localllm.ui.screens.DashboardScreen
import com.rootslab.localllm.ui.screens.OnboardingScreen
import com.rootslab.localllm.ui.screens.SettingsScreen
import com.rootslab.localllm.ui.theme.LocalLLMTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pickModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importModel(uri)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                getString(R.string.notification_permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(applicationContext)

        setContent {
            LocalLLMTheme {
                AppRoot(
                    onPickModel = { pickModelLauncher.launch(arrayOf("*/*")) },
                    onRequestNotifications = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }
        }
    }

    /**
     * Copies the picked GGUF file into the private app storage and stores the
     * resulting path in the DataStore so that the engine can memory-map it.
     */
    private fun importModel(uri: Uri) {
        lifecycleScope.launch {
            val imported = ServiceLocator.modelRepository.import(uri).getOrNull()
            if (imported == null) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.model_import_failed),
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val config = ServiceLocator.dataStore.currentConfig()
            ServiceLocator.dataStore.updateConfig(
                config.copy(
                    modelPath = imported.absolutePath,
                    modelName = imported.name
                )
            )

            Toast.makeText(
                this@MainActivity,
                getString(R.string.model_imported, imported.name),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

private sealed class Destination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Dashboard : Destination("dashboard", "Dashboard", Icons.Default.Dashboard)
    data object Chat : Destination("chat", "Chat", Icons.Default.Chat)
    data object Settings : Destination("settings", "Settings", Icons.Default.Settings)
    data object About : Destination("about", "About", Icons.Default.Info)
}

/**
 * Resolves the hosting [Activity] from a (possibly wrapped) [Context].
 *
 * The vendor specific deep links of [PermissionManager] need a real Activity
 * because they are started with `startActivity()`.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun AppRoot(
    onPickModel: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val onboardingDone by ServiceLocator.dataStore.onboardingDoneFlow
        .collectAsStateWithLifecycle(initialValue = false)
    val config by ServiceLocator.dataStore.configFlow
        .collectAsStateWithLifecycle(initialValue = ServerConfig())

    var showOnboarding by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(onboardingDone) {
        showOnboarding = !onboardingDone
    }

    if (showOnboarding) {
        OnboardingScreen(
            notificationsGranted = PermissionManager.hasNotificationPermission(context),
            batteryExempt = PermissionManager.isIgnoringBatteryOptimizations(context),
            isXiaomiDevice = PermissionManager.isXiaomiDevice(),
            modelSelected = config.modelPath.isNotBlank(),
            onRequestNotifications = onRequestNotifications,
            onRequestBatteryExemption = {
                activity?.let { PermissionManager.requestIgnoreBatteryOptimizations(it) }
            },
            onOpenAutostart = {
                activity?.let { PermissionManager.openAutostartSettings(it) }
            },
            onOpenSecurityCenter = {
                activity?.let { PermissionManager.openXiaomiSecurityCenter(it) }
            },
            onPickModel = onPickModel,
            onFinish = {
                scope.launch {
                    ServiceLocator.dataStore.setOnboardingDone(true)
                }
                showOnboarding = false
            }
        )
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(
                    Destination.Dashboard,
                    Destination.Chat,
                    Destination.Settings,
                    Destination.About
                ).forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destination.Dashboard.route) {
                DashboardScreen(
                    onPickModel = onPickModel,
                    onOpenSettings = {
                        navController.navigate(Destination.Settings.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Destination.Chat.route) { ChatScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
            composable(Destination.About.route) { AboutScreen() }
        }
    }
}
