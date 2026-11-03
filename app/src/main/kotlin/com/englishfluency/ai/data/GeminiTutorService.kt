package com.englishfluency.ai.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
        val transcriptExcerpt: String = "",
        val discussionQuestions: List<String>,
        val vocabulary: List<ExtractedVocabSchema>
    )

    @Serializable
    private data class ExtractedVocabSchema(
        val expression: String,
        val definitionEn: String,
        val translationRu: String,
        val exampleSentence: String,
        val synonyms: List<String> = emptyList()
    )

    @Serializable
    private data class GeneratedSrsChallengeSchema(
        val promptText: String,
        val hintText: String = "",
        val sampleIdealAnswer: String = ""
    )

    private fun requireApiKey(apiKey: String): String {
        val clean = apiKey.trim()
        if (clean.isBlank()) {
            throw IllegalStateException("Укажите ваш Gemini API Key на вкладке «Настройки», чтобы ИИ мог работать.")
        }
        return clean
    }

    /**
     * Dynamically fetches the list of available Gemini models directly from Google's API
     * (GET https://generativelanguage.googleapis.com/v1beta/models?key=...).
     */
    suspend fun fetchAvailableModels(apiKey: String): List<GeminiModelInfo> = withContext(Dispatchers.IO) {
        val key = requireApiKey(apiKey)
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models?key=$key&pageSize=100"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
        }

        val status = conn.responseCode
        val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        if (status !in 200..299) {
            throw IllegalStateException("Ошибка получения списка моделей HTTP $status: $body")
        }

        val root = json.parseToJsonElement(body).jsonObject
        val modelsArray = root["models"]?.jsonArray ?: return@withContext emptyList()

        val result = mutableListOf<GeminiModelInfo>()
        for (elem in modelsArray) {
            val obj = elem.jsonObject
            val rawName = obj["name"]?.jsonPrimitive?.content.orEmpty()
            val id = rawName.removePrefix("models/")
            if (!id.startsWith("gemini-")) continue

            // Filter out specialized non-multimodal-chat models
            val lower = id.lowercase()
            if (lower.contains("embedding") ||
                lower.contains("tts") ||
                lower.contains("image") ||
                lower.contains("vision") ||
                lower.contains("robotics") ||
                lower.contains("computer-use") ||
                lower.contains("aqa")
            ) {
                continue
            }

            val methods = obj["supportedGenerationMethods"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content }
                .orEmpty()
            if (!methods.contains("generateContent")) continue

            val displayName = obj["displayName"]?.jsonPrimitive?.content.orEmpty().ifBlank { id }
            val description = obj["description"]?.jsonPrimitive?.content.orEmpty()
            result.add(
                GeminiModelInfo(
                    id = id,
                    displayName = displayName,
                    description = description
                )
            )
        }

        result.sortedWith(
            compareByDescending<GeminiModelInfo> { it.id.contains("gemini-3") }
                .thenByDescending { it.id.contains("gemini-2.5") }
                .thenByDescending { it.id.contains("flash") }
                .thenByDescending { it.id }
        )
    }

    /**
     * Dynamically generates the opening welcome message & first question for a lesson via Gemini.
     */
    suspend fun generateOpeningQuestion(
        apiKey: String,
        modelId: String = FluencyRepository.DEFAULT_GEMINI_MODEL,
        lesson: Lesson
    ): String = withContext(Dispatchers.IO) {
        val key = requireApiKey(apiKey)
        val vocabList = lesson.vocabulary.joinToString(", ") { "\"${it.expression}\"" }
        val prompt = """
            You are a warm British English tutor starting a conversation about the BBC 6 Minute English lesson "${lesson.title}".
            Topic summary: ${lesson.topicSummary}
            Target vocabulary from this lesson: $vocabList
            
            Write a brief, natural opening greeting (2-3 sentences in English) inviting the student to discuss this topic and encouraging them to use 1-2 expressions from the target vocabulary list in their answer. End with an engaging open question.
            Return ONLY valid JSON:
            { "openingMessage": "Your greeting and first question in English" }
        """.trimIndent()

        val rawJson = callGeminiGenerateContent(key, modelId, prompt, audioFile = null)
        val obj = json.parseToJsonElement(cleanJsonFence(rawJson)).jsonObject
        obj["openingMessage"]?.jsonPrimitive?.content ?: lesson.discussionQuestions.firstOrNull().orEmpty()
    }

    /**
     * Dynamically generates a realistic, fun roleplay scenario based on the lesson's topic and target idioms.
     */
    suspend fun generateRoleplayScenario(
        apiKey: String,
        modelId: String = FluencyRepository.DEFAULT_GEMINI_MODEL,
        lesson: Lesson
    ): RoleplayScenario = withContext(Dispatchers.IO) {
        val key = requireApiKey(apiKey)
        val idiomsList = lesson.vocabulary.joinToString(", ") { "\"${it.expression}\"" }
        val prompt = """
            You are creating an interactive British English conversational roleplay simulation based on this lesson:
            Title: "${lesson.title}"
            Topic summary: ${lesson.topicSummary}
            Available vocabulary: $idiomsList
            
            Design a realistic, engaging everyday or workplace scenario (e.g., job interview, friendly debate at a London pub, team standup, negotiating with a colleague, dinner party) where the student and the AI character talk back and forth.
            Select 3-5 of the best target expressions from the lesson that naturally fit this scenario as the student's secret mission checklist.
            
            Return ONLY valid JSON matching this exact structure:
            {
              "title": "Short creative title of the roleplay (e.g., London Cafe Debate: The Future of Remote Work)",
              "aiCharacterName": "Oliver (BBC Colleague)",
              "aiCharacterRole": "A slightly skeptical but friendly British colleague discussing the topic",
              "userRole": "Your Role: Senior developer pitching your viewpoint",
              "situationRu": "Описание ситуации и контекста на русском языке в 2 предложениях",
              "missionGoalRu": "Ваша цель в диалоге на русском: убедить собеседника и ненавязчиво ввернуть выражения из чек-листа",
              "initialAiLine": "Oliver's opening conversational line in English to start the dialogue immediately, setting up the scene and asking the student a direct question",
              "checklistExpressions": ["exact expression 1", "exact expression 2", "exact expression 3"]
            }
        """.trimIndent()

        val rawJson = callGeminiGenerateContent(key, modelId, prompt, audioFile = null)
        val parsed = json.decodeFromString<GeneratedRoleplaySchema>(cleanJsonFence(rawJson))
        val scenarioId = "rp_${UUID.randomUUID().toString().take(8)}"
        RoleplayScenario(
            id = scenarioId,
            lessonId = lesson.id,
            title = parsed.title,
            aiCharacterName = parsed.aiCharacterName,
            aiCharacterRole = parsed.aiCharacterRole,
            userRole = parsed.userRole,
            situationRu = parsed.situationRu,
            missionGoalRu = parsed.missionGoalRu,
            initialAiLine = parsed.initialAiLine,
            checklistExpressions = parsed.checklistExpressions.ifEmpty {
                lesson.vocabulary.take(4).map { it.expression }
            }
        )
    }

    @Serializable
    private data class GeneratedRoleplaySchema(
        val title: String,
        val aiCharacterName: String,
        val aiCharacterRole: String,
        val userRole: String,
        val situationRu: String,
        val missionGoalRu: String,
        val initialAiLine: String,
        val checklistExpressions: List<String> = emptyList()
    )

    /**
     * Processes a user's Push-to-Talk turn (raw audio file + optional STT hint) strictly via Gemini API.
     */
    suspend fun evaluateConversationTurn(
        apiKey: String,
        modelId: String = FluencyRepository.DEFAULT_GEMINI_MODEL,
        lesson: Lesson,
        history: List<ChatMessage>,
        userSpeechText: String,
        audioFile: File?,
        roleplayScenario: RoleplayScenario? = null
    ): TutorTurnResponse = withContext(Dispatchers.IO) {
        val key = requireApiKey(apiKey)

        val vocabListPrompt = if (roleplayScenario != null) {
            "SECRET CHECKLIST IDIOMS TO ENCOURAGE AND TRACK:\n" +
                roleplayScenario.checklistExpressions.joinToString("\n") { "- \"$it\"" }
        } else {
            lesson.vocabulary.joinToString("\n") {
                "- \"${it.expression}\" (${it.definitionEn} / ${it.translationRu}) | Synonyms: ${it.synonyms.joinToString(", ")}"
            }
        }

        val roleplayInstructions = if (roleplayScenario != null) {
            """
            ROLEPLAY MODE ACTIVATED:
            - You are playing the character: ${roleplayScenario.aiCharacterName} (${roleplayScenario.aiCharacterRole}).
            - The student is playing: ${roleplayScenario.userRole}.
            - Scenario & Context: ${roleplayScenario.situationRu}
            - Student's Mission Goal: ${roleplayScenario.missionGoalRu}
            - In your reply ("tutorReplyEn"), STAY FULLY IN CHARACTER as ${roleplayScenario.aiCharacterName}! React realistically to what the student said, keep the conversation moving forward, debate or cooperate according to your role, and ask a follow-up question to keep the roleplay lively.
            """.trimIndent()
        } else {
            "STANDARD TUTOR COACHING MODE: Engage the student warmly in discussing the lesson topic."
        }

        val historyTranscript = history.takeLast(10).joinToString("\n") { msg ->
            val role = if (msg.sender == MessageSender.TUTOR) {
                roleplayScenario?.aiCharacterName ?: "TUTOR"
            } else {
                "STUDENT"
            }
            "$role: ${msg.text}"
        }

        val systemInstruction = """
            You are an expert British English conversational coach and actor leading a simulation based on a BBC Learning English (6 Minute English) episode.
            
            LESSON TITLE: ${lesson.title}
            TOPIC SUMMARY: ${lesson.topicSummary}
            $roleplayInstructions
            
            TARGET VOCABULARY / CHECKLIST:
            $vocabListPrompt
            
            YOUR GOALS:
            1. In "tutorReplyEn", give a natural, authentic response in character (2-3 sentences max) advancing the conversation.
            2. Evaluate every student turn across 4 dimensions:
               - Target vocabulary usage (which target/checklist expressions they used, or which specific expression from the checklist fits their thought right now).
               - Grammar & accuracy (concise explanation in Russian + corrected English sentence).
               - Native phrasing / idioms (how an educated native speaker would naturally phrase the student's exact idea).
               - Pronunciation & intonation (listen carefully to the attached audio if present, or analyze phonetic challenges of the words they used: word stress, linking, weak forms, rising/falling intonation, in Russian, plus word-by-word IPA breakdown).
            3. If the student made a notable mistake or missed a great opportunity to use a target phrase, set "repeatPrompt" to a short natural sentence containing that target phrase and ask them in your reply to try saying it out loud, or ask the next engaging follow-up question.
            4. If the student made a grammar/collocation mistake, Russian calque, or awkward phrasing, populate "personalMistakeToLearn" with the natural English expression/construction they should practice in Spaced Repetition (SRS); otherwise set "personalMistakeToLearn" to null.
            
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
                "wordBreakdown": [
                  {
                    "word": "word from student's transcript",
                    "status": "GOOD or WARN or NEEDS_WORK",
                    "ipa": "/fəˈnetɪk/",
                    "tipRu": "Краткий совет по произношению этого слова на русском (ударение, звук, связка)"
                  }
                ],
                "repeatPrompt": "Optional short English sentence for the student to repeat out loud, or null",
                "personalMistakeToLearn": {
                  "expression": "The exact natural English collocation, phrasal verb, or grammar pattern the student needed here",
                  "definitionEn": "Brief explanation of why native speakers use this construction instead of the student's phrasing",
                  "translationRu": "Точный перевод на русский язык",
                  "exampleSentence": "Corrected natural sentence using this expression",
                  "synonyms": ["Alternative natural phrasing 1", "Alternative 2"],
                  "userOriginalMistake": "The exact awkward/incorrect part the student said"
                }
              }
            }
        """.trimIndent()

        val userPrompt = """
            CONVERSATION HISTORY SO FAR:
            $historyTranscript
            
            STUDENT'S NEW TURN (STT hint: "$userSpeechText"):
            Listen to the student's audio (if attached) and/or read their transcript, then respond with the JSON object.
        """.trimIndent()

        val rawJson = callGeminiGenerateContent(
            apiKey = key,
            modelId = modelId,
            promptText = "$systemInstruction\n\n$userPrompt",
            audioFile = audioFile
        )
        json.decodeFromString<TutorTurnResponse>(cleanJsonFence(rawJson))
    }

    /**
     * Dynamically generates a fresh, contextually natural SRS exercise prompt via Gemini for any vocabulary item & mode.
     * Nothing is hardcoded.
     */
    suspend fun generateSrsChallenge(
        apiKey: String,
        modelId: String = FluencyRepository.DEFAULT_GEMINI_MODEL,
        vocab: VocabularyItem,
        mode: SrsExerciseMode,
        lessonTitle: String = ""
    ): SrsChallenge = withContext(Dispatchers.IO) {
        val key = requireApiKey(apiKey)

        val modeInstructions = when (mode) {
            SrsExerciseMode.RU_TO_EN -> """
                Generate a natural, realistic Russian sentence (1 sentence) from everyday life or work that the student must translate into English out loud using the exact target English expression "${vocab.expression}" (${vocab.translationRu}).
                Make sure the Russian sentence naturally conveys the meaning of "${vocab.expression}" so it makes 100% sense in context.
                In "promptText", write the Russian instruction and the Russian sentence to translate.
            """.trimIndent()

            SrsExerciseMode.EN_TO_EN_PARAPHRASE -> """
                Generate a simple, plain English sentence (1 sentence) that expresses the meaning of "${vocab.expression}" using basic words or synonyms (${vocab.synonyms.joinToString(", ")}), WITHOUT using "${vocab.expression}" itself.
                Ask the student (in Russian + show the English sentence) to rephrase/paraphrase that English sentence out loud by replacing the simple wording with the target idiom/expression "${vocab.expression}".
            """.trimIndent()

            SrsExerciseMode.SITUATIONAL_QA -> """
                Generate a thought-provoking conversational question in English (with a brief Russian instruction) about a realistic life or work scenario that naturally invites the student to use "${vocab.expression}" in their spoken answer.
            """.trimIndent()
        }

        val prompt = """
            You are creating a dynamic Spaced Repetition (SRS) speaking drill for an English learner.
            Lesson context: "$lessonTitle"
            Target Expression: "${vocab.expression}"
            English Definition: "${vocab.definitionEn}"
            Russian Meaning: "${vocab.translationRu}"
            Example from lesson: "${vocab.exampleSentence}"
            Synonyms: ${vocab.synonyms.joinToString(", ")}
            
            TASK FOR MODE ${mode.name}:
            $modeInstructions
            
            Create a fresh, unique exercise every time. Return ONLY valid JSON:
            {
              "promptText": "The full exercise prompt to display to the student",
              "hintText": "Short helpful hint in Russian without giving away the full answer",
              "sampleIdealAnswer": "One complete, natural English sentence that solves this prompt using '${vocab.expression}'"
            }
        """.trimIndent()

        val rawJson = callGeminiGenerateContent(
            apiKey = key,
            modelId = modelId,
            promptText = prompt,
            audioFile = null
        )
        val parsed = json.decodeFromString<GeneratedSrsChallengeSchema>(cleanJsonFence(rawJson))
        SrsChallenge(
            vocabId = vocab.id,
            modeName = mode.name,
            promptText = parsed.promptText,
            hintText = parsed.hintText,
            sampleIdealAnswer = parsed.sampleIdealAnswer
        )
    }

    /**
     * Evaluates an active Spaced Repetition (SRS) oral exercise strictly via Gemini API.
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
        val key = requireApiKey(apiKey)

        val prompt = """
            You are evaluating an English learner's spoken answer in a Spaced Repetition active recall drill.
            Target Expression: "${vocab.expression}"
            Meaning: ${vocab.definitionEn} (${vocab.translationRu})
            Synonyms: ${vocab.synonyms.joinToString(", ")}
            Exercise Mode: ${mode.name} (${mode.titleRu})
            Challenge Prompt given to student: "$challengePrompt"
            Student's spoken answer (STT hint): "$userSpokenText"
            
            Listen to the attached audio (if present) and/or read the transcript. Evaluate if the student accurately expressed the meaning, used "${vocab.expression}" (with correct tense/prepositions/articles), and how natural their pronunciation & intonation sounded.
            Return ONLY valid JSON:
            {
              "passed": true,
              "score": 90,
              "recognizedSpeech": "Transcript of what the student said",
              "usedTargetExpression": true,
              "feedbackRu": "Подробный комментарий на русском языке: насколько точно построена фраза, грамматика и уместность использования выражения",
              "nativePolishedVersion": "Ideal natural English sentence solving this prompt using '${vocab.expression}'",
              "pronunciationTip": "Совет по произношению, ударениям и интонации этой фразы на русском"
            }
        """.trimIndent()

        val rawJson = callGeminiGenerateContent(
            apiKey = key,
            modelId = modelId,
            promptText = prompt,
            audioFile = audioFile
        )
        json.decodeFromString<SrsEvaluationResult>(cleanJsonFence(rawJson))
    }

    /**
     * Uses Gemini to turn scraped BBC page text into a structured Lesson with vocabulary, definitions,
     * Russian translations, synonyms, transcript lines for shadowing, and discussion questions.
     */
    suspend fun extractLessonFromScrapedPage(
        apiKey: String,
        modelId: String = FluencyRepository.DEFAULT_GEMINI_MODEL,
        scraped: BbcLessonParser.ScrapedBbcPage
    ): Lesson = withContext(Dispatchers.IO) {
        val key = requireApiKey(apiKey)

        val prompt = """
            Extract a comprehensive English conversation lesson from this BBC Learning English (6 Minute English) page content.
            URL: ${scraped.url}
            Page Title: ${scraped.title}
            Content:
            ${scraped.rawText.take(14000)}
            
            Extract ALL target vocabulary items taught in the episode (plus key idiomatic expressions used by the presenters), a clean excerpt of dialogue lines from the transcript (6-10 lines formatted as "Speaker: line" for shadowing practice), and 4 engaging discussion questions that invite the student to use the vocabulary.
            
            Return ONLY valid JSON with this structure:
            {
              "title": "Episode title",
              "topicSummary": "2-3 sentence summary in Russian & English explaining what the episode is about",
              "transcriptExcerpt": "Speaker 1: ...\nSpeaker 2: ...",
              "discussionQuestions": ["Question 1", "Question 2", "Question 3", "Question 4"],
              "vocabulary": [
                {
                  "expression": "exact phrase, collocation, or idiom from the episode",
                  "definitionEn": "clear English definition from the episode",
                  "translationRu": "точный естественный перевод на русский",
                  "exampleSentence": "natural example sentence from the episode",
                  "synonyms": ["English synonym 1", "English paraphrase 2"]
                }
              ]
            }
        """.trimIndent()

        val rawJson = callGeminiGenerateContent(key, modelId, prompt, audioFile = null)
        val parsed = json.decodeFromString<ExtractedLessonSchema>(cleanJsonFence(rawJson))
        val lessonId = "bbc_" + UUID.randomUUID().toString().take(8)
        Lesson(
            id = lessonId,
            title = parsed.title,
            subtitle = "BBC Learning English • 6 Minute English",
            sourceUrl = scraped.url,
            dateLabel = scraped.url.substringAfterLast('/'),
            topicSummary = parsed.topicSummary,
            discussionQuestions = parsed.discussionQuestions,
            transcriptExcerpt = parsed.transcriptExcerpt.ifBlank { scraped.rawText.take(2500) },
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
            connectTimeout = 25_000
            readTimeout = 45_000
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
            throw IllegalStateException("Gemini API ($cleanModel) HTTP $status: $body")
        }

        val root = json.parseToJsonElement(body).jsonObject
        val text = root["candidates"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")
            ?.jsonObject?.get("parts")
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")
            ?.jsonPrimitive?.content
            ?: throw IllegalStateException("Пустой ответ от модели $cleanModel")

        return text
    }

    private fun cleanJsonFence(raw: String): String {
        return raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}
