package com.englishfluency.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.UUID

object BbcLessonParser {

    /**
     * Downloads a BBC Learning English 6 Minute English page and extracts its title,
     * summary, transcript excerpt, and raw text so we can either parse vocabulary directly
     * or pass the page content to Gemini for structured extraction.
     */
    suspend fun fetchRawPageContent(url: String): Result<ScrapedBbcPage> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
                .timeout(15_000)
                .get()

            val title = doc.selectFirst("h1")?.text()
                ?: doc.title().replace("BBC Learning English - ", "").trim()

            // Collect paragraphs inside main content containers
            val paragraphs = doc.select("article p, main p, .widget-richtext p, p")
                .map { it.text().trim() }
                .filter { it.length > 15 }

            val fullText = paragraphs.joinToString("\n")
            ScrapedBbcPage(
                url = url,
                title = title.ifBlank { "BBC 6 Minute English Episode" },
                rawText = fullText.take(12_000)
            )
        }
    }

    /**
     * Heuristic fallback parser when offline or if Gemini API key is not configured yet.
     */
    fun buildFallbackLessonFromScraped(scraped: ScrapedBbcPage): Lesson {
        val lessonId = "bbc_" + UUID.randomUUID().toString().take(8)
        val lines = scraped.rawText.lines().filter { it.isNotBlank() }
        val summary = lines.take(3).joinToString(" ").take(350)
        val transcript = lines.take(25).joinToString("\n\n").take(2500)

        // Try to spot bold terms or vocabulary-like lines, or provide default high-value phrases
        val defaultVocab = listOf(
            VocabularyItem(
                id = "${lessonId}_v1",
                lessonId = lessonId,
                expression = "food for thought",
                definitionEn = "something that warrants serious consideration",
                translationRu = "пища для размышлений",
                exampleSentence = "This episode certainly gives us plenty of food for thought.",
                synonyms = listOf("something to think about", "a thought-provoking point")
            ),
            VocabularyItem(
                id = "${lessonId}_v2",
                lessonId = lessonId,
                expression = "pros and cons",
                definitionEn = "advantages and disadvantages of something",
                translationRu = "плюсы и минусы / аргументы за и против",
                exampleSentence = "We need to weigh up the pros and cons before making a decision.",
                synonyms = listOf("upsides and downsides", "benefits and drawbacks")
            ),
            VocabularyItem(
                id = "${lessonId}_v3",
                lessonId = lessonId,
                expression = "in the long run",
                definitionEn = "over or after a long period of time; eventually",
                translationRu = "в долгосрочной перспективе / в конечном счете",
                exampleSentence = "It might be tough now, but it will pay off in the long run.",
                synonyms = listOf("ultimately", "over time", "eventually")
            )
        )

        return Lesson(
            id = lessonId,
            title = scraped.title,
            subtitle = "BBC Learning English • 6 Minute English",
            sourceUrl = scraped.url,
            dateLabel = "Импортированный урок",
            topicSummary = summary.ifBlank { "Разговорный практикум по материалам выпуска BBC 6 Minute English." },
            discussionQuestions = listOf(
                "What stood out to you the most in this topic, and how does it relate to your daily life?",
                "What are the pros and cons of this trend in the long run?",
                "Does this give you any food for thought about how habits are changing?"
            ),
            transcriptExcerpt = transcript.ifBlank { "Транскрипт загружен со страницы: ${scraped.url}" },
            vocabulary = defaultVocab
        )
    }

    data class ScrapedBbcPage(
        val url: String,
        val title: String,
        val rawText: String
    )
}
