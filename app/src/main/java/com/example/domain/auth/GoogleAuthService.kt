package com.example.domain.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import com.example.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

data class GoogleAccountInfo(
    val id: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
    val idToken: String
)

sealed class GoogleSignInResult {
    data class Success(val account: GoogleAccountInfo) : GoogleSignInResult()
    object Cancelled : GoogleSignInResult()
    data class NotConfigured(val message: String) : GoogleSignInResult()
    data class Error(val message: String) : GoogleSignInResult()
}

interface GoogleAuthService {
    fun isConfigured(): Boolean
    suspend fun signInWithGoogle(context: Context): GoogleSignInResult
}

class DefaultGoogleAuthService(
    private val explicitClientId: String? = null
) : GoogleAuthService {

    private fun getResolvedClientId(): String {
        return (explicitClientId ?: BuildConfig.GOOGLE_CLIENT_ID).trim()
    }

    override fun isConfigured(): Boolean {
        val clientId = getResolvedClientId()
        return clientId.isNotBlank() && clientId != "MY_GOOGLE_CLIENT_ID" && !clientId.contains("YOUR_")
    }

    override suspend fun signInWithGoogle(context: Context): GoogleSignInResult {
        if (!isConfigured()) {
            val message = if (BuildConfig.DEBUG) {
                "Google OAuth is not configured. Please set GOOGLE_CLIENT_ID in the AI Studio Secrets panel."
            } else {
                "Google sign-in is temporarily unavailable. Please try again."
            }
            return GoogleSignInResult.NotConfigured(message)
        }

        val clientId = getResolvedClientId()

        return try {
            val credentialManager = CredentialManager.create(context)
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val response = credentialManager.getCredential(context = context, request = request)
            val credential = response.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName
                val photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                val idToken = googleIdTokenCredential.idToken

                if (email.isBlank() || !email.contains("@")) {
                    GoogleSignInResult.Error(
                        if (BuildConfig.DEBUG) "Invalid Google account email received"
                        else "Google sign-in failed. Please try again."
                    )
                } else {
                    GoogleSignInResult.Success(
                        GoogleAccountInfo(
                            id = email,
                            email = email,
                            displayName = displayName,
                            photoUrl = photoUrl,
                            idToken = idToken
                        )
                    )
                }
            } else {
                GoogleSignInResult.Error(
                    if (BuildConfig.DEBUG) "Unexpected credential response type: ${credential.type}"
                    else "Google sign-in is temporarily unavailable. Please try again."
                )
            }
        } catch (e: GetCredentialCancellationException) {
            // User backed out or tapped cancel - return safely
            GoogleSignInResult.Cancelled
        } catch (e: GetCredentialProviderConfigurationException) {
            val devMsg = "Google Play Services configuration error: ${e.message}"
            GoogleSignInResult.NotConfigured(
                if (BuildConfig.DEBUG) devMsg
                else "Google sign-in is temporarily unavailable. Please try again."
            )
        } catch (e: GoogleIdTokenParsingException) {
            GoogleSignInResult.Error(
                if (BuildConfig.DEBUG) "Failed to parse Google ID token: ${e.message}"
                else "Google sign-in failed. Please try again."
            )
        } catch (e: GetCredentialException) {
            val errorMsg = e.message ?: ""
            if (errorMsg.contains("cancel", ignoreCase = true) || e.type.contains("USER_CANCELED", ignoreCase = true)) {
                GoogleSignInResult.Cancelled
            } else {
                GoogleSignInResult.Error(
                    if (BuildConfig.DEBUG) "Google sign-in error: $errorMsg"
                    else "Google sign-in is temporarily unavailable. Please try again."
                )
            }
        } catch (e: Exception) {
            GoogleSignInResult.Error(
                if (BuildConfig.DEBUG) "Authentication error: ${e.message}"
                else "Google sign-in is temporarily unavailable. Please try again."
            )
        }
    }
}
