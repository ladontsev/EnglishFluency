package com.englishfluency.ai.data

object SampleBbcLessons {

    fun defaultLessons(): List<Lesson> = listOf(
        Lesson(
            id = "ep-261029",
            title = "How AI & Digital Habits Are Reshaping Our Brains",
            subtitle = "BBC Learning English • 6 Minute English (ep-261029)",
            sourceUrl = "https://www.bbc.co.uk/learningenglish/english/features/6-minute-english_2026/ep-261029",
            dateLabel = "29 Oct 2026 • ep-261029",
            topicSummary = "В этом выпуске ведущие обсуждают, как постоянный поток уведомлений, ИИ-ассистенты и короткие видео влияют на нашу память, концентрацию и способность глубоко размышлять.",
            discussionQuestions = listOf(
                "Do you ever feel that constant notifications make your attention span shorter, or is it a blessing in disguise?",
                "When you need to solve a difficult problem at work, do you rely on AI right away or rack your brains first?",
                "What are the pros and cons of outsourcing our everyday memory to smartphones in the long run?"
            ),
            transcriptExcerpt = """
                Neil: Hello and welcome to 6 Minute English, the programme where we discuss an interesting topic and teach some related English vocabulary.
                Beth: Today we're talking about how modern technology and AI assistants are changing the way we think and remember things. Does it free up our minds, or does it make us mentally lazy?
                Neil: When we don't have to remember phone numbers or directions, we might lose the habit of racking our brains.
                Beth: On the other hand, forgetting your phone at home can turn out to be a blessing in disguise because it gives your brain a proper rest.
                Neil: Exactly! Nowadays we have the whole world's knowledge right at our fingertips, which certainly gives us plenty of food for thought.
                Beth: And with enough daily practice, using these English expressions in conversation will soon become second nature!
            """.trimIndent(),
            vocabulary = listOf(
                VocabularyItem(
                    id = "ep261029_v1",
                    lessonId = "ep-261029",
                    expression = "rack your brains",
                    definitionEn = "to think very hard or for a long time in order to remember something or solve a problem",
                    translationRu = "ломать голову / напрягать память",
                    exampleSentence = "I've been racking my brains all morning trying to remember where I heard that phrase.",
                    synonyms = listOf("think really hard", "search your memory", "puzzle over something")
                ),
                VocabularyItem(
                    id = "ep261029_v2",
                    lessonId = "ep-261029",
                    expression = "a blessing in disguise",
                    definitionEn = "something that seems bad or unlucky at first, but results in something good happening later",
                    translationRu = "нет худа без добра / скрытое благо",
                    exampleSentence = "Forgetting my phone at home turned out to be a blessing in disguise because I finally read my book.",
                    synonyms = listOf("a hidden benefit", "something bad that turns out well", "silver lining")
                ),
                VocabularyItem(
                    id = "ep261029_v3",
                    lessonId = "ep-261029",
                    expression = "second nature",
                    definitionEn = "a habit or skill that you have practiced so much that you do it automatically without thinking",
                    translationRu = "вторая натура / доведенная до автоматизма привычка",
                    exampleSentence = "After a few weeks of daily voice practice, using these English idioms will become second nature.",
                    synonyms = listOf("automatic habit", "instinctive behavior", "muscle memory")
                ),
                VocabularyItem(
                    id = "ep261029_v4",
                    lessonId = "ep-261029",
                    expression = "food for thought",
                    definitionEn = "something that deserves serious consideration and makes you think carefully",
                    translationRu = "пища для размышлений",
                    exampleSentence = "The study about screen time and sleep quality certainly gives us plenty of food for thought.",
                    synonyms = listOf("something to seriously consider", "a thought-provoking idea")
                ),
                VocabularyItem(
                    id = "ep261029_v5",
                    lessonId = "ep-261029",
                    expression = "at your fingertips",
                    definitionEn = "readily available and easy to access immediately",
                    translationRu = "под рукой / в мгновенном доступе",
                    exampleSentence = "Nowadays we have the whole world's knowledge right at our fingertips.",
                    synonyms = listOf("readily available", "within easy reach", "instantly accessible")
                )
            )
        ),
        Lesson(
            id = "bbc-small-talk-work",
            title = "The Art of Effortless Small Talk & Workplace Idioms",
            subtitle = "BBC Learning English • Everyday & Business English",
            sourceUrl = "https://www.bbc.co.uk/learningenglish/english/features/6-minute-english",
            dateLabel = "Workplace Fluency",
            topicSummary = "Практикум по естественному началу беседы перед созвоном и использованию живых разговорных идиом (touch base, play it by ear, call it a day).",
            discussionQuestions = listOf(
                "When a project plan suddenly changes, do you prefer a strict backup plan or do you play it by ear?",
                "How do you usually decide when it's time to wrap things up and call it a day?",
                "How often do you touch base with your teammates during a busy week?"
            ),
            transcriptExcerpt = """
                Pippa: Starting a conversation before a meeting starts can feel awkward in a second language.
                Georgie: Exactly! Instead of formal textbook sentences, native speakers rely on short idiomatic chunks like 'let's touch base tomorrow'.
                Pippa: And if the schedule changes unexpectedly, you can always say 'let's play it by ear' and see how things go.
                Georgie: Before starting a big task, make sure everyone is on the same page so there are no surprises later.
                Pippa: And when you've been working hard since morning, don't be afraid to wrap things up and call it a day!
            """.trimIndent(),
            vocabulary = listOf(
                VocabularyItem(
                    id = "work_v1",
                    lessonId = "bbc-small-talk-work",
                    expression = "play it by ear",
                    definitionEn = "to decide how to deal with a situation as it develops, rather than acting according to a fixed plan",
                    translationRu = "действовать по обстоятельствам / ориентироваться по ходу дела",
                    exampleSentence = "We don't know how long the meeting will take, so let's just play it by ear.",
                    synonyms = listOf("improvise as we go", "decide on the spot", "see how things go")
                ),
                VocabularyItem(
                    id = "work_v2",
                    lessonId = "bbc-small-talk-work",
                    expression = "touch base",
                    definitionEn = "to briefly make or renew contact with someone to update each other",
                    translationRu = "коротко созвониться / сверить часы / выйти на связь",
                    exampleSentence = "I just wanted to touch base with you before we present the new feature on Friday.",
                    synonyms = listOf("check in briefly", "catch up quickly", "sync up")
                ),
                VocabularyItem(
                    id = "work_v3",
                    lessonId = "bbc-small-talk-work",
                    expression = "call it a day",
                    definitionEn = "to decide to stop working on something for the rest of the day",
                    translationRu = "закругляться на сегодня / закончить работу",
                    exampleSentence = "We've been debugging this since morning — let's call it a day and look at it fresh tomorrow.",
                    synonyms = listOf("stop working for today", "finish up for the evening", "wrap up")
                ),
                VocabularyItem(
                    id = "work_v4",
                    lessonId = "bbc-small-talk-work",
                    expression = "on the same page",
                    definitionEn = "in agreement and sharing the same understanding about what is happening or planned",
                    translationRu = "на одной волне / иметь общее понимание задачи",
                    exampleSentence = "Let's review the main goals so everyone is on the same page before we start.",
                    synonyms = listOf("in full agreement", "aligned on the plan", "sharing the same understanding")
                )
            )
        )
    )
}
