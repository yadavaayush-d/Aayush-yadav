package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NexaDatabase
import com.example.data.repository.NexaRepository
import com.example.domain.auth.GoogleAccountInfo
import com.example.domain.auth.GoogleAuthService
import com.example.domain.auth.GoogleSignInResult
import com.example.ui.auth.AuthUiState
import com.example.ui.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoogleAuthIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: NexaDatabase
    private lateinit var repository: NexaRepository
    private lateinit var mockGoogleAuthService: MockGoogleAuthService

    class MockGoogleAuthService : GoogleAuthService {
        var configured = true
        var resultToReturn: GoogleSignInResult = GoogleSignInResult.Success(
            GoogleAccountInfo(
                id = "google_user_123",
                email = "sarah.connor@gmail.com",
                displayName = "Sarah Connor",
                photoUrl = "https://lh3.googleusercontent.com/a/photo_123",
                idToken = "mock_jwt_id_token"
            )
        )

        override fun isConfigured(): Boolean = configured

        override suspend fun signInWithGoogle(context: Context): GoogleSignInResult {
            return if (!configured) {
                GoogleSignInResult.NotConfigured(
                    "Google OAuth is not configured. Please set GOOGLE_CLIENT_ID in the AI Studio Secrets panel."
                )
            } else {
                resultToReturn
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NexaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        mockGoogleAuthService = MockGoogleAuthService()
        repository = NexaRepository(
            database = database,
            googleAuthService = mockGoogleAuthService,
            context = context
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testGoogleSignUp_createsProfileAndWelcomeConversationAndSession() = runBlocking {
        val result = repository.authenticateWithGoogle(
            email = "alex.creator@gmail.com",
            displayName = "Alex Rivera",
            photoUrl = "https://lh3.googleusercontent.com/avatar/alex.jpg"
        )

        assertTrue("Authentication should succeed", result.isSuccess)
        val user = result.getOrNull()
        assertNotNull(user)
        assertEquals("alex.creator@gmail.com", user?.email)
        assertEquals("Alex Rivera", user?.name)
        assertEquals("https://lh3.googleusercontent.com/avatar/alex.jpg", user?.profileImage)
        assertEquals("Free Tier", user?.subscriptionPlan)

        // Verify in Room database
        val dbUser = database.userDao().getUserByEmail("alex.creator@gmail.com")
        assertNotNull("User must be stored in Room database", dbUser)
        assertEquals(user?.id, dbUser?.id)

        // Verify welcome conversation created
        val conversations = database.conversationDao().getConversationsByUser(dbUser!!.id).first()
        assertTrue("Welcome conversation must be created", conversations.isNotEmpty())

        // Verify current user state in repository
        assertEquals(dbUser.id, repository.currentUser.value?.id)
    }

    @Test
    fun testGoogleLogin_existingUser_doesNotCreateDuplicate() = runBlocking {
        // First, existing user signed up with email/password
        repository.signUp(
            name = "Sarah Connor",
            email = "sarah.connor@gmail.com",
            password = "securePassword123",
            preferredLanguage = "English",
            userType = "Professional"
        )

        val usersBefore = database.userDao().getAllUsers().first()
        assertEquals(1, usersBefore.size)
        val originalUserId = usersBefore[0].id

        // Now user signs in with Google using same email
        val result = repository.authenticateWithGoogle(
            email = "sarah.connor@gmail.com",
            displayName = "Sarah Connor",
            photoUrl = "https://lh3.googleusercontent.com/a/photo_123"
        )

        assertTrue("Google auth should succeed for existing user", result.isSuccess)
        val usersAfter = database.userDao().getAllUsers().first()
        assertEquals("No duplicate account should be created", 1, usersAfter.size)
        assertEquals("Existing user id must be preserved", originalUserId, usersAfter[0].id)
        assertEquals("Profile picture should be updated from Google", "https://lh3.googleusercontent.com/a/photo_123", usersAfter[0].profileImage)
        assertEquals("Current user should be the existing user", originalUserId, repository.currentUser.value?.id)
    }

    @Test
    fun testGoogleAuthViewModel_successFlow() = runBlocking {
        val viewModel = AuthViewModel(repository)
        viewModel.continueWithGoogle(context)

        val state = withTimeout(5000) {
            viewModel.uiState.filter { it is AuthUiState.Success || it is AuthUiState.Error }.first()
        }
        assertTrue("UI state should be Success", state is AuthUiState.Success)
        val user = (state as AuthUiState.Success).user
        assertEquals("sarah.connor@gmail.com", user.email)
        assertFalse("isGoogleLoading should be false after completion", viewModel.isGoogleLoading.value)
    }

    @Test
    fun testGoogleAuthViewModel_cancellationReturnsSafely() = runBlocking {
        mockGoogleAuthService.resultToReturn = GoogleSignInResult.Cancelled
        val viewModel = AuthViewModel(repository)

        viewModel.continueWithGoogle(context)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Cancellation should return safely to Idle without an error dialog
        assertEquals("UI state should be Idle on cancellation", AuthUiState.Idle, viewModel.uiState.value)
        assertFalse("isGoogleLoading should be false on cancellation", viewModel.isGoogleLoading.value)
    }

    @Test
    fun testGoogleAuthViewModel_notConfiguredShowsError() = runBlocking {
        mockGoogleAuthService.configured = false
        val viewModel = AuthViewModel(repository)

        viewModel.continueWithGoogle(context)

        val state = withTimeout(5000) {
            viewModel.uiState.filter { it is AuthUiState.Error }.first()
        }
        assertTrue("UI state should be Error when not configured", state is AuthUiState.Error)
        val message = (state as AuthUiState.Error).message
        assertTrue("Error message should mention configuration", message.contains("configured", ignoreCase = true))
        assertFalse("isGoogleLoading should be false after error", viewModel.isGoogleLoading.value)
    }

    @Test
    fun testSessionPersistence_restoresOnRepositoryRecreation() = runBlocking {
        // Authenticate with Google
        val authResult = repository.authenticateWithGoogle(
            email = "persisted.user@gmail.com",
            displayName = "Persisted User",
            photoUrl = "https://example.com/avatar.png"
        )
        val userId = authResult.getOrNull()!!.id

        // Create a brand new repository simulating app relaunch/refresh
        val newRepository = NexaRepository(
            database = database,
            googleAuthService = mockGoogleAuthService,
            context = context
        )

        // Verify session was restored automatically via SharedPreferences
        assertEquals(userId, newRepository.currentUser.value?.id)
        assertEquals("persisted.user@gmail.com", newRepository.currentUser.value?.email)
    }

    @Test
    fun testLogout_clearsSession() = runBlocking {
        repository.authenticateWithGoogle(
            email = "logout.test@gmail.com",
            displayName = "Logout Test",
            photoUrl = ""
        )
        assertNotNull(repository.currentUser.value)

        repository.logout()
        assertNull(repository.currentUser.value)

        // Simulating app restart after logout
        val newRepository = NexaRepository(
            database = database,
            googleAuthService = mockGoogleAuthService,
            context = context
        )
        assertNull("Session must not restore after logout", newRepository.currentUser.value)
    }
}
