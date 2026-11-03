package com.englishfluency.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object BbcLessonParser {

    /**
     * Downloads a BBC Learning English 6 Minute English page and extracts its title,
     * summary, transcript, and raw text so Gemini can dynamically extract all vocabulary,
     * definitions, Russian translations, synonyms, and discussion questions.
     */
    suspend fun fetchRawPageContent(url: String): Result<ScrapedBbcPage> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
                .timeout(20_000)
                .get()

            val title = doc.selectFirst("h1")?.text()
                ?: doc.title().replace("BBC Learning English - ", "").trim()

            // Collect paragraphs and list items inside main content containers
            val elements = doc.select("article p, article li, main p, main li, .widget-richtext p, .widget-richtext li, p")
                .map { it.text().trim() }
                .filter { it.length > 10 }

            val fullText = elements.distinct().joinToString("\n")
            if (fullText.isBlank()) {
                throw IllegalStateException("Не удалось извлечь текст со страницы: $url")
            }

            ScrapedBbcPage(
                url = url,
                title = title.ifBlank { url.substringAfterLast('/') },
                rawText = fullText.take(16_000)
            )
        }
    }

    data class ScrapedBbcPage(
        val url: String,
        val title: String,
        val rawText: String
    )
}
