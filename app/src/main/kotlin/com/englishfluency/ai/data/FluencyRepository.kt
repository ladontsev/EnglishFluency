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
    val conversationsByLesson: Map<String, List<ChatMessage>> = emptyMap(),
    val srsChallengesCache: Map<String, SrsChallenge> = emptyMap()
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
    // Bumped to v2 so old hardcoded sample lessons are not loaded from legacy state
    private val storageFile = File(appContext.filesDir, "fluency_state_v2.json")
    private val legacyStorageFile = File(appContext.filesDir, "fluency_state_v1.json")
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

    private val _srsChallengesCache = MutableStateFlow<Map<String, SrsChallenge>>(emptyMap())
    val srsChallengesCache: StateFlow<Map<String, SrsChallenge>> = _srsChallengesCache.asStateFlow()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        val loaded = runCatching {
            if (storageFile.exists()) {
                json.decodeFromString<PersistedAppState>(storageFile.readText())
            } else if (legacyStorageFile.exists()) {
                // Preserve user's API key & model from v1, but drop static hardcoded lessons
                val old = json.decodeFromString<PersistedAppState>(legacyStorageFile.readText())
                val dynamicLessons = old.lessons.filter {
                    it.id != "ep-261029" && it.id != "bbc-small-talk-work"
                }
                old.copy(
                    lessons = dynamicLessons,
                    conversationsByLesson = old.conversationsByLesson.filterKeys { k ->
                        dynamicLessons.any { it.id == k }
                    }
                )
            } else {
                null
            }
        }.getOrNull()

        if (loaded != null) {
            _apiKey.value = loaded.apiKey
            _geminiModelId.value = loaded.geminiModelId.ifBlank { DEFAULT_GEMINI_MODEL }
            _lessons.value = loaded.lessons
            _conversations.value = loaded.conversationsByLesson
            _srsChallengesCache.value = loaded.srsChallengesCache
            saveToDisk()
        }
    }

    private fun saveToDisk() {
        runCatching {
            val state = PersistedAppState(
                apiKey = _apiKey.value,
                geminiModelId = _geminiModelId.value,
                lessons = _lessons.value,
                conversationsByLesson = _conversations.value,
                srsChallengesCache = _srsChallengesCache.value
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

    fun addOrReplaceLesson(lesson: Lesson, openingMessage: String? = null) {
        val current = _lessons.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.id == lesson.id || it.sourceUrl == lesson.sourceUrl }
        if (existingIdx >= 0) {
            current[existingIdx] = lesson
        } else {
            current.add(0, lesson)
        }
        _lessons.value = current

        val firstMsg = buildWelcomeTutorMessage(lesson, openingMessage)
        _conversations.value = _conversations.value + (lesson.id to listOf(firstMsg))
        saveToDisk()
    }

    fun deleteLesson(lessonId: String) {
        _lessons.value = _lessons.value.filterNot { it.id == lessonId }
        _conversations.value = _conversations.value - lessonId
        saveToDisk()
    }

    fun getConversationForLesson(lessonId: String): List<ChatMessage> {
        val existing = _conversations.value[lessonId]
        if (!existing.isNullOrEmpty()) return existing
        val lesson = _lessons.value.find { it.id == lessonId } ?: return emptyList()
        val welcome = listOf(buildWelcomeTutorMessage(lesson, null))
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

    fun resetConversation(lessonId: String, openingMessage: String? = null) {
        val lesson = _lessons.value.find { it.id == lessonId } ?: return
        _conversations.value = _conversations.value + (lessonId to listOf(buildWelcomeTutorMessage(lesson, openingMessage)))
        saveToDisk()
    }

    fun cacheSrsChallenge(vocabId: String, mode: SrsExerciseMode, challenge: SrsChallenge) {
        val key = "${vocabId}_${mode.name}"
        _srsChallengesCache.value = _srsChallengesCache.value + (key to challenge)
        saveToDisk()
    }

    fun getCachedSrsChallenge(vocabId: String, mode: SrsExerciseMode): SrsChallenge? {
        return _srsChallengesCache.value["${vocabId}_${mode.name}"]
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

    fun addPersonalMistakeToLesson(lessonId: String, mistake: PersonalMistakeItem): VocabularyItem? {
        val cleanExpr = mistake.expression.trim()
        if (cleanExpr.isBlank()) return null

        var addedItem: VocabularyItem? = null
        _lessons.value = _lessons.value.map { lesson ->
            if (lesson.id != lessonId) {
                lesson
            } else {
                val alreadyExists = lesson.vocabulary.any {
                    it.expression.equals(cleanExpr, ignoreCase = true)
                }
                if (alreadyExists) {
                    lesson
                } else {
                    val newItem = VocabularyItem(
                        id = "${lesson.id}_err_${System.currentTimeMillis()}",
                        lessonId = lesson.id,
                        expression = cleanExpr,
                        definitionEn = mistake.definitionEn.ifBlank { cleanExpr },
                        translationRu = mistake.translationRu.ifBlank { cleanExpr },
                        exampleSentence = mistake.exampleSentence.ifBlank { cleanExpr },
                        synonyms = mistake.synonyms,
                        masteryLevel = 0,
                        intervalDays = 0,
                        nextReviewEpochMs = 0L,
                        isPersonalMistake = true,
                        userOriginalMistake = mistake.userOriginalMistake
                    )
                    addedItem = newItem
                    lesson.copy(vocabulary = lesson.vocabulary + newItem)
                }
            }
        }
        if (addedItem != null) {
            saveToDisk()
        }
        return addedItem
    }

    fun allVocabularyItems(): List<VocabularyItem> {
        return _lessons.value.flatMap { it.vocabulary }
    }

    private fun buildWelcomeTutorMessage(lesson: Lesson, customOpening: String?): ChatMessage {
        val text = if (!customOpening.isNullOrBlank()) {
            customOpening
        } else {
            val firstQuestion = lesson.discussionQuestions.firstOrNull().orEmpty()
            val targetHighlights = lesson.vocabulary.take(3).joinToString(", ") { "'${it.expression}'" }
            "Welcome! Today we're discussing \"${lesson.title}\". Try using expressions from this lesson like $targetHighlights. $firstQuestion"
        }
        return ChatMessage(
            id = "welcome_${lesson.id}_${System.currentTimeMillis()}",
            sender = MessageSender.TUTOR,
            text = text,
            timestampMs = System.currentTimeMillis()
        )
    }
}
