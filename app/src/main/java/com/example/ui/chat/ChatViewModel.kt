package com.example.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.repository.NexaRepository
import com.example.domain.ai.AIModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: NexaRepository
) : ViewModel() {

    val currentUser = repository.currentUser

    private val _selectedModule = MutableStateFlow(AIModule.GENERAL_AI)
    val selectedModule: StateFlow<AIModule> = _selectedModule.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _lastFailedPrompt = MutableStateFlow<String?>(null)
    val lastFailedPrompt: StateFlow<String?> = _lastFailedPrompt.asStateFlow()

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user == null) {
                    _currentConversationId.value = null
                    _errorMessage.value = null
                    _lastFailedPrompt.value = null
                }
            }
        }
        viewModelScope.launch {
            repository.activeConversationId.collect { activeId ->
                if (activeId != _currentConversationId.value) {
                    _currentConversationId.value = activeId
                }
            }
        }
    }

    val conversations: StateFlow<List<ConversationEntity>> = currentUser.flatMapLatest { user ->
        if (user != null) {
            repository.getConversations(user.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages: StateFlow<List<MessageEntity>> = _currentConversationId.flatMapLatest { convId ->
        if (convId != null) {
            repository.getMessages(convId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectModule(module: AIModule) {
        _selectedModule.value = module
    }

    fun selectConversation(conversationId: String, moduleName: String? = null) {
        _currentConversationId.value = conversationId
        repository.setActiveConversationId(conversationId)
        _errorMessage.value = null
        _lastFailedPrompt.value = null
        if (moduleName != null) {
            _selectedModule.value = AIModule.fromId(moduleName)
        }
    }

    fun startNewConversation(module: AIModule = _selectedModule.value, initialPrompt: String? = null) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            _selectedModule.value = module
            _errorMessage.value = null
            _lastFailedPrompt.value = null
            val newId = repository.createConversation(
                userId = user.id,
                title = "New ${module.title}",
                module = module.title
            )
            _currentConversationId.value = newId
            repository.setActiveConversationId(newId)

            if (!initialPrompt.isNullOrBlank()) {
                sendMessage(initialPrompt)
            }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _isSending.value) return

        val user = currentUser.value ?: return
        viewModelScope.launch {
            _isSending.value = true
            _errorMessage.value = null
            _lastFailedPrompt.value = null

            // If no active conversation, create one first
            var activeConvId = _currentConversationId.value
            if (activeConvId == null) {
                activeConvId = repository.createConversation(
                    userId = user.id,
                    title = trimmed.take(24),
                    module = _selectedModule.value.title
                )
                _currentConversationId.value = activeConvId
                repository.setActiveConversationId(activeConvId)
            }

            val result = repository.sendMessage(
                conversationId = activeConvId,
                content = trimmed,
                module = _selectedModule.value
            )

            result.onFailure {
                _errorMessage.value = it.message ?: "AI is temporarily unavailable. Please try again."
                _lastFailedPrompt.value = trimmed
            }
            _isSending.value = false
        }
    }

    fun retryLastMessage() {
        val prompt = _lastFailedPrompt.value ?: return
        _errorMessage.value = null
        _lastFailedPrompt.value = null
        sendMessage(prompt)
    }

    fun clearError() {
        _errorMessage.value = null
        _lastFailedPrompt.value = null
    }

    fun deleteCurrentConversation() {
        val user = currentUser.value ?: return
        val convId = _currentConversationId.value ?: return
        viewModelScope.launch {
            repository.deleteConversation(convId, user.id)
            _currentConversationId.value = null
            _errorMessage.value = null
            _lastFailedPrompt.value = null
        }
    }
}
