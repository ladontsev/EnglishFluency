package com.englishfluency.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.englishfluency.ai.audio.VoiceSessionController
import com.englishfluency.ai.data.BbcCatalogItem
import com.englishfluency.ai.data.BbcLessonParser
import com.englishfluency.ai.data.ChatMessage
import com.englishfluency.ai.data.FluencyRepository
import com.englishfluency.ai.data.GeminiModelInfo
import com.englishfluency.ai.data.GeminiTutorService
import com.englishfluency.ai.data.GitHubReleaseInfo
import com.englishfluency.ai.data.GitHubUpdater
import com.englishfluency.ai.data.Lesson
import com.englishfluency.ai.data.MessageSender
import com.englishfluency.ai.data.RoleplayScenario
import com.englishfluency.ai.data.SrsChallenge
import com.englishfluency.ai.data.SrsEvaluationResult
import com.englishfluency.ai.data.SrsExerciseMode
import com.englishfluency.ai.data.SrsFilterCategory
import com.englishfluency.ai.data.VocabularyItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class FluencyViewModel(application: Application) : AndroidViewModel(application) {

    val repository = FluencyRepository(application)
    private val geminiService = GeminiTutorService()
    val voiceController = VoiceSessionController(application)

    val lessons: StateFlow<List<Lesson>> = repository.lessons
    val apiKey: StateFlow<String> = repository.apiKey
    val geminiModelId: StateFlow<String> = repository.geminiModelId
    val availableModels: StateFlow<List<GeminiModelInfo>> = repository.availableModels

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    private val _modelsStatusMessage = MutableStateFlow<String?>(null)
    val modelsStatusMessage: StateFlow<String?> = _modelsStatusMessage.asStateFlow()

    private val _selectedLessonId = MutableStateFlow(
        repository.lessons.value.firstOrNull()?.id.orEmpty()
    )
    val selectedLessonId: StateFlow<String> = _selectedLessonId.asStateFlow()

    // Live BBC 6 Minute English Catalog (Stage 2 v1.2)
    private val _bbcCatalogItems = MutableStateFlow<List<BbcCatalogItem>>(emptyList())
    val bbcCatalogItems: StateFlow<List<BbcCatalogItem>> = _bbcCatalogItems.asStateFlow()

    private val _isLoadingCatalog = MutableStateFlow(false)
    val isLoadingCatalog: StateFlow<Boolean> = _isLoadingCatalog.asStateFlow()

    private val _catalogError = MutableStateFlow<String?>(null)
    val catalogError: StateFlow<String?> = _catalogError.asStateFlow()

    private val _isLoadingUrl = MutableStateFlow(false)
    val isLoadingUrl: StateFlow<Boolean> = _isLoadingUrl.asStateFlow()

    private val _importingUrl = MutableStateFlow<String?>(null)
    val importingUrl: StateFlow<String?> = _importingUrl.asStateFlow()

    private val _urlImportStatus = MutableStateFlow<String?>(null)
    val urlImportStatus: StateFlow<String?> = _urlImportStatus.asStateFlow()

    private val _isProcessingTurn = MutableStateFlow(false)
    val isProcessingTurn: StateFlow<Boolean> = _isProcessingTurn.asStateFlow()

    private val _conversationError = MutableStateFlow<String?>(null)
    val conversationError: StateFlow<String?> = _conversationError.asStateFlow()

    // Stage 3 (v2.0): Interactive Roleplay Simulation
    val roleplaysByLesson: StateFlow<Map<String, RoleplayScenario>> = repository.roleplaysByLesson

    private val _isGeneratingRoleplay = MutableStateFlow(false)
    val isGeneratingRoleplay: StateFlow<Boolean> = _isGeneratingRoleplay.asStateFlow()

    private val _roleplayError = MutableStateFlow<String?>(null)
    val roleplayError: StateFlow<String?> = _roleplayError.asStateFlow()

    // SRS Review State (100% Dynamic via Gemini + Stage 2 Personal Mistakes & 5-Min Sprint)
    private val _selectedSrsMode = MutableStateFlow(SrsExerciseMode.RU_TO_EN)
    val selectedSrsMode: StateFlow<SrsExerciseMode> = _selectedSrsMode.asStateFlow()

    private val _selectedSrsFilter = MutableStateFlow(SrsFilterCategory.ALL)
    val selectedSrsFilter: StateFlow<SrsFilterCategory> = _selectedSrsFilter.asStateFlow()

    private val _currentSrsVocabId = MutableStateFlow<String?>(null)
    val currentSrsVocabId: StateFlow<String?> = _currentSrsVocabId.asStateFlow()

    private val _currentSrsChallenge = MutableStateFlow<SrsChallenge?>(null)
    val currentSrsChallenge: StateFlow<SrsChallenge?> = _currentSrsChallenge.asStateFlow()

    private val _isGeneratingChallenge = MutableStateFlow(false)
    val isGeneratingChallenge: StateFlow<Boolean> = _isGeneratingChallenge.asStateFlow()

    private val _srsError = MutableStateFlow<String?>(null)
    val srsError: StateFlow<String?> = _srsError.asStateFlow()

    private val _lastSrsResult = MutableStateFlow<SrsEvaluationResult?>(null)
    val lastSrsResult: StateFlow<SrsEvaluationResult?> = _lastSrsResult.asStateFlow()

    private val _isEvaluatingSrs = MutableStateFlow(false)
    val isEvaluatingSrs: StateFlow<Boolean> = _isEvaluatingSrs.asStateFlow()

    // 5-Minute Voice Sprint State (Stage 2 v1.2)
    private val _sprintQueueIds = MutableStateFlow<List<String>>(emptyList())
    val sprintQueueIds: StateFlow<List<String>> = _sprintQueueIds.asStateFlow()

    private val _sprintCurrentIndex = MutableStateFlow(0)
    val sprintCurrentIndex: StateFlow<Int> = _sprintCurrentIndex.asStateFlow()

    private val _sprintPassedCount = MutableStateFlow(0)
    val sprintPassedCount: StateFlow<Int> = _sprintPassedCount.asStateFlow()

    private val _isSprintFinished = MutableStateFlow(false)
    val isSprintFinished: StateFlow<Boolean> = _isSprintFinished.asStateFlow()

    // GitHub Self-Update State
    val currentVersionName: String = GitHubUpdater.getCurrentAppVersion(application)

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _latestRelease = MutableStateFlow<GitHubReleaseInfo?>(null)
    val latestRelease: StateFlow<GitHubReleaseInfo?> = _latestRelease.asStateFlow()

    private val _updateStatusMessage = MutableStateFlow<String?>(null)
    val updateStatusMessage: StateFlow<String?> = _updateStatusMessage.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Int?>(null)
    val downloadProgress: StateFlow<Int?> = _downloadProgress.asStateFlow()

    init {
        refreshBbcCatalog()
        if (apiKey.value.isNotBlank()) {
            refreshAvailableModels()
        }
    }

    fun saveApiKeyAndRefreshModels(newKey: String) {
        repository.updateApiKey(newKey)
        if (newKey.isNotBlank()) {
            refreshAvailableModels()
        }
    }

    fun refreshAvailableModels() {
        val key = apiKey.value.trim()
        if (key.isBlank()) {
            _modelsStatusMessage.value = "Сначала укажите Gemini API Key, чтобы загрузить список доступных моделей из API."
            return
        }
        viewModelScope.launch {
            _isLoadingModels.value = true
            _modelsStatusMessage.value = "Запрос списка актуальных моделей из Google Gemini API..."
            runCatching {
                geminiService.fetchAvailableModels(key)
            }.fold(
                onSuccess = { models ->
                    repository.updateAvailableModels(models)
                    _isLoadingModels.value = false
                    _modelsStatusMessage.value = "✅ Загружено доступных моделей из API: ${models.size}"
                },
                onFailure = { err ->
                    _isLoadingModels.value = false
                    _modelsStatusMessage.value = "Ошибка загрузки моделей: ${err.localizedMessage}"
                }
            )
        }
    }

    fun refreshBbcCatalog() {
        viewModelScope.launch {
            _isLoadingCatalog.value = true
            _catalogError.value = null
            val result = BbcLessonParser.fetchLatestEpisodeCatalog(limit = 35)
            result.fold(
                onSuccess = { items ->
                    _bbcCatalogItems.value = items
                    _isLoadingCatalog.value = false
                },
                onFailure = { err ->
                    _catalogError.value = "Не удалось загрузить каталог BBC: ${err.localizedMessage}"
                    _isLoadingCatalog.value = false
                }
            )
        }
    }

    fun checkForGitHubUpdates() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateStatusMessage.value = "Проверка последнего релиза на GitHub (ladontsev/EnglishFluency)..."
            val result = GitHubUpdater.checkLatestRelease()
            result.fold(
                onSuccess = { release ->
                    _latestRelease.value = release
                    val newer = GitHubUpdater.isNewerVersion(currentVersionName, release.tagName)
                    _updateStatusMessage.value = if (newer) {
                        "🎉 Доступна новая версия ${release.tagName}: ${release.releaseName}"
                    } else {
                        "✅ У вас установлена актуальная версия ($currentVersionName). Последний релиз на GitHub: ${release.tagName}"
                    }
                    _isCheckingUpdate.value = false
                },
                onFailure = { err ->
                    _updateStatusMessage.value = "Ошибка при проверке обновлений: ${err.localizedMessage}"
                    _isCheckingUpdate.value = false
                }
            )
        }
    }

    fun downloadAndInstallGitHubUpdate(release: GitHubReleaseInfo) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _downloadProgress.value = 0
            _updateStatusMessage.value = "Скачивание APK ${release.tagName}..."
            val result = GitHubUpdater.downloadApkToCache(
                context = app,
                downloadUrl = release.apkDownloadUrl,
                onProgress = { pct -> _downloadProgress.value = pct }
            )
            result.fold(
                onSuccess = { apkFile ->
                    _downloadProgress.value = null
                    _updateStatusMessage.value = "APK скачан (${apkFile.length() / 1024} КБ). Запуск установщика Android..."
                    GitHubUpdater.promptInstallApk(app, apkFile)
                },
                onFailure = { err ->
                    _downloadProgress.value = null
                    _updateStatusMessage.value = "Ошибка скачивания APK: ${err.localizedMessage}"
                }
            )
        }
    }

    fun selectLesson(lessonId: String) {
        _selectedLessonId.value = lessonId
        _conversationError.value = null
    }

    fun deleteLesson(lessonId: String) {
        repository.deleteLesson(lessonId)
        if (_selectedLessonId.value == lessonId) {
            _selectedLessonId.value = repository.lessons.value.firstOrNull()?.id.orEmpty()
        }
    }

    fun getSelectedLesson(): Lesson? {
        return lessons.value.find { it.id == _selectedLessonId.value } ?: lessons.value.firstOrNull()
    }

    fun importBbcLessonFromUrl(url: String, onSuccess: (Lesson) -> Unit) {
        if (url.isBlank()) return
        if (apiKey.value.isBlank()) {
            _urlImportStatus.value = "⚠️ Сначала укажите ваш Gemini API Key во вкладке «Настройки»!"
            return
        }
        val cleanUrl = url.trim()
        viewModelScope.launch {
            _isLoadingUrl.value = true
            _importingUrl.value = cleanUrl
            _urlImportStatus.value = "Загрузка выпуска BBC и ИИ-извлечение словаря..."
            val scrapedResult = BbcLessonParser.fetchRawPageContent(cleanUrl)
            scrapedResult.fold(
                onSuccess = { scraped ->
                    runCatching {
                        val lesson = geminiService.extractLessonFromScrapedPage(
                            apiKey = apiKey.value,
                            modelId = geminiModelId.value,
                            scraped = scraped
                        )
                        val opening = runCatching {
                            geminiService.generateOpeningQuestion(
                                apiKey = apiKey.value,
                                modelId = geminiModelId.value,
                                lesson = lesson
                            )
                        }.getOrNull()

                        repository.addOrReplaceLesson(lesson, opening)
                        _selectedLessonId.value = lesson.id
                        _urlImportStatus.value = "✅ Урок «${lesson.title}» (${lesson.vocabulary.size} выражений) загружен!"
                        _isLoadingUrl.value = false
                        _importingUrl.value = null
                        onSuccess(lesson)
                    }.onFailure { aiErr ->
                        _urlImportStatus.value = "Ошибка Gemini API: ${aiErr.localizedMessage}"
                        _isLoadingUrl.value = false
                        _importingUrl.value = null
                    }
                },
                onFailure = { err ->
                    _urlImportStatus.value = "Ошибка загрузки страницы: ${err.localizedMessage}"
                    _isLoadingUrl.value = false
                    _importingUrl.value = null
                }
            )
        }
    }

    fun clearUrlStatus() {
        _urlImportStatus.value = null
    }

    fun startOrGenerateRoleplay() {
        val lesson = getSelectedLesson() ?: return
        if (apiKey.value.isBlank()) {
            _roleplayError.value = "Укажите Gemini API Key в Настройках для запуска Roleplay."
            return
        }
        viewModelScope.launch {
            _isGeneratingRoleplay.value = true
            _roleplayError.value = null
            runCatching {
                geminiService.generateRoleplayScenario(
                    apiKey = apiKey.value,
                    modelId = geminiModelId.value,
                    lesson = lesson
                )
            }.fold(
                onSuccess = { scenario ->
                    repository.setRoleplayForLesson(lesson.id, scenario)
                    // Reset conversation with the character's initial greeting
                    val characterOpening = "${scenario.initialAiLine}\n\n[Ролевая игра: ${scenario.title}. Ваша роль: ${scenario.userRole}]"
                    repository.resetConversation(lesson.id, characterOpening)
                    _isGeneratingRoleplay.value = false
                    voiceController.speakTutorText(scenario.initialAiLine)
                },
                onFailure = { err ->
                    _isGeneratingRoleplay.value = false
                    _roleplayError.value = "Ошибка генерации Roleplay: ${err.localizedMessage}"
                }
            )
        }
    }

    fun exitRoleplay() {
        val lesson = getSelectedLesson() ?: return
        repository.setRoleplayForLesson(lesson.id, null)
        resetCurrentConversation()
    }

    fun submitUserTurn(spokenOrTypedText: String, audioFile: File? = null) {
        val lesson = getSelectedLesson() ?: return
        val history = repository.getConversationForLesson(lesson.id)
        val cleanText = spokenOrTypedText.trim()
        if (cleanText.isBlank() && (audioFile == null || !audioFile.exists())) return

        val activeRoleplay = repository.getRoleplayForLesson(lesson.id)

        viewModelScope.launch {
            _isProcessingTurn.value = true
            _conversationError.value = null
            runCatching {
                geminiService.evaluateConversationTurn(
                    apiKey = apiKey.value,
                    modelId = geminiModelId.value,
                    lesson = lesson,
                    history = history,
                    userSpeechText = cleanText,
                    audioFile = audioFile,
                    roleplayScenario = activeRoleplay
                )
            }.fold(
                onSuccess = { response ->
                    val userMsg = ChatMessage(
                        id = "usr_${UUID.randomUUID()}",
                        sender = MessageSender.USER,
                        text = response.analysis.userTranscript.ifBlank { cleanText },
                        timestampMs = System.currentTimeMillis(),
                        analysis = response.analysis,
                        audioFilePath = audioFile?.absolutePath
                    )

                    val tutorMsg = ChatMessage(
                        id = "tut_${UUID.randomUUID()}",
                        sender = MessageSender.TUTOR,
                        text = response.tutorReplyEn,
                        timestampMs = System.currentTimeMillis() + 1
                    )

                    repository.appendMessages(lesson.id, userMsg, tutorMsg)
                    repository.markVocabUsedInConversation(
                        lessonId = lesson.id,
                        expressionsUsed = response.analysis.targetVocabUsed,
                        pronunciationScore = response.analysis.pronunciationScore
                    )

                    // Automatically capture personal mistake into SRS if Gemini identified one
                    val mistake = response.analysis.personalMistakeToLearn
                    if (mistake != null && mistake.expression.isNotBlank()) {
                        repository.addPersonalMistakeToLesson(lesson.id, mistake)
                    }

                    _isProcessingTurn.value = false
                    voiceController.speakTutorText(response.tutorReplyEn)
                },
                onFailure = { err ->
                    _isProcessingTurn.value = false
                    _conversationError.value = "Ошибка ИИ: ${err.localizedMessage}"
                }
            )
        }
    }

    fun speakText(text: String) {
        voiceController.speakTutorText(text)
    }

    fun resetCurrentConversation() {
        val lesson = getSelectedLesson() ?: return
        viewModelScope.launch {
            val opening = runCatching {
                if (apiKey.value.isNotBlank()) {
                    geminiService.generateOpeningQuestion(apiKey.value, geminiModelId.value, lesson)
                } else null
            }.getOrNull()
            repository.resetConversation(lesson.id, opening)
        }
    }

    // Dynamic SRS Methods
    fun ensureSrsChallengeLoaded(vocab: VocabularyItem, forceNew: Boolean = false) {
        val mode = _selectedSrsMode.value
        if (!forceNew) {
            val cached = repository.getCachedSrsChallenge(vocab.id, mode)
            if (cached != null) {
                _currentSrsChallenge.value = cached
                _srsError.value = null
                return
            }
        }
        if (apiKey.value.isBlank()) {
            _srsError.value = "Укажите ваш Gemini API Key во вкладке «Настройки» для генерации заданий."
            return
        }

        val lessonTitle = lessons.value.find { it.id == vocab.lessonId }?.title.orEmpty()
        viewModelScope.launch {
            _isGeneratingChallenge.value = true
            _srsError.value = null
            runCatching {
                geminiService.generateSrsChallenge(
                    apiKey = apiKey.value,
                    modelId = geminiModelId.value,
                    vocab = vocab,
                    mode = mode,
                    lessonTitle = lessonTitle
                )
            }.fold(
                onSuccess = { challenge ->
                    repository.cacheSrsChallenge(vocab.id, mode, challenge)
                    _currentSrsChallenge.value = challenge
                    _isGeneratingChallenge.value = false
                },
                onFailure = { err ->
                    _srsError.value = "Ошибка генерации задания: ${err.localizedMessage}"
                    _isGeneratingChallenge.value = false
                }
            )
        }
    }

    fun setSrsMode(mode: SrsExerciseMode) {
        _selectedSrsMode.value = mode
        _lastSrsResult.value = null
        val all = repository.allVocabularyItems()
        val current = all.find { it.id == _currentSrsVocabId.value } ?: all.firstOrNull()
        if (current != null) {
            ensureSrsChallengeLoaded(current, forceNew = false)
        }
    }

    fun setSrsFilter(filter: SrsFilterCategory) {
        _selectedSrsFilter.value = filter
        _lastSrsResult.value = null
    }

    fun selectSrsVocab(vocabId: String) {
        _currentSrsVocabId.value = vocabId
        _lastSrsResult.value = null
        val vocab = repository.allVocabularyItems().find { it.id == vocabId }
        if (vocab != null) {
            ensureSrsChallengeLoaded(vocab, forceNew = false)
        }
    }

    fun startFiveMinuteSprint() {
        val now = System.currentTimeMillis()
        val all = repository.allVocabularyItems()
        if (all.isEmpty()) return

        // Prioritize personal mistakes & due items, then lowest mastery
        val prioritized = all.sortedWith(
            compareByDescending<VocabularyItem> { it.isPersonalMistake }
                .thenByDescending { it.nextReviewEpochMs <= now }
                .thenBy { it.masteryLevel }
        ).take(5)

        val queue = prioritized.map { it.id }
        _sprintQueueIds.value = queue
        _sprintCurrentIndex.value = 0
        _sprintPassedCount.value = 0
        _isSprintFinished.value = false
        _lastSrsResult.value = null

        val firstItem = prioritized.first()
        _currentSrsVocabId.value = firstItem.id
        ensureSrsChallengeLoaded(firstItem, forceNew = true)
    }

    fun stopSprint() {
        _sprintQueueIds.value = emptyList()
        _sprintCurrentIndex.value = 0
        _sprintPassedCount.value = 0
        _isSprintFinished.value = false
    }

    fun nextSrsVocab() {
        val sprintQueue = _sprintQueueIds.value
        val all = repository.allVocabularyItems()
        if (all.isEmpty()) return

        if (sprintQueue.isNotEmpty() && !_isSprintFinished.value) {
            val nextSprintIdx = _sprintCurrentIndex.value + 1
            if (nextSprintIdx >= sprintQueue.size) {
                _isSprintFinished.value = true
                _lastSrsResult.value = null
                return
            } else {
                _sprintCurrentIndex.value = nextSprintIdx
                val nextId = sprintQueue[nextSprintIdx]
                val nextItem = all.find { it.id == nextId } ?: all.first()
                _currentSrsVocabId.value = nextItem.id
                _lastSrsResult.value = null
                ensureSrsChallengeLoaded(nextItem, forceNew = true)
                return
            }
        }

        val currentIdx = all.indexOfFirst { it.id == _currentSrsVocabId.value }
        val nextIdx = if (currentIdx < 0 || currentIdx + 1 >= all.size) 0 else currentIdx + 1
        val nextItem = all[nextIdx]
        _currentSrsVocabId.value = nextItem.id
        _lastSrsResult.value = null
        ensureSrsChallengeLoaded(nextItem, forceNew = true)
    }

    fun evaluateSrsResponse(
        vocab: VocabularyItem,
        spokenOrTypedText: String,
        audioFile: File? = null
    ) {
        val mode = _selectedSrsMode.value
        val prompt = _currentSrsChallenge.value?.promptText ?: vocab.exampleSentence
        viewModelScope.launch {
            _isEvaluatingSrs.value = true
            _srsError.value = null
            runCatching {
                geminiService.evaluateSrsAttempt(
                    apiKey = apiKey.value,
                    modelId = geminiModelId.value,
                    vocab = vocab,
                    mode = mode,
                    challengePrompt = prompt,
                    userSpokenText = spokenOrTypedText,
                    audioFile = audioFile
                )
            }.fold(
                onSuccess = { result ->
                    repository.recordSrsReviewResult(
                        vocabId = vocab.id,
                        passed = result.passed,
                        score = result.score
                    )
                    if (_sprintQueueIds.value.isNotEmpty() && result.passed) {
                        _sprintPassedCount.value = _sprintPassedCount.value + 1
                    }
                    _lastSrsResult.value = result.copy(audioFilePath = audioFile?.absolutePath)
                    _isEvaluatingSrs.value = false
                },
                onFailure = { err ->
                    _srsError.value = "Ошибка проверки ИИ: ${err.localizedMessage}"
                    _isEvaluatingSrs.value = false
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
    }
}
