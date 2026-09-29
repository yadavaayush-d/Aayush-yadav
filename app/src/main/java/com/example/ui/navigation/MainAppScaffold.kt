package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import com.example.data.repository.NexaRepository
import com.example.domain.ai.AIModule
import com.example.ui.chat.AIChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.creator.CreatorAIScreen
import com.example.ui.creator.CreatorViewModel
import com.example.ui.documents.DocumentAIScreen
import com.example.ui.documents.DocumentViewModel
import com.example.ui.history.HistoryScreen
import com.example.ui.home.HomeScreen
import com.example.ui.language.LanguageAIScreen
import com.example.ui.language.LanguageViewModel
import com.example.ui.profile.ProfileScreen
import com.example.ui.study.StudyAIScreen
import com.example.ui.study.StudyViewModel
import com.example.ui.tools.ToolsScreen

@Composable
fun MainAppScaffold(
    repository: NexaRepository,
    chatViewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedDestination by remember { mutableStateOf(MainDestination.HOME) }
    var isStudyWorkspaceOpen by remember { mutableStateOf(false) }
    var isCreatorWorkspaceOpen by remember { mutableStateOf(false) }
    var isDocumentWorkspaceOpen by remember { mutableStateOf(false) }
    var isLanguageWorkspaceOpen by remember { mutableStateOf(false) }

    val studyViewModel = remember { StudyViewModel(repository) }
    val creatorViewModel = remember { CreatorViewModel(repository) }
    val documentViewModel = remember { DocumentViewModel(repository) }
    val languageViewModel = remember { LanguageViewModel(repository) }

    if (isStudyWorkspaceOpen) {
        StudyAIScreen(
            viewModel = studyViewModel,
            onBack = { isStudyWorkspaceOpen = false }
        )
        return
    }

    if (isCreatorWorkspaceOpen) {
        CreatorAIScreen(
            viewModel = creatorViewModel,
            onBack = { isCreatorWorkspaceOpen = false }
        )
        return
    }

    if (isDocumentWorkspaceOpen) {
        DocumentAIScreen(
            viewModel = documentViewModel,
            onBack = { isDocumentWorkspaceOpen = false }
        )
        return
    }

    if (isLanguageWorkspaceOpen) {
        LanguageAIScreen(
            viewModel = languageViewModel,
            onBack = { isLanguageWorkspaceOpen = false }
        )
        return
    }

    // Screen configuration check for responsive navigation (Section 3: Bottom Nav for mobile, Rail for desktop)
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600

    if (isExpandedScreen) {
        // Desktop / Tablet layout with Navigation Rail
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                MainDestination.entries.forEach { destination ->
                    val isSelected = selectedDestination == destination
                    NavigationRailItem(
                        selected = isSelected,
                        onClick = { selectedDestination = destination },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.title
                            )
                        },
                        label = { Text(destination.title) },
                        modifier = Modifier.testTag("nav_rail_${destination.route}")
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                MainContentHost(
                    destination = selectedDestination,
                    repository = repository,
                    chatViewModel = chatViewModel,
                    studyViewModel = studyViewModel,
                    onNavigateToDestination = { selectedDestination = it },
                    onOpenStudyWorkspace = { isStudyWorkspaceOpen = true },
                    onOpenCreatorWorkspace = { isCreatorWorkspaceOpen = true },
                    onOpenDocumentWorkspace = { isDocumentWorkspaceOpen = true },
                    onOpenLanguageWorkspace = { isLanguageWorkspaceOpen = true },
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToAdmin = onNavigateToAdmin,
                    onLogout = onLogout
                )
            }
        }
    } else {
        // Mobile layout with standard bottom NavigationBar (Section 3)
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    MainDestination.entries.forEach { destination ->
                        val isSelected = selectedDestination == destination
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedDestination = destination },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.title
                                )
                            },
                            label = { Text(destination.title) },
                            modifier = Modifier.testTag("bottom_nav_${destination.route}")
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
                MainContentHost(
                    destination = selectedDestination,
                    repository = repository,
                    chatViewModel = chatViewModel,
                    studyViewModel = studyViewModel,
                    onNavigateToDestination = { selectedDestination = it },
                    onOpenStudyWorkspace = { isStudyWorkspaceOpen = true },
                    onOpenCreatorWorkspace = { isCreatorWorkspaceOpen = true },
                    onOpenDocumentWorkspace = { isDocumentWorkspaceOpen = true },
                    onOpenLanguageWorkspace = { isLanguageWorkspaceOpen = true },
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToAdmin = onNavigateToAdmin,
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
fun MainContentHost(
    destination: MainDestination,
    repository: NexaRepository,
    chatViewModel: ChatViewModel,
    studyViewModel: StudyViewModel,
    onNavigateToDestination: (MainDestination) -> Unit,
    onOpenStudyWorkspace: () -> Unit,
    onOpenCreatorWorkspace: () -> Unit,
    onOpenDocumentWorkspace: () -> Unit,
    onOpenLanguageWorkspace: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onLogout: () -> Unit
) {
    when (destination) {
        MainDestination.HOME -> {
            HomeScreen(
                repository = repository,
                onNavigateToChat = { module, initialPrompt ->
                    when (module) {
                        AIModule.STUDY_AI -> onOpenStudyWorkspace()
                        AIModule.CREATOR_AI -> onOpenCreatorWorkspace()
                        AIModule.DOCUMENT_AI -> onOpenDocumentWorkspace()
                        AIModule.LANGUAGE_AI -> onOpenLanguageWorkspace()
                        else -> {
                            if (module != null) {
                                chatViewModel.startNewConversation(module, initialPrompt)
                            }
                            onNavigateToDestination(MainDestination.AI_CHAT)
                        }
                    }
                },
                onNavigateToTools = { onNavigateToDestination(MainDestination.TOOLS) },
                onNavigateToHistory = { onNavigateToDestination(MainDestination.HISTORY) }
            )
        }
        MainDestination.AI_CHAT -> {
            AIChatScreen(viewModel = chatViewModel)
        }
        MainDestination.TOOLS -> {
            ToolsScreen(
                onSelectTool = { module ->
                    when (module) {
                        AIModule.STUDY_AI -> onOpenStudyWorkspace()
                        AIModule.CREATOR_AI -> onOpenCreatorWorkspace()
                        AIModule.DOCUMENT_AI -> onOpenDocumentWorkspace()
                        AIModule.LANGUAGE_AI -> onOpenLanguageWorkspace()
                        else -> {
                            chatViewModel.startNewConversation(module)
                            onNavigateToDestination(MainDestination.AI_CHAT)
                        }
                    }
                }
            )
        }
        MainDestination.HISTORY -> {
            HistoryScreen(
                repository = repository,
                onOpenConversation = { convId, moduleName ->
                    chatViewModel.selectConversation(convId, moduleName)
                    onNavigateToDestination(MainDestination.AI_CHAT)
                },
                onStartNewChat = {
                    chatViewModel.startNewConversation(AIModule.GENERAL_AI)
                    onNavigateToDestination(MainDestination.AI_CHAT)
                }
            )
        }
        MainDestination.PROFILE -> {
            ProfileScreen(
                repository = repository,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToAdmin = onNavigateToAdmin,
                onLogout = onLogout
            )
        }
    }
}
