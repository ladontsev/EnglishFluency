package com.englishfluency.ai.data

import kotlinx.serialization.Serializable

@Serializable
data class BbcCatalogItem(
    val episodeSlug: String,
    val title: String,
    val url: String,
    val dateFormatted: String
)

@Serializable
data class GeminiModelInfo(
    val id: String,
    val displayName: String,
    val description: String = ""
)

@Serializable
data class VocabularyItem(
    val id: String,
    val lessonId: String,
    val expression: String,
    val definitionEn: String,
    val translationRu: String,
    val exampleSentence: String,
    val synonyms: List<String> = emptyList(),
    // Spaced Repetition (SRS) state
    val masteryLevel: Int = 0, // 0..5
    val intervalDays: Int = 0,
    val nextReviewEpochMs: Long = 0L,
    val timesUsedInConversation: Int = 0,
    val lastPronunciationScore: Int? = null,
    // Stage 2 (v1.2): Personal mistake tracking
    val isPersonalMistake: Boolean = false,
    val userOriginalMistake: String = ""
)

@Serializable
enum class LessonSourceType(val labelRu: String, val badgeEmoji: String) {
    BBC("BBC 6 Minute English", "🎙️"),
    YOUTUBE("YouTube", "📺"),
    WEB_ARTICLE("Веб-статья", "🌐"),
    CUSTOM_NOTES("Заметки & Текст", "📝")
}

@Serializable
data class Lesson(
    val id: String,
    val title: String,
    val subtitle: String,
    val sourceUrl: String,
    val dateLabel: String,
    val topicSummary: String,
    val discussionQuestions: List<String>,
    val transcriptExcerpt: String,
    val vocabulary: List<VocabularyItem>,
    val sourceType: LessonSourceType = LessonSourceType.BBC
)

@Serializable
data class WordPronunciationItem(
    val word: String,
    val status: String = "GOOD", // "GOOD", "WARN", "NEEDS_WORK"
    val ipa: String = "",
    val tipRu: String = ""
)

@Serializable
data class PersonalMistakeItem(
    val expression: String = "",
    val definitionEn: String = "",
    val translationRu: String = "",
    val exampleSentence: String = "",
    val synonyms: List<String> = emptyList(),
    val userOriginalMistake: String = ""
)

@Serializable
data class SpeechAnalysis(
    val userTranscript: String,
    val overallScore: Int, // 0..100
    val targetVocabUsed: List<String> = emptyList(),
    val targetVocabSuggestion: String = "",
    val grammarFeedback: String = "",
    val correctedSentence: String = "",
    val nativeAlternative: String = "",
    val pronunciationScore: Int = 85, // 0..100
    val pronunciationNotes: String = "",
    val intonationNotes: String = "",
    val wordBreakdown: List<WordPronunciationItem> = emptyList(),
    val repeatPrompt: String? = null, // If tutor asks user to repeat a specific phrase
    val personalMistakeToLearn: PersonalMistakeItem? = null
)

@Serializable
data class ChatMessage(
    val id: String,
    val sender: MessageSender,
    val text: String,
    val timestampMs: Long,
    val analysis: SpeechAnalysis? = null,
    val audioFilePath: String? = null
)

@Serializable
enum class MessageSender {
    TUTOR,
    USER
}

enum class SrsExerciseMode(val titleRu: String, val subtitleRu: String) {
    RU_TO_EN(
        "RU → EN (Перевод с целевой фразой)",
        "Произнесите перевод русской фразы на английский, используя изучаемое выражение"
    ),
    EN_TO_EN_PARAPHRASE(
        "EN → EN (Синонимы и перефразирование)",
        "Замените простое выражение в предложении на идиому или нейтив-конструкцию из урока"
    ),
    SITUATIONAL_QA(
        "Контекстный ответ вслух",
        "Ответьте голосом на вопрос тьютора, обязательно ввернув выражение на карточке"
    )
}

enum class SrsFilterCategory(val labelRu: String) {
    ALL("Все выражения"),
    PERSONAL_MISTAKES("⚠️ Мои ошибки"),
    DUE_TODAY("🔥 На сегодня")
}

@Serializable
data class RoleplayScenario(
    val id: String,
    val lessonId: String,
    val title: String,
    val aiCharacterName: String,
    val aiCharacterRole: String,
    val userRole: String,
    val situationRu: String,
    val missionGoalRu: String,
    val initialAiLine: String,
    val checklistExpressions: List<String>
)

@Serializable
data class SrsChallenge(
    val vocabId: String,
    val modeName: String,
    val promptText: String,
    val hintText: String,
    val sampleIdealAnswer: String
)

@Serializable
data class SrsEvaluationResult(
    val passed: Boolean,
    val score: Int,
    val recognizedSpeech: String,
    val usedTargetExpression: Boolean,
    val feedbackRu: String,
    val nativePolishedVersion: String,
    val pronunciationTip: String,
    val audioFilePath: String? = null
)
