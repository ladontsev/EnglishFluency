package com.englishfluency.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.englishfluency.ai.audio.VoiceSessionController
import com.englishfluency.ai.data.BbcLessonParser
import com.englishfluency.ai.data.ChatMessage
import com.englishfluency.ai.data.FluencyRepository
import com.englishfluency.ai.data.GeminiTutorService
import com.englishfluency.ai.data.Lesson
import com.englishfluency.ai.data.MessageSender
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

    private val _selectedLessonId = MutableStateFlow(
        repository.lessons.value.firstOrNull()?.id ?: "ep-261029"
    )
    val selectedLessonId: StateFlow<String> = _selectedLessonId.asStateFlow()

    private val _isLoadingUrl = MutableStateFlow(false)
    val isLoadingUrl: StateFlow<Boolean> = _isLoadingUrl.asStateFlow()

    private val _urlImportStatus = MutableStateFlow<String?>(null)
    val urlImportStatus: StateFlow<String?> = _urlImportStatus.asStateFlow()

    private val _isProcessingTurn = MutableStateFlow(false)
    val isProcessingTurn: StateFlow<Boolean> = _isProcessingTurn.asStateFlow()

    // SRS Review State
    private val _selectedSrsMode = MutableStateFlow(SrsExerciseMode.RU_TO_EN)
    val selectedSrsMode: StateFlow<SrsExerciseMode> = _selectedSrsMode.asStateFlow()

    private val _currentSrsVocabId = MutableStateFlow<String?>(null)
    val currentSrsVocabId: StateFlow<String?> = _currentSrsVocabId.asStateFlow()

    private val _lastSrsResult = MutableStateFlow<SrsEvaluationResult?>(null)
    val lastSrsResult: StateFlow<SrsEvaluationResult?> = _lastSrsResult.asStateFlow()

    private val _isEvaluatingSrs = MutableStateFlow(false)
    val isEvaluatingSrs: StateFlow<Boolean> = _isEvaluatingSrs.asStateFlow()

    fun selectLesson(lessonId: String) {
        _selectedLessonId.value = lessonId
    }

    fun getSelectedLesson(): Lesson? {
        return lessons.value.find { it.id == _selectedLessonId.value } ?: lessons.value.firstOrNull()
    }

    fun importBbcLessonFromUrl(url: String, onSuccess: (Lesson) -> Unit) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _isLoadingUrl.value = true
            _urlImportStatus.value = "Загрузка страницы BBC Learning English и извлечение словаря..."
            val scrapedResult = BbcLessonParser.fetchRawPageContent(url.trim())
            scrapedResult.fold(
                onSuccess = { scraped ->
                    val lesson = geminiService.extractLessonFromScrapedPage(
                        apiKey = apiKey.value,
                        scraped = scraped
                    )
                    repository.addOrReplaceLesson(lesson)
                    _selectedLessonId.value = lesson.id
                    _urlImportStatus.value = "Урок «${lesson.title}» успешно добавлен!"
                    _isLoadingUrl.value = false
                    onSuccess(lesson)
                },
                onFailure = { err ->
                    // Fallback if network is restricted or page format changed
                    val fallback = BbcLessonParser.buildFallbackLessonFromScraped(
                        BbcLessonParser.ScrapedBbcPage(
                            url = url.trim(),
                            title = "BBC 6 Minute English (${url.substringAfterLast('/')})",
                            rawText = ""
                        )
                    )
                    repository.addOrReplaceLesson(fallback)
                    _selectedLessonId.value = fallback.id
                    _urlImportStatus.value = "Создан шаблон урока по ссылке (${err.localizedMessage ?: "офлайн-режим"})"
                    _isLoadingUrl.value = false
                    onSuccess(fallback)
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
        val cleanText = spokenOrTypedText.trim().ifBlank {
            "I think in the long run technology can be a blessing in disguise if we use it mindfully."
        }

        viewModelScope.launch {
            _isProcessingTurn.value = true
            val response = geminiService.evaluateConversationTurn(
                apiKey = apiKey.value,
                lesson = lesson,
                history = history,
                userSpeechText = cleanText,
                audioFile = audioFile
            )

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
        }
    }

    fun speakText(text: String) {
        voiceController.speakTutorText(text)
    }

    fun resetCurrentConversation() {
        val lesson = getSelectedLesson() ?: return
        repository.resetConversation(lesson.id)
    }

    // SRS Methods
    fun setSrsMode(mode: SrsExerciseMode) {
        _selectedSrsMode.value = mode
        _lastSrsResult.value = null
    }

    fun selectSrsVocab(vocabId: String) {
        _currentSrsVocabId.value = vocabId
        _lastSrsResult.value = null
    }

    fun nextSrsVocab() {
        val all = repository.allVocabularyItems()
        if (all.isEmpty()) return
        val currentIdx = all.indexOfFirst { it.id == _currentSrsVocabId.value }
        val nextIdx = if (currentIdx < 0 || currentIdx + 1 >= all.size) 0 else currentIdx + 1
        _currentSrsVocabId.value = all[nextIdx].id
        _lastSrsResult.value = null
    }

    fun buildPromptForSrsItem(vocab: VocabularyItem, mode: SrsExerciseMode): String {
        return when (mode) {
            SrsExerciseMode.RU_TO_EN ->
                "Переведите на английский вслух, обязательно используя «${vocab.expression}»:\n\n«Сначала эта ситуация казалась сложной, но в итоге здесь есть скрытый смысл / ${vocab.translationRu}.»"
            SrsExerciseMode.EN_TO_EN_PARAPHRASE -> {
                val synonymToReplace = vocab.synonyms.firstOrNull() ?: vocab.definitionEn
                "Перефразируйте предложение (EN → EN), заменив выделенную мысль («$synonymToReplace») на идиому из урока:\n\n\"Instead of saying '$synonymToReplace', how would you express this idea naturally using '${vocab.expression}'?\""
            }
            SrsExerciseMode.SITUATIONAL_QA ->
                "Ответьте вслух (1–2 предложения) на вопрос, обязательно использовав выражение «${vocab.expression}»:\n\n\"Can you share a real example from your work or daily life where '${vocab.expression}' applies?\""
        }
    }

    fun evaluateSrsResponse(
        vocab: VocabularyItem,
        spokenOrTypedText: String,
        audioFile: File? = null
    ) {
        val mode = _selectedSrsMode.value
        val prompt = buildPromptForSrsItem(vocab, mode)
        viewModelScope.launch {
            _isEvaluatingSrs.value = true
            val result = geminiService.evaluateSrsAttempt(
                apiKey = apiKey.value,
                vocab = vocab,
                mode = mode,
                challengePrompt = prompt,
                userSpokenText = spokenOrTypedText,
                audioFile = audioFile
            )
            repository.recordSrsReviewResult(
                vocabId = vocab.id,
                passed = result.passed,
                score = result.score
            )
            _lastSrsResult.value = result
            _isEvaluatingSrs.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
    }
}
