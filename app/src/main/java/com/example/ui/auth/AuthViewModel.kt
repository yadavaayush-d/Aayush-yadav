package com.example.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.UserEntity
import com.example.data.repository.NexaRepository
import com.example.domain.auth.GoogleSignInResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val user: UserEntity) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(
    private val repository: NexaRepository
) : ViewModel() {

    val currentUser = repository.currentUser
    val isOnboardingCompleted = repository.isOnboardingCompleted

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _isGoogleLoading = MutableStateFlow(false)
    val isGoogleLoading: StateFlow<Boolean> = _isGoogleLoading.asStateFlow()

    fun completeOnboarding() {
        repository.completeOnboarding()
    }

    fun continueWithGoogle(context: Context) {
        if (_isGoogleLoading.value || _uiState.value is AuthUiState.Loading) {
            return
        }

        viewModelScope.launch {
            _isGoogleLoading.value = true
            _uiState.value = AuthUiState.Idle

            val result = repository.signInWithGoogle(context)
            when (result) {
                is GoogleSignInResult.Success -> {
                    val authResult = repository.authenticateWithGoogle(
                        email = result.account.email,
                        displayName = result.account.displayName,
                        photoUrl = result.account.photoUrl
                    )
                    authResult.fold(
                        onSuccess = { user ->
                            _uiState.value = AuthUiState.Success(user)
                        },
                        onFailure = { err ->
                            _uiState.value = AuthUiState.Error(err.message ?: "Google authentication failed")
                        }
                    )
                }
                is GoogleSignInResult.Cancelled -> {
                    // Safe return to login/signup screen without error
                    _uiState.value = AuthUiState.Idle
                }
                is GoogleSignInResult.NotConfigured -> {
                    _uiState.value = AuthUiState.Error(result.message)
                }
                is GoogleSignInResult.Error -> {
                    _uiState.value = AuthUiState.Error(result.message)
                }
            }
            _isGoogleLoading.value = false
        }
    }

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.login(email, pass)
            result.fold(
                onSuccess = { user -> _uiState.value = AuthUiState.Success(user) },
                onFailure = { err -> _uiState.value = AuthUiState.Error(err.message ?: "Authentication failed") }
            )
        }
    }

    fun loginDemo(userType: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val user = repository.loginAsDemo(userType)
            _uiState.value = AuthUiState.Success(user)
        }
    }

    fun loginAdmin() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val admin = repository.loginAsAdmin()
            _uiState.value = AuthUiState.Success(admin)
        }
    }

    fun signUp(
        name: String,
        email: String,
        pass: String,
        language: String,
        userType: String
    ) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.signUp(name, email, pass, language, userType)
            result.fold(
                onSuccess = { user -> _uiState.value = AuthUiState.Success(user) },
                onFailure = { err -> _uiState.value = AuthUiState.Error(err.message ?: "Sign up failed") }
            )
        }
    }

    fun logout() {
        repository.logout()
        _uiState.value = AuthUiState.Idle
    }

    fun resetError() {
        _uiState.value = AuthUiState.Idle
    }
}
