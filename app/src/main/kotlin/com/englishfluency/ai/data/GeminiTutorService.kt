package com.englishfluency.ai.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class GeminiTutorService(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {

    @Serializable
    data class TutorTurnResponse(
        val tutorReplyEn: String,
        val analysis: SpeechAnalysis
    )

    @Serializable
    private data class ExtractedLessonSchema(
        val title: String,
        val topicSummary: String,
        val discussionQuestions: List<String>,
        val vocabulary: List<ExtractedVocabSchema>
    )

    @Serializable
    private data class ExtractedVocabSchema(
        val expression: String,
        val definitionEn: String,
        val translationRu: String,
        val exampleSentence: String,
        val synonyms: List<String>
    )

    /**
     * Processes a user's Push-to-Talk turn (either raw audio file + optional STT transcript or text)
     * in the context of a BBC Learning English lesson.
     */
    suspend fun evaluateConversationTurn(
        apiKey: String,
        modelId: String = FluencyRepository.DEFAULT_GEMINI_MODEL,
        lesson: Lesson,
        history: List<ChatMessage>,
        userSpeechText: String,
        audioFile: File?
    ): TutorTurnResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext buildSimulatedTutorResponse(lesson, history, userSpeechText)
        }

        val vocabListPrompt = lesson.vocabulary.joinToString("\n") {
            "- \"${it.expression}\" (${it.definitionEn} / ${it.translationRu}) | Synonyms: ${it.synonyms.joinToString(", ")}"
        }

        val historyTranscript = history.takeLast(8).joinToString("\n") { msg ->
            val role = if (msg.sender == MessageSender.TUTOR) "TUTOR" else "STUDENT"
            "$role: ${msg.text}"
        }

        val systemInstruction = """
            You are an expert British English conversational coach leading a 1-on-1 discussion based on a BBC Learning English (6 Minute English) episode.
            
            LESSON TITLE: ${lesson.title}
            TOPIC SUMMARY: ${lesson.topicSummary}
            TARGET VOCABULARY FROM THIS LESSON:
            $vocabListPrompt
            
            YOUR GOALS:
            1. Engage the student in a natural, friendly conversation about the lesson topic.
            2. Actively encourage them to use the Target Vocabulary expressions in their answers.
            3. Evaluate every student turn across 4 dimensions:
               - Target vocabulary usage (which target expressions they used, or which one fits their thought right now).
               - Grammar & accuracy (concise explanation in Russian + corrected English sentence).
               - Native phrasing / idioms (how an educated native speaker would naturally phrase the student's idea).
               - Pronunciation & intonation (analyze the attached audio if present, or phonetic challenges of the words they used: word stress, linking, weak forms, rising/falling intonation, in Russian).
            4. If the student made a notable mistake or missed a great opportunity to use a target phrase, set "repeatPrompt" to a short natural sentence containing the target phrase and ask them in your reply to try saying it out loud, or ask the next engaging follow-up question from the lesson topic.
            
            Return ONLY valid JSON matching this exact structure:
            {
              "tutorReplyEn": "Your spoken response in English (2-3 sentences max, warm and conversational, ending with a question or a prompt to repeat a phrase)",
              "analysis": {
                "userTranscript": "Exact transcript of what the student said in English",
                "overallScore": 85,
                "targetVocabUsed": ["exact expression from target list if used"],
                "targetVocabSuggestion": "Подсказка на русском: какую фразу из урока здесь отлично было бы ввернуть и как именно",
                "grammarFeedback": "Разбор грамматики на русском языке",
                "correctedSentence": "Grammatically corrected version of the student's utterance",
                "nativeAlternative": "How a native speaker would naturally express this thought using idioms/chunks",
                "pronunciationScore": 88,
                "pronunciationNotes": "Разбор произношения на русском (ударения, связки слов, звуки)",
                "intonationNotes": "Разбор интонации и ритма фразы на русском",
                "repeatPrompt": "Optional short English sentence for the student to repeat out loud, or null"
              }
            }
        """.trimIndent()

        val userPrompt = """
            CONVERSATION HISTORY SO FAR:
            $historyTranscript
            
            STUDENT'S NEW TURN (STT hint: "$userSpeechText"):
            Please listen to the student's audio (if attached) and/or read their transcript, then respond with the JSON object.
        """.trimIndent()

        runCatching {
            val rawJson = callGeminiGenerateContent(
                apiKey = apiKey,
                modelId = modelId,
                promptText = "$systemInstruction\n\n$userPrompt",
                audioFile = audioFile
            )
            json.decodeFromString<TutorTurnResponse>(cleanJsonFence(rawJson))
        }.getOrElse {
            buildSimulatedTutorResponse(lesson, history, userSpeechText)
        }
    }

    /**
     * Evaluates an active Spaced Repetition (SRS) oral exercise (RU->EN, EN->EN paraphrase, or Situational Q&A).
     */
    suspend fun evaluateSrsAttempt(
        apiKey: String,
        modelId: String = FluencyRepository.DEFAULT_GEMINI_MODEL,
        vocab: VocabularyItem,
        mode: SrsExerciseMode,
        challengePrompt: String,
        userSpokenText: String,
        audioFile: File?
    ): SrsEvaluationResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext buildSimulatedSrsEvaluation(vocab, mode, userSpokenText)
        }

        val prompt = """
            You are evaluating an English learner's spoken answer in a Spaced Repetition active recall drill.
            Target Expression: "${vocab.expression}"
            Meaning: ${vocab.definitionEn} (${vocab.translationRu})
            Synonyms: ${vocab.synonyms.joinToString(", ")}
            Exercise Mode: ${mode.name} (${mode.titleRu})
            Challenge Prompt given to student: "$challengePrompt"
            Student's spoken answer (STT hint): "$userSpokenText"
            
            Evaluate if the student accurately expressed the meaning and properly used "${vocab.expression}" in natural English. Also evaluate their pronunciation/intonation from the audio if attached.
            Return ONLY valid JSON:
            {
              "passed": true,
              "score": 90,
              "recognizedSpeech": "Transcript of what the student said",
              "usedTargetExpression": true,
              "feedbackRu": "Подробный комментарий на русском языке: насколько точно построена фраза, грамматика и уместность использования выражения",
              "nativePolishedVersion": "Ideal natural English sentence using '${vocab.expression}'",
              "pronunciationTip": "Совет по произношению и интонации этой фразы на русском"
            }
        """.trimIndent()

        runCatching {
            val rawJson = callGeminiGenerateContent(
                apiKey = apiKey,
                modelId = modelId,
                promptText = prompt,
                audioFile = audioFile
            )
            json.decodeFromString<SrsEvaluationResult>(cleanJsonFence(rawJson))
        }.getOrElse {
            buildSimulatedSrsEvaluation(vocab, mode, userSpokenText)
        }
    }

    /**
     * Uses Gemini to turn scraped BBC page text into a structured Lesson with vocabulary & synonyms.
     */
    suspend fun extractLessonFromScrapedPage(
        apiKey: String,
        modelId: String = FluencyRepository.DEFAULT_GEMINI_MODEL,
        scraped: BbcLessonParser.ScrapedBbcPage
    ): Lesson = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext BbcLessonParser.buildFallbackLessonFromScraped(scraped)
        }

        val prompt = """
            Extract a structured English conversation lesson from this BBC Learning English (6 Minute English) page content.
            URL: ${scraped.url}
            Page Title: ${scraped.title}
            Content:
            ${scraped.rawText.take(8000)}
            
            Return ONLY valid JSON with this structure:
            {
              "title": "Episode title",
              "topicSummary": "2-3 sentence summary in English + brief Russian context",
              "discussionQuestions": ["Question 1 inviting target vocabulary use", "Question 2", "Question 3"],
              "vocabulary": [
                {
                  "expression": "exact phrase or idiom from the episode",
                  "definitionEn": "clear English definition",
                  "translationRu": "точный перевод на русский",
                  "exampleSentence": "natural example sentence from or inspired by the episode",
                  "synonyms": ["synonym 1", "paraphrase 2"]
                }
              ]
            }
        """.trimIndent()

        runCatching {
            val rawJson = callGeminiGenerateContent(apiKey, modelId, prompt, audioFile = null)
            val parsed = json.decodeFromString<ExtractedLessonSchema>(cleanJsonFence(rawJson))
            val lessonId = "bbc_" + UUID.randomUUID().toString().take(8)
            Lesson(
                id = lessonId,
                title = parsed.title,
                subtitle = "BBC Learning English • 6 Minute English",
                sourceUrl = scraped.url,
                dateLabel = "Импортирован по ссылке",
                topicSummary = parsed.topicSummary,
                discussionQuestions = parsed.discussionQuestions,
                transcriptExcerpt = scraped.rawText.take(2500),
                vocabulary = parsed.vocabulary.mapIndexed { index, v ->
                    VocabularyItem(
                        id = "${lessonId}_v$index",
                        lessonId = lessonId,
                        expression = v.expression,
                        definitionEn = v.definitionEn,
                        translationRu = v.translationRu,
                        exampleSentence = v.exampleSentence,
                        synonyms = v.synonyms
                    )
                }
            )
        }.getOrElse {
            BbcLessonParser.buildFallbackLessonFromScraped(scraped)
        }
    }

    private fun callGeminiGenerateContent(
        apiKey: String,
        modelId: String,
        promptText: String,
        audioFile: File?
    ): String {
        val cleanModel = modelId.trim().ifBlank { FluencyRepository.DEFAULT_GEMINI_MODEL }
        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=${apiKey.trim()}"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        val partsArray = buildJsonArray {
            add(buildJsonObject { put("text", promptText) })
            if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                val bytes = audioFile.readBytes()
                val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)
                add(
                    buildJsonObject {
                        put(
                            "inline_data",
                            buildJsonObject {
                                put("mime_type", "audio/mp4")
                                put("data", base64Audio)
                            }
                        )
                    }
                )
            }
        }

        val payload = buildJsonObject {
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("parts", partsArray)
                        }
                    )
                }
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("responseMimeType", "application/json")
                }
            )
        }

        conn.outputStream.use { os ->
            os.write(payload.toString().toByteArray(Charsets.UTF_8))
        }

        val status = conn.responseCode
        val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        if (status !in 200..299) {
            throw IllegalStateException("Gemini API HTTP $status: $body")
        }

        val root = json.parseToJsonElement(body).jsonObject
        val text = root["candidates"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")
            ?.jsonObject?.get("parts")
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")
            ?.jsonPrimitive?.content
            ?: throw IllegalStateException("Empty Gemini response")

        return text
    }

    private fun cleanJsonFence(raw: String): String {
        return raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    /**
     * Built-in intelligent local analyzer when running without an API key so the user can test
     * every Push-to-Talk flow, target vocabulary tracking, grammar/native rephrasing, and SRS drills immediately.
     */
    private fun buildSimulatedTutorResponse(
        lesson: Lesson,
        history: List<ChatMessage>,
        userSpeech: String
    ): TutorTurnResponse {
        val cleanSpeech = userSpeech.trim().ifBlank {
            "I think this topic is really interesting, especially when we think about how it affects our daily routine."
        }
        val lowerSpeech = cleanSpeech.lowercase()

        val matchedVocab = lesson.vocabulary.filter { vocab ->
            lowerSpeech.contains(vocab.expression.lowercase())
        }.map { it.expression }

        val unusedVocab = lesson.vocabulary.firstOrNull { vocab ->
            !matchedVocab.contains(vocab.expression)
        } ?: lesson.vocabulary.first()

        val nextQuestionIndex = (history.count { it.sender == MessageSender.USER }) % lesson.discussionQuestions.size
        val nextQuestion = lesson.discussionQuestions[nextQuestionIndex]

        val score = if (matchedVocab.isNotEmpty()) 92 else 78

        val reply = if (matchedVocab.isNotEmpty()) {
            "Spot on! I loved how naturally you used '${matchedVocab.first()}'. Building on that idea — $nextQuestion Try working '${unusedVocab.expression}' into your next answer!"
        } else {
            "That's a very fair point! To make it sound even more like a native speaker from this BBC episode, you could use '${unusedVocab.expression}' (${unusedVocab.definitionEn}). First, try repeating after me: \"${unusedVocab.exampleSentence}\" — or go ahead and answer: $nextQuestion"
        }

        val polished = if (matchedVocab.isNotEmpty()) {
            "To be honest, $cleanSpeech — and in the long run, that makes a huge difference."
        } else {
            "$cleanSpeech — in fact, ${unusedVocab.exampleSentence.replaceFirstChar { it.lowercase() }}"
        }

        val analysis = SpeechAnalysis(
            userTranscript = cleanSpeech,
            overallScore = score,
            targetVocabUsed = matchedVocab,
            targetVocabSuggestion = if (matchedVocab.isEmpty()) {
                "Попробуйте ввернуть фразу «${unusedVocab.expression}» (${unusedVocab.translationRu}): например, «${unusedVocab.exampleSentence}»."
            } else {
                "Отлично! Вы активно использовали целевую лексику (${matchedVocab.joinToString()}). В следующей реплике попробуйте «${unusedVocab.expression}»."
            },
            grammarFeedback = "Грамматическая основа верная. Обратите внимание на связки идей: вместо коротких простых предложений соединяйте мысли вводными конструкциями (To be fair..., What strikes me is that...).",
            correctedSentence = cleanSpeech.replaceFirstChar { it.uppercase() }.let {
                if (it.endsWith(".") || it.endsWith("!") || it.endsWith("?")) it else "$it."
            },
            nativeAlternative = polished,
            pronunciationScore = 86,
            pronunciationNotes = "Следите за связыванием (linking) на стыке согласной и гласной в беглой речи и не оглушайте звонкие согласные на конце слов (особенно -d, -z, -v).",
            intonationNotes = "В развернутых ответах делайте плавное понижение тона (falling tone) в конце ключевой мысли, а на вводных фразах — легкий восходящий тон.",
            repeatPrompt = if (matchedVocab.isEmpty()) unusedVocab.exampleSentence else null
        )

        return TutorTurnResponse(
            tutorReplyEn = reply,
            analysis = analysis
        )
    }

    private fun buildSimulatedSrsEvaluation(
        vocab: VocabularyItem,
        mode: SrsExerciseMode,
        userSpokenText: String
    ): SrsEvaluationResult {
        val text = userSpokenText.trim().ifBlank { vocab.exampleSentence }
        val usedTarget = text.lowercase().contains(vocab.expression.lowercase())
        val passed = usedTarget || text.split(" ").size >= 4
        val score = if (usedTarget) 94 else 68

        val feedback = if (usedTarget) {
            "Отлично! Вы правильно встроили выражение «${vocab.expression}» (${vocab.translationRu}) в контекст предложения в режиме «${mode.titleRu}»."
        } else {
            "Смысл передан близко, но вы не использовали целевое выражение «${vocab.expression}» (${vocab.translationRu}). Попробуйте произнести фразу еще раз, обязательно включив это выражение!"
        }

        return SrsEvaluationResult(
            passed = passed,
            score = score,
            recognizedSpeech = text,
            usedTargetExpression = usedTarget,
            feedbackRu = feedback,
            nativePolishedVersion = vocab.exampleSentence,
            pronunciationTip = "Выделяйте голосом ударное слово внутри чанка «${vocab.expression}», а служебные предлоги и артикли произносите слитно в слабой позиции (weak form)."
        )
    }
}
