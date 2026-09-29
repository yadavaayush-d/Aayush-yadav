package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.repository.NexaRepository
import com.example.ui.admin.AdminScreen
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.OnboardingScreen
import com.example.ui.auth.SignUpScreen
import com.example.ui.auth.SplashScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.settings.SettingsScreen

@Composable
fun AppNavHost(
    repository: NexaRepository,
    navController: NavHostController = rememberNavController()
) {
    val authViewModel = remember { AuthViewModel(repository) }
    val chatViewModel = remember { ChatViewModel(repository) }

    val currentUser by repository.currentUser.collectAsState()
    val isOnboardingCompleted by repository.isOnboardingCompleted.collectAsState()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.SPLASH
    ) {
        // Splash Screen
        composable(AppRoutes.SPLASH) {
            SplashScreen(
                onSplashFinished = {
                    if (currentUser != null) {
                        navController.navigate(AppRoutes.MAIN) {
                            popUpTo(AppRoutes.SPLASH) { inclusive = true }
                        }
                    } else if (isOnboardingCompleted) {
                        navController.navigate(AppRoutes.LOGIN) {
                            popUpTo(AppRoutes.SPLASH) { inclusive = true }
                        }
                    } else {
                        navController.navigate(AppRoutes.ONBOARDING) {
                            popUpTo(AppRoutes.SPLASH) { inclusive = true }
                        }
                    }
                }
            )
        }

        // Onboarding Screen
        composable(AppRoutes.ONBOARDING) {
            OnboardingScreen(
                onFinishOnboarding = {
                    authViewModel.completeOnboarding()
                    navController.navigate(AppRoutes.SIGN_UP) {
                        popUpTo(AppRoutes.ONBOARDING) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    authViewModel.completeOnboarding()
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(AppRoutes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // Login Screen
        composable(AppRoutes.LOGIN) {
            LoginScreen(
                authViewModel = authViewModel,
                onNavigateToSignUp = {
                    navController.navigate(AppRoutes.SIGN_UP)
                },
                onLoginSuccess = {
                    navController.navigate(AppRoutes.MAIN) {
                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // Sign Up Screen
        composable(AppRoutes.SIGN_UP) {
            SignUpScreen(
                authViewModel = authViewModel,
                onNavigateToLogin = {
                    navController.navigate(AppRoutes.LOGIN)
                },
                onSignUpSuccess = {
                    navController.navigate(AppRoutes.MAIN) {
                        popUpTo(AppRoutes.SIGN_UP) { inclusive = true }
                    }
                }
            )
        }

        // Main App Scaffold (Home, AI, Tools, History, Profile)
        composable(AppRoutes.MAIN) {
            MainAppScaffold(
                repository = repository,
                chatViewModel = chatViewModel,
                onNavigateToSettings = {
                    navController.navigate(AppRoutes.SETTINGS)
                },
                onNavigateToAdmin = {
                    navController.navigate(AppRoutes.ADMIN)
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(AppRoutes.MAIN) { inclusive = true }
                    }
                }
            )
        }

        // Settings Screen
        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }

        // Admin Screen
        composable(AppRoutes.ADMIN) {
            AdminScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }

        // Study AI Dedicated Workspace
        composable(AppRoutes.STUDY_AI) {
            val studyViewModel = remember { com.example.ui.study.StudyViewModel(repository) }
            com.example.ui.study.StudyAIScreen(
                viewModel = studyViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
