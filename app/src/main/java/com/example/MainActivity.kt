package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.example.ui.chat.ChatBottomSheet
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MedicinesScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(
            application = application,
            repository = (application as MedTrackApplication).repository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val isOnboardedState by viewModel.isOnboarded.collectAsState()

                // Chat sheet state lives here so it can be opened from any tab and
                // overlays the bottom navigation cleanly.
                var showChatSheet by remember { mutableStateOf(false) }
                var chatPrefilledMedicine by remember { mutableStateOf<String?>(null) }

                val openChat: (String?) -> Unit = { medicineName ->
                    chatPrefilledMedicine = medicineName
                    showChatSheet = true
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    when (isOnboardedState) {
                        null -> {
                            // Loading state placeholder
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
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
                                onOpenChat = openChat,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }

                if (showChatSheet) {
                    ChatBottomSheet(
                        onDismiss = {
                            showChatSheet = false
                            chatPrefilledMedicine = null
                        },
                        prefilledMedicine = chatPrefilledMedicine
                    )
                }
            }
        }
    }
}

@Composable
fun MainContentLayout(
    viewModel: MainViewModel,
    onOpenChat: (String?) -> Unit,
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
                    onOpenChat = { onOpenChat(null) }
                )
                1 -> MedicinesScreen(
                    viewModel = viewModel,
                    onAskAi = { medicineName -> onOpenChat(medicineName) }
                )
                2 -> AnalyticsScreen(viewModel = viewModel)
            }
        }
    }
}
