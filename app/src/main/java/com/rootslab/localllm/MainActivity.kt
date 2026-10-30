package com.rootslab.localllm

import android.Manifest
import android.content.Intent
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rootslab.localllm.di.ServiceLocator
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

    private fun importModel(uri: Uri) {
        lifecycleScope.launch {
            val path = ServiceLocator.modelRepository.importModel(uri)
            if (path == null) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.model_import_failed),
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            ServiceLocator.dataStore.setModelPath(path)
            Toast.makeText(
                this@MainActivity,
                getString(R.string.model_imported, path.substringAfterLast('/')),
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

@Composable
private fun AppRoot(
    onPickModel: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val onboardingCompleted by ServiceLocator.dataStore.onboardingCompletedFlow
        .collectAsStateWithLifecycle(initialValue = false)
    val config by ServiceLocator.dataStore.configFlow
        .collectAsStateWithLifecycle(initialValue = com.rootslab.localllm.models.ServerConfig())

    var showOnboarding by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(onboardingCompleted) {
        showOnboarding = !onboardingCompleted
    }

    if (showOnboarding) {
        OnboardingScreen(
            notificationsGranted = PermissionManager.hasNotificationPermission(context),
            batteryExempt = PermissionManager.isIgnoringBatteryOptimizations(context),
            isXiaomiDevice = PermissionManager.isXiaomiDevice(),
            modelSelected = config.modelPath.isNotBlank(),
            onRequestNotifications = onRequestNotifications,
            onRequestBatteryExemption = {
                PermissionManager.requestIgnoreBatteryOptimizations(context)
            },
            onOpenAutostart = {
                PermissionManager.openAutostartSettings(context)
            },
            onOpenSecurityCenter = {
                PermissionManager.openXiaomiSecurityCenter(context)
            },
            onPickModel = onPickModel,
            onFinish = {
                scope.launch {
                    ServiceLocator.dataStore.setOnboardingCompleted(true)
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
