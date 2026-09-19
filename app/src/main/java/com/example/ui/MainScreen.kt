package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppTopBar
import com.example.ui.screens.ConversationScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.LanguagesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TranslateScreen
import com.example.ui.theme.MintPrimaryDark
import com.example.ui.theme.OceanBlue

@Composable
fun MainScreen(
    viewModel: MainViewModel
) {
    var showSplash by remember { mutableStateOf(true) }
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()

    if (showSplash) {
        SplashScreen(
            onSplashFinished = { showSplash = false }
        )
    } else {
        Scaffold(
            topBar = {
                AppTopBar(isOffline = isOffline)
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.navigationBarsPadding().testTag("bottom_navigation_bar")
                ) {
                    val items = listOf(
                        Triple(0, "Translate", Icons.Filled.Translate to Icons.Outlined.Translate),
                        Triple(1, "Chat", Icons.Filled.Chat to Icons.Outlined.Chat),
                        Triple(2, "History", Icons.Filled.History to Icons.Outlined.History),
                        Triple(3, "Languages", Icons.Filled.Language to Icons.Outlined.Language),
                        Triple(4, "Settings", Icons.Filled.Settings to Icons.Outlined.Settings)
                    )

                    items.forEach { (index, title, icons) ->
                        val isSelected = selectedTab == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.setSelectedTab(index) },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) icons.first else icons.second,
                                    contentDescription = title
                                )
                            },
                            label = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp
                                    )
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MintPrimaryDark,
                                selectedTextColor = MintPrimaryDark,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_tab_$index")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tab_transition"
                ) { tab ->
                    when (tab) {
                        0 -> TranslateScreen(viewModel = viewModel)
                        1 -> ConversationScreen(viewModel = viewModel)
                        2 -> HistoryScreen(viewModel = viewModel)
                        3 -> LanguagesScreen(viewModel = viewModel)
                        4 -> SettingsScreen(viewModel = viewModel)
                        else -> TranslateScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
