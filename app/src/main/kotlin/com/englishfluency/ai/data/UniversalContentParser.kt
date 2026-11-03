package com.englishfluency.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object UniversalContentParser {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    data class ExtractedContent(
        val title: String,
        val sourceUrl: String,
        val sourceLabel: String,
        val rawText: String,
        val contentType: UniversalContentType
    )

    enum class UniversalContentType {
        BBC,
        YOUTUBE,
        WEB_ARTICLE,
        CUSTOM_NOTES;

        fun toLessonSourceType(): LessonSourceType = when (this) {
            BBC -> LessonSourceType.BBC
            YOUTUBE -> LessonSourceType.YOUTUBE
            WEB_ARTICLE -> LessonSourceType.WEB_ARTICLE
            CUSTOM_NOTES -> LessonSourceType.CUSTOM_NOTES
        }
    }

    /**
     * Extracts YouTube video ID from standard URLs, short links, or Shorts.
     */
    fun extractYouTubeVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.length == 11 && trimmed.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            return trimmed
        }
        val patterns = listOf(
            Regex("(?:https?://)?(?:www\\.|m\\.)?youtube\\.com/watch\\?.*v=([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?://)?(?:www\\.|m\\.)?youtu\\.be/([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?://)?(?:www\\.|m\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?://)?(?:www\\.|m\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11})")
        )
        for (pattern in patterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    /**
     * Automatically detects the source type from input string.
     */
    fun detectContentType(input: String): UniversalContentType {
        val trimmed = input.trim()
        if (extractYouTubeVideoId(trimmed) != null) {
            return UniversalContentType.YOUTUBE
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return if (trimmed.contains("bbc.co.uk/learningenglish")) {
                UniversalContentType.BBC
            } else {
                UniversalContentType.WEB_ARTICLE
            }
        }
        return UniversalContentType.CUSTOM_NOTES
    }

    /**
     * Fetches video metadata and subtitle transcript using YouTube InnerTube Android API.
     */
    suspend fun parseYouTubeVideo(urlOrId: String): Result<ExtractedContent> = withContext(Dispatchers.IO) {
        runCatching {
            val videoId = extractYouTubeVideoId(urlOrId)
                ?: throw IllegalArgumentException("Не удалось определить ID YouTube видео из: $urlOrId")

            val playerEndpoint = "https://www.youtube.com/youtubei/v1/player"
            val requestBody = """
                {
                  "context": {
                    "client": {
                      "clientName": "ANDROID",
                      "clientVersion": "20.10.38"
                    }
                  },
                  "videoId": "$videoId"
                }
            """.trimIndent()

            val conn = (URL(playerEndpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "com.google.android.youtube/20.10.38 (Linux; U; Android 14)")
            }

            conn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val responseCode = conn.responseCode
            val responseText = (if (responseCode in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }

            if (responseCode !in 200..299) {
                throw IllegalStateException("YouTube API вернул статус $responseCode: ${responseText.take(300)}")
            }

            val rootJson = json.parseToJsonElement(responseText).jsonObject
            val videoDetails = rootJson["videoDetails"]?.jsonObject
            val videoTitle = videoDetails?.get("title")?.jsonPrimitive?.content.orEmpty()
                .ifBlank { "YouTube Video ($videoId)" }
            val videoAuthor = videoDetails?.get("author")?.jsonPrimitive?.content.orEmpty()

            // Extract captions
            val captions = rootJson["captions"]?.jsonObject
                ?.get("playerCaptionsTracklistRenderer")?.jsonObject
                ?.get("captionTracks")?.jsonArray

            if (captions.isNullOrEmpty()) {
                throw IllegalStateException(
                    "У видео «$videoTitle» нет доступных субтитров на YouTube.\n\n" +
                    "💡 Вы можете скопировать расшифровку или текст во вкладку «Свой текст», чтобы создать урок."
                )
            }

            // Pick English track or fallback to first available
            val trackList = captions.mapNotNull { it.jsonObject }
            val preferredTrack = trackList.find {
                val lang = it["languageCode"]?.jsonPrimitive?.content.orEmpty()
                lang.startsWith("en", ignoreCase = true)
            } ?: trackList.first()

            val rawBaseUrl = preferredTrack["baseUrl"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Не найден URL для скачивания субтитров видео")

            // Ensure json3 format for clean parsing
            val timedTextUrl = if (rawBaseUrl.contains("fmt=")) {
                rawBaseUrl.replace(Regex("fmt=\\w+"), "fmt=json3")
            } else {
                "$rawBaseUrl&fmt=json3"
            }

            val timedTextConn = (URL(timedTextUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val timedTextBody = timedTextConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val transcriptText = parseTimedTextJson3(timedTextBody)

            if (transcriptText.isBlank()) {
                throw IllegalStateException("Транскрипт видео пуст или не удалось извлечь речь.")
            }

            val authorSuffix = if (videoAuthor.isNotBlank()) " • $videoAuthor" else ""
            ExtractedContent(
                title = videoTitle,
                sourceUrl = "https://www.youtube.com/watch?v=$videoId",
                sourceLabel = "YouTube$authorSuffix",
                rawText = transcriptText.take(18_000),
                contentType = UniversalContentType.YOUTUBE
            )
        }
    }

    private fun parseTimedTextJson3(jsonString: String): String {
        return runCatching {
            val root = json.parseToJsonElement(jsonString).jsonObject
            val events = root["events"]?.jsonArray ?: return@runCatching ""
            val buffer = StringBuilder()
            var lastSpeakerLine = ""

            for (ev in events) {
                val segs = ev.jsonObject["segs"]?.jsonArray ?: continue
                val text = segs.mapNotNull { it.jsonObject["utf8"]?.jsonPrimitive?.content }
                    .joinToString("")
                    .trim()
                if (text.isNotBlank() && text != "\n") {
                    if (buffer.isNotEmpty()) {
                        buffer.append(" ")
                    }
                    buffer.append(text)
                }
            }
            buffer.toString()
        }.getOrDefault("")
    }

    /**
     * Downloads any web article via Jsoup and strips noise, menus, ads, and footers.
     */
    suspend fun parseWebArticle(url: String): Result<ExtractedContent> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = url.trim()
            val doc = Jsoup.connect(cleanUrl)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9,ru;q=0.8")
                .timeout(25_000)
                .get()

            // Remove scripts, styles, forms, ads, headers, navs
            doc.select(
                "script, style, nav, footer, header, noscript, aside, form, svg, iframe, " +
                ".advertisement, .ads, .comment, .comments, .cookie-banner, .social-share, " +
                "[role=navigation], [role=banner]"
            ).remove()

            // Extract title
            val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")
            val h1 = doc.selectFirst("h1")?.text()
            val title = ogTitle?.takeIf { it.isNotBlank() }
                ?: h1?.takeIf { it.isNotBlank() }
                ?: doc.title().trim().ifBlank { cleanUrl.substringAfterLast('/') }

            // Extract site name / domain
            val ogSiteName = doc.selectFirst("meta[property=og:site_name]")?.attr("content")
            val host = runCatching { URI(cleanUrl).host?.removePrefix("www.") }.getOrNull()
            val domainLabel = ogSiteName?.takeIf { it.isNotBlank() } ?: host ?: "Веб-статья"

            // Collect article paragraphs
            val paragraphs = doc.select("article p, main p, [role=main] p, .content p, .article-body p, .entry-content p, .post-content p, p")
                .map { it.text().trim() }
                .filter { it.length > 25 }

            val fullText = if (paragraphs.isNotEmpty()) {
                paragraphs.distinct().joinToString("\n\n")
            } else {
                doc.body().text().take(16_000)
            }

            if (fullText.length < 50) {
                throw IllegalStateException("Не удалось извлечь достаточно текста из статьи: $cleanUrl")
            }

            ExtractedContent(
                title = title,
                sourceUrl = cleanUrl,
                sourceLabel = "$domainLabel • Статья",
                rawText = fullText.take(18_000),
                contentType = UniversalContentType.WEB_ARTICLE
            )
        }
    }

    /**
     * Parses custom raw user notes, emails, transcripts, or speech drafts.
     */
    fun parseRawNotes(rawText: String, customTitle: String?): Result<ExtractedContent> {
        val clean = rawText.trim()
        if (clean.length < 30) {
            return Result.failure(IllegalArgumentException("Текст слишком короткий (минимум 30 символов)."))
        }

        val derivedTitle = customTitle?.trim()?.takeIf { it.isNotBlank() }
            ?: clean.lines().firstOrNull { it.isNotBlank() }?.take(60)?.trim()
            ?: "Пользовательские заметки"

        return Result.success(
            ExtractedContent(
                title = derivedTitle,
                sourceUrl = "",
                sourceLabel = "Заметки & Текст",
                rawText = clean.take(18_000),
                contentType = UniversalContentType.CUSTOM_NOTES
            )
        )
    }
}
