package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.preferences.JarvisLanguageMode
import com.example.ui.components.SensitiveActionConfirmationDialog
import com.example.ui.screens.AppBuilderScreen
import com.example.ui.screens.AppControlsScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.state.AppBuilderViewModel
import com.example.ui.state.JarvisAssistantViewModel
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepSpace
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisPanelElevated
import com.example.ui.theme.JarvisTextSecondary
import com.example.voice.VoiceState

object JarvisRoutes {
    const val DASHBOARD = "dashboard"
    const val CHAT = "chat"
    const val BUILDER = "builder"
    const val CONTROLS = "controls"
    const val SETTINGS = "settings"
}

private data class JarvisNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    JarvisNavItem(JarvisRoutes.DASHBOARD, "HUD", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq),
    JarvisNavItem(JarvisRoutes.CHAT, "Chat", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    JarvisNavItem(JarvisRoutes.BUILDER, "Builder", Icons.Filled.Build, Icons.Outlined.Build),
    JarvisNavItem(JarvisRoutes.CONTROLS, "Controls", Icons.Filled.Tune, Icons.Outlined.Tune),
    JarvisNavItem(JarvisRoutes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun JarvisAppRoot(
    assistantVm: JarvisAssistantViewModel = viewModel(),
    builderVm: AppBuilderViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by assistantVm.settings.collectAsStateWithLifecycle()
    val messages by assistantVm.messages.collectAsStateWithLifecycle()
    val memoryFacts by assistantVm.memoryFacts.collectAsStateWithLifecycle()
    val voiceState by assistantVm.voiceState.collectAsStateWithLifecycle()
    val rmsDb by assistantVm.rmsDb.collectAsStateWithLifecycle()
    val partialTranscript by assistantVm.partialTranscript.collectAsStateWithLifecycle()
    val voiceError by assistantVm.voiceError.collectAsStateWithLifecycle()
    val aiState by assistantVm.aiState.collectAsStateWithLifecycle()
    val pendingConfirmation by assistantVm.pendingConfirmation.collectAsStateWithLifecycle()
    val telemetry by assistantVm.telemetry.collectAsStateWithLifecycle()
    val permissions by assistantVm.permissions.collectAsStateWithLifecycle()
    val installedApps by assistantVm.installedApps.collectAsStateWithLifecycle()
    val isOnline by assistantVm.isOnline.collectAsStateWithLifecycle()
    val accessibilityConnected by assistantVm.accessibilityConnected.collectAsStateWithLifecycle()
    val statusBanner by assistantVm.statusBanner.collectAsStateWithLifecycle()
    val pendingBuilderPrompt by assistantVm.pendingBuilderPrompt.collectAsStateWithLifecycle()

    val projects by builderVm.projects.collectAsStateWithLifecycle()
    val selectedProjectId by builderVm.selectedProjectId.collectAsStateWithLifecycle()
    val projectFiles by builderVm.selectedProjectFiles.collectAsStateWithLifecycle()
    val selectedFileId by builderVm.selectedFileId.collectAsStateWithLifecycle()
    val builderState by builderVm.builderState.collectAsStateWithLifecycle()
    val validationIssues by builderVm.validationIssues.collectAsStateWithLifecycle()
    val shareIntentEvent by builderVm.shareIntentEvent.collectAsStateWithLifecycle()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        assistantVm.refreshSystemState()
        if (granted) {
            assistantVm.voiceManager.startCommandListening()
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        assistantVm.refreshSystemState()
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        assistantVm.refreshSystemState()
    }

    fun handleMicTrigger() {
        when (voiceState) {
            VoiceState.LISTENING -> assistantVm.voiceManager.stopListening()
            VoiceState.SPEAKING -> assistantVm.voiceManager.stopSpeaking()
            else -> {
                if (permissions.micGranted) {
                    assistantVm.voiceManager.startCommandListening()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    LaunchedEffect(statusBanner) {
        if (!statusBanner.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusBanner!!)
            assistantVm.clearBanner()
        }
    }

    LaunchedEffect(pendingBuilderPrompt) {
        if (pendingBuilderPrompt != null) {
            navController.navigate(JarvisRoutes.BUILDER) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(shareIntentEvent) {
        if (shareIntentEvent != null) {
            val intent = builderVm.consumeShareIntent()
            if (intent != null) {
                context.startActivity(android.content.Intent.createChooser(intent, "Share Project Bundle"))
            }
        }
    }

    pendingConfirmation?.let { confirmation ->
        SensitiveActionConfirmationDialog(
            confirmation = confirmation,
            onConfirm = { assistantVm.confirmPendingAction() },
            onDismiss = { assistantVm.dismissPendingAction() }
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: JarvisRoutes.DASHBOARD

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = JarvisObsidian,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = JarvisDeepSpace,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = JarvisCyan,
                                    selectedTextColor = JarvisCyan,
                                    indicatorColor = JarvisPanelElevated,
                                    unselectedIconColor = JarvisTextSecondary,
                                    unselectedTextColor = JarvisTextSecondary
                                ),
                                modifier = Modifier.testTag("nav_${item.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = JarvisDeepSpace,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = JarvisCyan,
                                    selectedTextColor = JarvisCyan,
                                    indicatorColor = JarvisPanelElevated,
                                    unselectedIconColor = JarvisTextSecondary,
                                    unselectedTextColor = JarvisTextSecondary
                                ),
                                modifier = Modifier.testTag("nav_rail_${item.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = JarvisRoutes.DASHBOARD,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(JarvisRoutes.DASHBOARD) {
                        DashboardScreen(
                            settings = settings,
                            voiceState = voiceState,
                            rmsDb = rmsDb,
                            partialTranscript = partialTranscript,
                            voiceError = voiceError,
                            aiState = aiState,
                            telemetry = telemetry,
                            isOnline = isOnline,
                            latestJarvisMessage = messages.lastOrNull { it.role == "jarvis" },
                            onOrbMicClick = ::handleMicTrigger,
                            onSendCommand = { assistantVm.submitUserMessage(it, speakResponse = false) },
                            onToggleContinuous = { assistantVm.updateContinuousVoice(it) },
                            onToggleWakeWord = { enabled ->
                                if (enabled && !permissions.micGranted) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                                assistantVm.updateWakeWordEnabled(enabled)
                            },
                            onCycleLanguage = {
                                val next = when (settings.languageMode) {
                                    JarvisLanguageMode.BILINGUAL -> JarvisLanguageMode.HINDI
                                    JarvisLanguageMode.HINDI -> JarvisLanguageMode.ENGLISH
                                    JarvisLanguageMode.ENGLISH -> JarvisLanguageMode.BILINGUAL
                                }
                                assistantVm.updateLanguageMode(next)
                            },
                            onOpenChat = { navigateTo(JarvisRoutes.CHAT) },
                            onOpenBuilder = { navigateTo(JarvisRoutes.BUILDER) }
                        )
                    }

                    composable(JarvisRoutes.CHAT) {
                        ChatScreen(
                            messages = messages,
                            aiState = aiState,
                            voiceState = voiceState,
                            onSendMessage = { assistantVm.submitUserMessage(it, speakResponse = false) },
                            onMicClick = ::handleMicTrigger,
                            onSpeakText = { assistantVm.voiceManager.speak(it) },
                            onStopSpeaking = { assistantVm.voiceManager.stopSpeaking() },
                            onOpenUrl = { assistantVm.deviceManager.openUrlInBrowser(it) },
                            onImportFileUri = { assistantVm.analyzeImportedFileInChat(it) }
                        )
                    }

                    composable(JarvisRoutes.BUILDER) {
                        val incoming = remember(pendingBuilderPrompt) {
                            assistantVm.consumePendingBuilderPrompt()
                        }
                        AppBuilderScreen(
                            projects = projects,
                            selectedProjectId = selectedProjectId,
                            files = projectFiles,
                            selectedFileId = selectedFileId,
                            builderState = builderState,
                            validationIssues = validationIssues,
                            initialPromptPair = incoming,
                            onGenerateProject = { prompt, type ->
                                builderVm.generateNewProject(prompt, type)
                            },
                            onSelectProject = { builderVm.selectProject(it) },
                            onSelectFile = { builderVm.selectFile(it) },
                            onSaveFileContent = { file, content ->
                                builderVm.updateFileContent(file, content)
                            },
                            onAnalyzeAndFix = { builderVm.runPreflightAndAiFix() },
                            onImportExternalFile = { builderVm.importFileIntoWorkspace(it) },
                            onExportProjectZip = { builderVm.exportCurrentProjectToUri(it) },
                            onShareProjectZip = { builderVm.shareCurrentProjectZip() },
                            onDeleteProject = { builderVm.deleteProject(it) }
                        )
                    }

                    composable(JarvisRoutes.CONTROLS) {
                        AppControlsScreen(
                            permissions = permissions,
                            installedApps = installedApps,
                            accessibilityConnected = accessibilityConnected,
                            onRequestMicPermission = {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onRequestCallPermission = {
                                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                            },
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onOpenAccessibilitySettings = {
                                assistantVm.deviceManager.openAccessibilitySettings()
                            },
                            onOpenAppSettings = {
                                assistantVm.deviceManager.openAppDetailsSettings()
                            },
                            onLaunchApp = { pkg ->
                                assistantVm.deviceManager.launchApp(pkg)
                            },
                            onRequestCallConfirm = { label, num ->
                                assistantVm.requestCallConfirmation(label, num)
                            },
                            onRequestSmsConfirm = { label, num, body ->
                                assistantVm.requestSmsConfirmation(label, num, body)
                            }
                        )
                    }

                    composable(JarvisRoutes.SETTINGS) {
                        SettingsScreen(
                            settings = settings,
                            memoryFacts = memoryFacts,
                            telemetry = telemetry,
                            onSelectLanguage = { assistantVm.updateLanguageMode(it) },
                            onSelectVoiceGender = { assistantVm.updateVoiceGender(it) },
                            onChangeSpeechRate = { assistantVm.updateSpeechRate(it) },
                            onChangeSpeechPitch = { assistantVm.updateSpeechPitch(it) },
                            onTestVoice = { assistantVm.testVoiceSample() },
                            onToggleContinuousVoice = { assistantVm.updateContinuousVoice(it) },
                            onToggleWakeWord = { enabled ->
                                if (enabled && !permissions.micGranted) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                                assistantVm.updateWakeWordEnabled(enabled)
                            },
                            onToggleBackgroundWakeService = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissions.notificationsGranted) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                assistantVm.updateBackgroundWakeService(enabled)
                            },
                            onToggleSaveHistory = { assistantVm.updateSaveHistory(it) },
                            onToggleAutoMemory = { assistantVm.updateAutoMemory(it) },
                            onTogglePreferDirectCall = { assistantVm.updatePreferDirectCall(it) },
                            onToggleLowRamMode = { assistantVm.updateLowRamOptimized(it) },
                            onAddMemoryFact = { assistantVm.addManualMemoryFact(it) },
                            onToggleMemoryFact = { assistantVm.toggleMemoryFact(it) },
                            onDeleteMemoryFact = { assistantVm.deleteMemoryFact(it) },
                            onClearChatHistory = { assistantVm.clearChatHistory() },
                            onWipeAllMemory = { assistantVm.wipeAllMemory() }
                        )
                    }
                }
            }
        }
    }
}
