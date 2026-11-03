package com.englishfluency.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.englishfluency.ai.audio.VoiceSessionController
import com.englishfluency.ai.data.BbcLessonParser
import com.englishfluency.ai.data.ChatMessage
import com.englishfluency.ai.data.FluencyRepository
import com.englishfluency.ai.data.GeminiTutorService
import com.englishfluency.ai.data.GitHubReleaseInfo
import com.englishfluency.ai.data.GitHubUpdater
import com.englishfluency.ai.data.Lesson
import com.englishfluency.ai.data.MessageSender
import com.englishfluency.ai.data.SrsChallenge
import com.englishfluency.ai.data.SrsEvaluationResult
import com.englishfluency.ai.data.SrsExerciseMode
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

    private val _selectedLessonId = MutableStateFlow(
        repository.lessons.value.firstOrNull()?.id.orEmpty()
    )
    val selectedLessonId: StateFlow<String> = _selectedLessonId.asStateFlow()

    private val _isLoadingUrl = MutableStateFlow(false)
    val isLoadingUrl: StateFlow<Boolean> = _isLoadingUrl.asStateFlow()

    private val _urlImportStatus = MutableStateFlow<String?>(null)
    val urlImportStatus: StateFlow<String?> = _urlImportStatus.asStateFlow()

    private val _isProcessingTurn = MutableStateFlow(false)
    val isProcessingTurn: StateFlow<Boolean> = _isProcessingTurn.asStateFlow()

    private val _conversationError = MutableStateFlow<String?>(null)
    val conversationError: StateFlow<String?> = _conversationError.asStateFlow()

    // SRS Review State (100% Dynamic via Gemini)
    private val _selectedSrsMode = MutableStateFlow(SrsExerciseMode.RU_TO_EN)
    val selectedSrsMode: StateFlow<SrsExerciseMode> = _selectedSrsMode.asStateFlow()

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
        viewModelScope.launch {
            _isLoadingUrl.value = true
            _urlImportStatus.value = "Загрузка страницы BBC Learning English и ИИ-извлечение словаря..."
            val scrapedResult = BbcLessonParser.fetchRawPageContent(url.trim())
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
                        onSuccess(lesson)
                    }.onFailure { aiErr ->
                        _urlImportStatus.value = "Ошибка Gemini API: ${aiErr.localizedMessage}"
                        _isLoadingUrl.value = false
                    }
                },
                onFailure = { err ->
                    _urlImportStatus.value = "Ошибка загрузки страницы: ${err.localizedMessage}"
                    _isLoadingUrl.value = false
                }
            )
        }
    }

    fun clearUrlStatus() {
        _urlImportStatus.value = null
    }

    fun submitUserTurn(spokenOrTypedText: String, audioFile: File? = null) {
        val lesson = getSelectedLesson() ?: return
        val history = repository.getConversationForLesson(lesson.id)
        val cleanText = spokenOrTypedText.trim()
        if (cleanText.isBlank() && (audioFile == null || !audioFile.exists())) return

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
                    audioFile = audioFile
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

    fun selectSrsVocab(vocabId: String) {
        _currentSrsVocabId.value = vocabId
        _lastSrsResult.value = null
        val vocab = repository.allVocabularyItems().find { it.id == vocabId }
        if (vocab != null) {
            ensureSrsChallengeLoaded(vocab, forceNew = false)
        }
    }

    fun nextSrsVocab() {
        val all = repository.allVocabularyItems()
        if (all.isEmpty()) return
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
                    _lastSrsResult.value = result
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
