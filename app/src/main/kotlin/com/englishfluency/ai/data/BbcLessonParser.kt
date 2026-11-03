package com.englishfluency.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object BbcLessonParser {

    private const val BBC_BASE_URL = "https://www.bbc.co.uk"
    private const val BBC_INDEX_URL = "https://www.bbc.co.uk/learningenglish/english/features/6-minute-english"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    /**
     * Fetches the live catalog of the latest BBC 6 Minute English episodes directly from BBC's website.
     * Zero static content — always parses the live index page.
     */
    suspend fun fetchLatestEpisodeCatalog(limit: Int = 35): Result<List<BbcCatalogItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val doc = Jsoup.connect(BBC_INDEX_URL)
                    .userAgent(USER_AGENT)
                    .timeout(20_000)
                    .get()

                val links = doc.select("a[href*=/features/6-minute-english][href*=ep-]")
                val itemsBySlug = linkedMapOf<String, BbcCatalogItem>()

                for (a in links) {
                    val rawHref = a.attr("href").trim()
                    if (rawHref.isBlank()) continue
                    val fullUrl = if (rawHref.startsWith("http")) {
                        rawHref
                    } else {
                        BBC_BASE_URL + rawHref
                    }
                    val slug = fullUrl.substringAfterLast('/')
                    if (!slug.startsWith("ep-")) continue

                    val text = a.text().trim()
                    // Skip image-only links or generic links without the episode title
                    if (text.length < 6) continue

                    if (!itemsBySlug.containsKey(slug)) {
                        itemsBySlug[slug] = BbcCatalogItem(
                            episodeSlug = slug,
                            title = text,
                            url = fullUrl,
                            dateFormatted = formatEpisodeDate(slug)
                        )
                    }
                    if (itemsBySlug.size >= limit) break
                }

                itemsBySlug.values.toList()
            }
        }

    private fun formatEpisodeDate(slug: String): String {
        val digits = slug.removePrefix("ep-").filter { it.isDigit() }
        return if (digits.length == 6) {
            val yy = digits.substring(0, 2)
            val mm = digits.substring(2, 4)
            val dd = digits.substring(4, 6)
            "$dd.$mm.20$yy"
        } else {
            slug
        }
    }

    /**
     * Downloads a BBC Learning English 6 Minute English page and extracts its title,
     * summary, transcript, and raw text so Gemini can dynamically extract all vocabulary,
     * definitions, Russian translations, synonyms, and discussion questions.
     */
    suspend fun fetchRawPageContent(url: String): Result<ScrapedBbcPage> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
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
