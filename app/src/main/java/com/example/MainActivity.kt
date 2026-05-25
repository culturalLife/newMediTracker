package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.caregiver.CaregiverNotifier
import com.example.data.preferences.AppLockPreferences
import com.example.report.HtmlReportBuilder
import com.example.ui.chat.ChatBottomSheet
import com.example.ui.health.HealthInsightsViewModel
import com.example.ui.lock.AppLockScreen
import com.example.ui.report.ReportScreen
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MedicinesScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.computeStreaks
import com.example.ui.settings.SettingsScreen
import com.example.ui.streak.AchievementTracker
import com.example.ui.streak.MilestoneConfetti
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

/**
 * Top-level activity. Extends [FragmentActivity] (a superset of ComponentActivity) so
 * the AndroidX BiometricPrompt can attach for the Privacy Shield unlock flow.
 *
 * Owns three globally-visible overlays — chat sheet, HTML report, lock screen, and
 * settings — that need to sit above the bottom nav and persist across tab switches.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(
            application = application,
            repository = (application as MedTrackApplication).repository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appLockPreferences = AppLockPreferences(applicationContext)
        val achievementTracker = AchievementTracker(applicationContext)

        setContent {
            MyApplicationTheme {
                val isOnboardedState by viewModel.isOnboarded.collectAsState()
                val context = LocalContext.current

                val insightsVM: HealthInsightsViewModel = viewModel(
                    factory = HealthInsightsViewModel.Factory()
                )

                // Privacy Shield: cold-start lock gate. Once unlocked, stays unlocked
                // for the lifetime of the activity so tab switches don't re-prompt.
                var unlocked by remember { mutableStateOf(!appLockPreferences.isLockEnabled) }

                // Chat overlay state.
                var showChatSheet by remember { mutableStateOf(false) }
                var chatPrefilledMedicine by remember { mutableStateOf<String?>(null) }
                val openChat: (String?) -> Unit = { medicineName ->
                    chatPrefilledMedicine = medicineName
                    showChatSheet = true
                }

                // Report overlay state.
                var reportHtml by remember { mutableStateOf<String?>(null) }
                var reportProfileName by remember { mutableStateOf("") }
                val openReport: () -> Unit = {
                    val profile = viewModel.selectedProfile.value
                    if (profile != null) {
                        reportProfileName = profile.name
                        reportHtml = HtmlReportBuilder.build(
                            profile = profile,
                            medicines = viewModel.medicinesList.value,
                            allLogs = viewModel.allProfileLogs.value,
                            windowDays = 30
                        )
                    }
                }

                // Settings overlay state.
                var showSettings by remember { mutableStateOf(false) }

                // Milestone celebration overlay state. Recomputes whenever the
                // user's all-logs flow emits — i.e. after every dose status change.
                val allLogs by viewModel.allProfileLogs.collectAsState()
                var pendingMilestone by remember {
                    mutableStateOf<com.example.ui.streak.Milestone?>(null)
                }
                LaunchedEffect(allLogs) {
                    val streak = computeStreaks(allLogs).current
                    val newOne = achievementTracker.consumeNewMilestone(streak)
                    if (newOne != null) pendingMilestone = newOne
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (isOnboardedState) {
                        null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        }
                        false -> {
                            OnboardingScreen(
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        true -> {
                            MainContentLayout(
                                viewModel = viewModel,
                                insightsVM = insightsVM,
                                onOpenChat = openChat,
                                onOpenReport = openReport,
                                onOpenSettings = { showSettings = true },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }

                // ─── Overlays (z-order: lock > settings > report > chat > milestone) ─────
                if (showChatSheet) {
                    ChatBottomSheet(
                        onDismiss = {
                            showChatSheet = false
                            chatPrefilledMedicine = null
                        },
                        prefilledMedicine = chatPrefilledMedicine
                    )
                }

                pendingMilestone?.let { milestone ->
                    MilestoneConfetti(
                        milestone = milestone,
                        onDismiss = { pendingMilestone = null }
                    )
                }

                reportHtml?.let { html ->
                    ReportScreen(
                        profileName = reportProfileName,
                        html = html,
                        onBack = {
                            reportHtml = null
                            reportProfileName = ""
                        }
                    )
                }

                if (showSettings) {
                    val activeProfile = viewModel.selectedProfile.collectAsState().value
                    SettingsScreen(
                        profile = activeProfile,
                        appLockPreferences = appLockPreferences,
                        onProfileUpdated = { updated -> viewModel.updateProfile(updated) },
                        onTestCaregiverSms = { profile ->
                            CaregiverNotifier.composeMissedDoseSms(
                                context = context,
                                profile = profile,
                                medicineName = "(test)",
                                scheduledTime = "12:00"
                            )
                        },
                        onBack = { showSettings = false }
                    )
                }

                if (!unlocked && isOnboardedState == true) {
                    AppLockScreen(
                        preferences = appLockPreferences,
                        onUnlocked = { unlocked = true }
                    )
                }
            }
        }
    }
}

@Composable
fun MainContentLayout(
    viewModel: MainViewModel,
    insightsVM: HealthInsightsViewModel,
    onOpenChat: (String?) -> Unit,
    onOpenReport: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    modifier = Modifier.testTag("tab_home")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Medicines") },
                    label = { Text("Medicines") },
                    modifier = Modifier.testTag("tab_medicines")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Analytics") },
                    label = { Text("Analytics") },
                    modifier = Modifier.testTag("tab_analytics")
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenSettings,
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    viewModel = viewModel,
                    insightsVM = insightsVM,
                    onOpenChat = { onOpenChat(null) }
                )
                1 -> MedicinesScreen(
                    viewModel = viewModel,
                    insightsVM = insightsVM,
                    onAskAi = { medicineName -> onOpenChat(medicineName) }
                )
                2 -> AnalyticsScreen(
                    viewModel = viewModel,
                    onExportReport = onOpenReport
                )
            }
        }
    }
}
