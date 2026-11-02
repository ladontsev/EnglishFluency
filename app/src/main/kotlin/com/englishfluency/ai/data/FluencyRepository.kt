package com.englishfluency.ai.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.max

@Serializable
private data class PersistedAppState(
    val apiKey: String = "",
    val geminiModelId: String = FluencyRepository.DEFAULT_GEMINI_MODEL,
    val lessons: List<Lesson> = emptyList(),
    val conversationsByLesson: Map<String, List<ChatMessage>> = emptyMap()
)

class FluencyRepository(context: Context) {

    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-3-flash-preview"
        val AVAILABLE_MODELS = listOf(
            "gemini-3-flash-preview" to "Gemini 3 Flash (Свежая, быстрая + аудио)",
            "gemini-3.1-pro-preview" to "Gemini 3.1 Pro (Максимальная глубина разбора)",
            "gemini-2.5-flash" to "Gemini 2.5 Flash (Стабильная классика)",
            "gemini-2.5-pro" to "Gemini 2.5 Pro"
        )
    }

    private val appContext = context.applicationContext
    private val storageFile = File(appContext.filesDir, "fluency_state_v1.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _geminiModelId = MutableStateFlow(DEFAULT_GEMINI_MODEL)
    val geminiModelId: StateFlow<String> = _geminiModelId.asStateFlow()

    private val _lessons = MutableStateFlow<List<Lesson>>(emptyList())
    val lessons: StateFlow<List<Lesson>> = _lessons.asStateFlow()

    private val _conversations = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val conversations: StateFlow<Map<String, List<ChatMessage>>> = _conversations.asStateFlow()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        val loaded = runCatching {
            if (storageFile.exists()) {
                json.decodeFromString<PersistedAppState>(storageFile.readText())
            } else {
                null
            }
        }.getOrNull()

        if (loaded != null && loaded.lessons.isNotEmpty()) {
            _apiKey.value = loaded.apiKey
            _geminiModelId.value = loaded.geminiModelId.ifBlank { DEFAULT_GEMINI_MODEL }
            _lessons.value = loaded.lessons
            _conversations.value = loaded.conversationsByLesson
        } else {
            val initialLessons = SampleBbcLessons.defaultLessons()
            _lessons.value = initialLessons
            val initialConvos = initialLessons.associate { lesson ->
                lesson.id to listOf(buildWelcomeTutorMessage(lesson))
            }
            _conversations.value = initialConvos
            saveToDisk()
        }
    }

    private fun saveToDisk() {
        runCatching {
            val state = PersistedAppState(
                apiKey = _apiKey.value,
                geminiModelId = _geminiModelId.value,
                lessons = _lessons.value,
                conversationsByLesson = _conversations.value
            )
            storageFile.writeText(json.encodeToString(state))
        }
    }

    fun updateApiKey(newKey: String) {
        _apiKey.value = newKey.trim()
        saveToDisk()
    }

    fun updateGeminiModelId(modelId: String) {
        _geminiModelId.value = modelId.trim().ifBlank { DEFAULT_GEMINI_MODEL }
        saveToDisk()
    }

    fun addOrReplaceLesson(lesson: Lesson) {
        val current = _lessons.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.id == lesson.id || it.sourceUrl == lesson.sourceUrl }
        if (existingIdx >= 0) {
            current[existingIdx] = lesson
        } else {
            current.add(0, lesson)
        }
        _lessons.value = current

        if (!_conversations.value.containsKey(lesson.id)) {
            _conversations.value = _conversations.value + (lesson.id to listOf(buildWelcomeTutorMessage(lesson)))
        }
        saveToDisk()
    }

    fun getConversationForLesson(lessonId: String): List<ChatMessage> {
        val existing = _conversations.value[lessonId]
        if (!existing.isNullOrEmpty()) return existing
        val lesson = _lessons.value.find { it.id == lessonId } ?: return emptyList()
        val welcome = listOf(buildWelcomeTutorMessage(lesson))
        _conversations.value = _conversations.value + (lessonId to welcome)
        saveToDisk()
        return welcome
    }

    fun appendMessages(lessonId: String, vararg messages: ChatMessage) {
        val current = getConversationForLesson(lessonId).toMutableList()
        current.addAll(messages)
        _conversations.value = _conversations.value + (lessonId to current)
        saveToDisk()
    }

    fun resetConversation(lessonId: String) {
        val lesson = _lessons.value.find { it.id == lessonId } ?: return
        _conversations.value = _conversations.value + (lessonId to listOf(buildWelcomeTutorMessage(lesson)))
        saveToDisk()
    }

    fun markVocabUsedInConversation(
        lessonId: String,
        expressionsUsed: List<String>,
        pronunciationScore: Int
    ) {
        if (expressionsUsed.isEmpty()) return
        val lowerSet = expressionsUsed.map { it.lowercase() }.toSet()
        _lessons.value = _lessons.value.map { lesson ->
            if (lesson.id != lessonId) {
                lesson
            } else {
                lesson.copy(
                    vocabulary = lesson.vocabulary.map { item ->
                        if (lowerSet.contains(item.expression.lowercase())) {
                            val nextMastery = (item.masteryLevel + 1).coerceAtMost(5)
                            val nextInterval = when (nextMastery) {
                                1 -> 1
                                2 -> 3
                                3 -> 7
                                4 -> 14
                                else -> 30
                            }
                            item.copy(
                                masteryLevel = nextMastery,
                                intervalDays = nextInterval,
                                nextReviewEpochMs = System.currentTimeMillis() + nextInterval * 86_400_000L,
                                timesUsedInConversation = item.timesUsedInConversation + 1,
                                lastPronunciationScore = pronunciationScore
                            )
                        } else {
                            item
                        }
                    }
                )
            }
        }
        saveToDisk()
    }

    fun recordSrsReviewResult(vocabId: String, passed: Boolean, score: Int) {
        val now = System.currentTimeMillis()
        _lessons.value = _lessons.value.map { lesson ->
            lesson.copy(
                vocabulary = lesson.vocabulary.map { item ->
                    if (item.id != vocabId) {
                        item
                    } else {
                        val newMastery = if (passed) {
                            (item.masteryLevel + 1).coerceAtMost(5)
                        } else {
                            max(0, item.masteryLevel - 1)
                        }
                        val nextIntervalDays = if (!passed) {
                            0
                        } else {
                            when (newMastery) {
                                1 -> 1
                                2 -> 3
                                3 -> 7
                                4 -> 14
                                else -> 30
                            }
                        }
                        item.copy(
                            masteryLevel = newMastery,
                            intervalDays = nextIntervalDays,
                            nextReviewEpochMs = if (passed) now + nextIntervalDays * 86_400_000L else now,
                            lastPronunciationScore = score
                        )
                    }
                }
            )
        }
        saveToDisk()
    }

    fun allVocabularyItems(): List<VocabularyItem> {
        return _lessons.value.flatMap { it.vocabulary }
    }

    private fun buildWelcomeTutorMessage(lesson: Lesson): ChatMessage {
        val firstQuestion = lesson.discussionQuestions.firstOrNull()
            ?: "What do you think about this topic?"
        val targetHighlights = lesson.vocabulary.take(3).joinToString(", ") { "'${it.expression}'" }
        return ChatMessage(
            id = "welcome_${lesson.id}_${System.currentTimeMillis()}",
            sender = MessageSender.TUTOR,
            text = "Welcome! Today we're discussing \"${lesson.title}\". As we chat, try using expressions from this lesson like $targetHighlights. Let's start: $firstQuestion",
            timestampMs = System.currentTimeMillis()
        )
    }
}
