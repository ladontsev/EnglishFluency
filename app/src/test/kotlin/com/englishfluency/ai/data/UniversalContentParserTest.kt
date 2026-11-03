package com.englishfluency.ai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalContentParserTest {

    @Test
    fun `extractYouTubeVideoId correctly parses various url formats`() {
        val testCases = mapOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "http://youtube.com/watch?v=dQw4w9WgXcQ&t=10s" to "dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "dQw4w9WgXcQ" to "dQw4w9WgXcQ"
        )

        for ((input, expectedId) in testCases) {
            val extracted = UniversalContentParser.extractYouTubeVideoId(input)
            assertEquals("Failed for input: $input", expectedId, extracted)
        }

        assertNull(UniversalContentParser.extractYouTubeVideoId("https://google.com"))
        assertNull(UniversalContentParser.extractYouTubeVideoId("short"))
    }

    @Test
    fun `detectContentType accurately classifies urls and raw text`() {
        assertEquals(
            UniversalContentParser.UniversalContentType.YOUTUBE,
            UniversalContentParser.detectContentType("https://www.youtube.com/watch?v=Ks-_Mh1QhMc")
        )
        assertEquals(
            UniversalContentParser.UniversalContentType.YOUTUBE,
            UniversalContentParser.detectContentType("https://youtu.be/dQw4w9WgXcQ")
        )
        assertEquals(
            UniversalContentParser.UniversalContentType.BBC,
            UniversalContentParser.detectContentType("https://www.bbc.co.uk/learningenglish/english/features/6-minute-english/ep-240101")
        )
        assertEquals(
            UniversalContentParser.UniversalContentType.WEB_ARTICLE,
            UniversalContentParser.detectContentType("https://medium.com/@author/learning-english-fluently-123")
        )
        assertEquals(
            UniversalContentParser.UniversalContentType.CUSTOM_NOTES,
            UniversalContentParser.detectContentType("Hi team, here are my notes for the upcoming architecture review.")
        )
    }

    @Test
    fun `parseRawNotes creates valid content with derived title`() {
        val rawText = """
            Today we discussed the quarterly financial results and future strategic priorities.
            Several key stakeholders emphasized the importance of cost optimization and automation.
        """.trimIndent()

        val result = UniversalContentParser.parseRawNotes(rawText, customTitle = "Quarterly Strategy")
        assertTrue(result.isSuccess)

        val content = result.getOrThrow()
        assertEquals("Quarterly Strategy", content.title)
        assertEquals(UniversalContentParser.UniversalContentType.CUSTOM_NOTES, content.contentType)
        assertEquals(LessonSourceType.CUSTOM_NOTES, content.contentType.toLessonSourceType())
        assertTrue(content.rawText.contains("quarterly financial results"))
    }

    @Test
    fun `parseRawNotes rejects too short input`() {
        val shortText = "Hi there"
        val result = UniversalContentParser.parseRawNotes(shortText, customTitle = null)
        assertTrue(result.isFailure)
    }
}
