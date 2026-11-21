package com.example.ui.state

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.IntentActionType
import com.example.data.ai.JarvisAiRepository
import com.example.data.local.ChatMessageEntity
import com.example.data.local.JarvisDatabase
import com.example.data.local.MemoryFactEntity
import com.example.data.preferences.JarvisLanguageMode
import com.example.data.preferences.JarvisSettings
import com.example.data.preferences.JarvisSettingsStore
import com.example.data.preferences.VoiceGender
import com.example.data.remote.WebSearchRepository
import com.example.data.remote.WebSource
import com.example.system.DeviceActionManager
import com.example.system.FileWorkspaceManager
import com.example.system.InstalledAppShortcut
import com.example.system.JarvisAccessibilityService
import com.example.system.JarvisGlobalAction
import com.example.system.PermissionSnapshot
import com.example.system.SystemTelemetry
import com.example.voice.JarvisVoiceManager
import com.example.voice.JarvisWakeWordService
import com.example.voice.VoiceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

sealed interface AiResponseState {
    data object Idle : AiResponseState
    data object Thinking : AiResponseState
    data class Streaming(val partialText: String) : AiResponseState
    data class Error(val message: String) : AiResponseState
}

sealed interface PendingConfirmation {
    data class ConfirmCall(
        val recipientLabel: String,
        val phoneNumberOrQuery: String
    ) : PendingConfirmation

    data class ConfirmSms(
        val recipientLabel: String,
        val phoneNumberOrQuery: String,
        val messageBody: String
    ) : PendingConfirmation
}

class JarvisAssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val database = JarvisDatabase.getInstance(application)
    private val chatDao = database.chatMessageDao()
    private val memoryDao = database.memoryFactDao()
    private val settingsStore = JarvisSettingsStore(application)
    private val aiRepo = JarvisAiRepository()
    private val webSearchRepo = WebSearchRepository()

    val deviceManager = DeviceActionManager(application)
    val fileWorkspaceManager = FileWorkspaceManager(application)
    val voiceManager = JarvisVoiceManager(application)

    val settings: StateFlow<JarvisSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, JarvisSettings())

    val messages: StateFlow<List<ChatMessageEntity>> = chatDao.observeRecentMessages(60)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memoryFacts: StateFlow<List<MemoryFactEntity>> = memoryDao.observeAllFacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceState: StateFlow<VoiceState> = voiceManager.voiceState
    val rmsDb: StateFlow<Float> = voiceManager.rmsDb
    val partialTranscript: StateFlow<String> = voiceManager.partialTranscript
    val voiceError: StateFlow<String?> = voiceManager.voiceError
    val isOnline: StateFlow<Boolean> = deviceManager.isOnline
    val accessibilityConnected: StateFlow<Boolean> = JarvisAccessibilityService.isConnected

    private val _aiState = MutableStateFlow<AiResponseState>(AiResponseState.Idle)
    val aiState: StateFlow<AiResponseState> = _aiState.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _telemetry = MutableStateFlow(SystemTelemetry())
    val telemetry: StateFlow<SystemTelemetry> = _telemetry.asStateFlow()

    private val _permissions = MutableStateFlow(PermissionSnapshot())
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppShortcut>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppShortcut>> = _installedApps.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _pendingBuilderPrompt = MutableStateFlow<Pair<String, String>?>(null)
    val pendingBuilderPrompt: StateFlow<Pair<String, String>?> = _pendingBuilderPrompt.asStateFlow()

    init {
        voiceManager.setCallbacks(
            onCommand = { spoken -> submitUserMessage(spoken, speakResponse = true) },
            onWakeWord = { trailing ->
                _statusBanner.value = "Hey JARVIS activated!"
                if (!trailing.isNullOrBlank()) {
                    submitUserMessage(trailing, speakResponse = true)
                }
            }
        )

        viewModelScope.launch {
            settings.collect { current ->
                voiceManager.updateSettings(
                    languageMode = current.languageMode,
                    gender = current.voiceGender,
                    rate = current.speechRate,
                    pitch = current.speechPitch,
                    continuous = current.continuousVoiceMode,
                    wakeWord = current.wakeWordEnabled
                )
            }
        }

        refreshSystemState()
    }

    fun refreshSystemState() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _telemetry.value = deviceManager.readTelemetry()
            _permissions.value = deviceManager.readPermissions()
            _installedApps.value = deviceManager.getInstalledApps()
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun consumePendingBuilderPrompt(): Pair<String, String>? {
        val current = _pendingBuilderPrompt.value
        _pendingBuilderPrompt.value = null
        return current
    }

    fun submitUserMessage(rawInput: String, speakResponse: Boolean = false) {
        val text = rawInput.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            val currentSettings = settings.value
            if (currentSettings.saveConversationHistory) {
                chatDao.insertMessage(
                    ChatMessageEntity(
                        role = "user",
                        content = text,
                        languageTag = currentSettings.languageMode.name
                    )
                )
            }

            _aiState.value = AiResponseState.Thinking

            try {
                val intent = aiRepo.classifyUserIntent(
                    userText = text,
                    allowAutoMemory = currentSettings.autoMemoryFacts && currentSettings.saveConversationHistory
                )

                if (intent.memoryFact.isNotBlank() && currentSettings.autoMemoryFacts) {
                    memoryDao.insertFact(
                        MemoryFactEntity(fact = intent.memoryFact.trim(), category = "Auto-Memory")
                    )
                }

                when (intent.actionType) {
                    IntentActionType.OPEN_APP -> {
                        val target = intent.targetApp.ifBlank { text }
                        val matched = deviceManager.findBestMatchingApp(target)
                        val reply = if (matched != null && deviceManager.launchApp(matched.packageName)) {
                            "Opening ${matched.label} now."
                        } else {
                            "I could not find an installed app matching \"$target\". You can inspect available apps in the Controls tab."
                        }
                        finishJarvisReply(reply, emptyList(), "App Launch: $target", speakResponse)
                    }

                    IntentActionType.MAKE_CALL -> {
                        val target = intent.contactOrNumber.ifBlank { text }
                        _pendingConfirmation.value = PendingConfirmation.ConfirmCall(
                            recipientLabel = target,
                            phoneNumberOrQuery = target
                        )
                        val reply = "कृपया कॉल कन्फर्म करें: $target को कॉल करने के लिए नीचे Confirm दबाएँ।"
                        finishJarvisReply(reply, emptyList(), "Call Confirmation", speakResponse)
                    }

                    IntentActionType.SEND_SMS -> {
                        val target = intent.contactOrNumber.ifBlank { "Recipient" }
                        val body = intent.smsBody.ifBlank { text }
                        _pendingConfirmation.value = PendingConfirmation.ConfirmSms(
                            recipientLabel = target,
                            phoneNumberOrQuery = target,
                            messageBody = body
                        )
                        val reply = "कृपया SMS कन्फर्म करें: \"$target\" को संदेश भेजने के लिए Confirm दबाएँ।"
                        finishJarvisReply(reply, emptyList(), "SMS Confirmation", speakResponse)
                    }

                    IntentActionType.SYSTEM_NAV -> {
                        val action = runCatching { JarvisGlobalAction.valueOf(intent.navAction) }.getOrNull()
                        val reply = if (action != null && JarvisAccessibilityService.isConnected.value) {
                            val ok = JarvisAccessibilityService.performGlobalNavigation(action)
                            if (ok) "Executed system action: ${action.label}." else "Could not perform ${action.label}."
                        } else {
                            "JARVIS Accessibility Service is currently disabled. Enable it in Controls with your permission for hands-free system navigation."
                        }
                        finishJarvisReply(reply, emptyList(), "System Action", speakResponse)
                    }

                    IntentActionType.OPEN_BUILDER -> {
                        val mode = if (intent.builderMode == "WEBSITE_BUNDLE") "WEBSITE_BUNDLE" else "ANDROID_KOTLIN"
                        val prompt = intent.builderPrompt.ifBlank { text }
                        _pendingBuilderPrompt.value = mode to prompt
                        val reply = "App Builder workspace ready for \"$prompt\". Opening Builder tab..."
                        finishJarvisReply(reply, emptyList(), "App Builder", speakResponse)
                    }

                    IntentActionType.WEB_SEARCH -> {
                        val query = intent.searchQuery.ifBlank { text }
                        val preferHindi = currentSettings.languageMode == JarvisLanguageMode.HINDI ||
                            text.any { it in '\u0900'..'\u097F' }
                        val sources = webSearchRepo.searchWeb(query, preferHindi)
                        streamConversationalReply(text, sources, speakResponse)
                    }

                    IntentActionType.CHAT -> {
                        streamConversationalReply(text, emptyList(), speakResponse)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("JarvisVM", "AI timed out", e)
                _aiState.value = AiResponseState.Error("Request timed out after 60 seconds. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("JarvisVM", "Failed to process message", e)
                _aiState.value = AiResponseState.Error(
                    e.localizedMessage ?: "AI connection error. Check network or Firebase App Check."
                )
            }
        }
    }

    private suspend fun streamConversationalReply(
        userText: String,
        sources: List<WebSource>,
        speakResponse: Boolean
    ) {
        val currentSettings = settings.value
        val recentHistory = if (currentSettings.saveConversationHistory) {
            chatDao.getLatestMessages(10).reversed()
        } else {
            emptyList()
        }
        val activeFacts = if (currentSettings.saveConversationHistory) {
            memoryDao.getActiveFacts()
        } else {
            emptyList()
        }

        var latestText = ""
        aiRepo.streamJarvisReply(
            userMessage = userText,
            languageMode = currentSettings.languageMode,
            recentHistory = recentHistory,
            memoryFacts = activeFacts,
            webSources = sources
        ).collect { partial ->
            latestText = partial
            _aiState.value = AiResponseState.Streaming(partial)
        }

        finishJarvisReply(
            replyText = latestText,
            sources = sources,
            actionLabel = if (sources.isNotEmpty()) "Web Search" else "",
            speakResponse = speakResponse
        )
    }

    private suspend fun finishJarvisReply(
        replyText: String,
        sources: List<WebSource>,
        actionLabel: String,
        speakResponse: Boolean
    ) {
        _aiState.value = AiResponseState.Idle
        val sourcesJson = encodeSources(sources)
        if (settings.value.saveConversationHistory) {
            chatDao.insertMessage(
                ChatMessageEntity(
                    role = "jarvis",
                    content = replyText,
                    languageTag = settings.value.languageMode.name,
                    sourcesJson = sourcesJson,
                    actionLabel = actionLabel
                )
            )
        }
        if (speakResponse) {
            voiceManager.speak(replyText)
        }
    }

    fun confirmPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        when (pending) {
            is PendingConfirmation.ConfirmCall -> {
                val ok = deviceManager.executeConfirmedCall(
                    pending.phoneNumberOrQuery,
                    settings.value.preferDirectCall
                )
                _statusBanner.value = if (ok) {
                    "Launching phone call for ${pending.recipientLabel}"
                } else {
                    "Could not open Phone app on this device."
                }
            }
            is PendingConfirmation.ConfirmSms -> {
                val ok = deviceManager.executeConfirmedSms(
                    pending.phoneNumberOrQuery,
                    pending.messageBody
                )
                _statusBanner.value = if (ok) {
                    "Opened SMS composer for ${pending.recipientLabel}"
                } else {
                    "No SMS app available on this device."
                }
            }
        }
    }

    fun dismissPendingAction() {
        _pendingConfirmation.value = null
        _statusBanner.value = "Sensitive action cancelled."
    }

    fun requestCallConfirmation(recipient: String, number: String) {
        _pendingConfirmation.value = PendingConfirmation.ConfirmCall(recipient, number)
    }

    fun requestSmsConfirmation(recipient: String, number: String, body: String) {
        _pendingConfirmation.value = PendingConfirmation.ConfirmSms(recipient, number, body)
    }

    fun analyzeImportedFileInChat(uri: Uri) {
        viewModelScope.launch {
            fileWorkspaceManager.readExternalTextFile(uri).onSuccess { imported ->
                val prompt = "Analyze this file (${imported.displayName}) and explain or find any issues:\n\n${imported.content.take(5000)}"
                submitUserMessage(prompt, speakResponse = false)
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Failed to read file."
            }
        }
    }

    fun addManualMemoryFact(fact: String) {
        val trimmed = fact.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            memoryDao.insertFact(MemoryFactEntity(fact = trimmed, category = "User Note"))
        }
    }

    fun toggleMemoryFact(fact: MemoryFactEntity) {
        viewModelScope.launch {
            memoryDao.updateFact(fact.copy(isEnabled = !fact.isEnabled))
        }
    }

    fun deleteMemoryFact(id: Long) {
        viewModelScope.launch { memoryDao.deleteFactById(id) }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            chatDao.clearAllMessages()
            _statusBanner.value = "Conversation history cleared."
        }
    }

    fun wipeAllMemory() {
        viewModelScope.launch {
            chatDao.clearAllMessages()
            memoryDao.clearAllFacts()
            _statusBanner.value = "All conversation history and memory facts wiped."
        }
    }

    fun updateLanguageMode(mode: JarvisLanguageMode) = viewModelScope.launch {
        settingsStore.setLanguageMode(mode)
    }

    fun updateVoiceGender(gender: VoiceGender) = viewModelScope.launch {
        settingsStore.setVoiceGender(gender)
    }

    fun updateSpeechRate(rate: Float) = viewModelScope.launch {
        settingsStore.setSpeechRate(rate)
    }

    fun updateSpeechPitch(pitch: Float) = viewModelScope.launch {
        settingsStore.setSpeechPitch(pitch)
    }

    fun updateContinuousVoice(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setContinuousVoiceMode(enabled)
    }

    fun updateWakeWordEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setWakeWordEnabled(enabled)
        if (!enabled && settings.value.backgroundWakeService) {
            settingsStore.setBackgroundWakeService(false)
            JarvisWakeWordService.stop(getApplication())
        }
    }

    fun updateBackgroundWakeService(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setBackgroundWakeService(enabled)
        if (enabled) {
            settingsStore.setWakeWordEnabled(true)
            JarvisWakeWordService.start(getApplication())
        } else {
            JarvisWakeWordService.stop(getApplication())
        }
    }

    fun updateSaveHistory(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setSaveConversationHistory(enabled)
    }

    fun updateAutoMemory(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setAutoMemoryFacts(enabled)
    }

    fun updatePreferDirectCall(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setPreferDirectCall(enabled)
    }

    fun updateLowRamOptimized(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setLowRamOptimized(enabled)
    }

    fun testVoiceSample() {
        val sample = when (settings.value.languageMode) {
            JarvisLanguageMode.HINDI -> "नमस्ते, मैं जार्विस हूँ। आपकी सहायता के लिए तैयार हूँ।"
            JarvisLanguageMode.ENGLISH -> "Hello, I am JARVIS AI. Voice systems are calibrated and online."
            JarvisLanguageMode.BILINGUAL -> "नमस्ते! I am JARVIS AI, ready to assist you in Hindi and English."
        }
        voiceManager.speak(sample)
    }

    private fun encodeSources(sources: List<WebSource>): String {
        if (sources.isEmpty()) return ""
        val arr = JSONArray()
        sources.forEach { src ->
            arr.put(
                JSONObject().apply {
                    put("title", src.title)
                    put("url", src.url)
                }
            )
        }
        return arr.toString()
    }

    override fun onCleared() {
        voiceManager.release()
        deviceManager.unregister()
        super.onCleared()
    }
}
