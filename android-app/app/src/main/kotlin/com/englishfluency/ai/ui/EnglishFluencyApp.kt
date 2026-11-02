package com.englishfluency.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.englishfluency.ai.data.ChatMessage
import com.englishfluency.ai.data.Lesson
import com.englishfluency.ai.data.MessageSender
import com.englishfluency.ai.data.SpeechAnalysis
import com.englishfluency.ai.data.SrsExerciseMode
import com.englishfluency.ai.data.VocabularyItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnglishFluencyApp(vm: FluencyViewModel = viewModel()) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val lessons by vm.lessons.collectAsState()
    val selectedLessonId by vm.selectedLessonId.collectAsState()
    val currentLesson = lessons.find { it.id == selectedLessonId } ?: lessons.firstOrNull()
    val allVocab = lessons.flatMap { it.vocabulary }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (selectedTab) {
                                0 -> "BBC Learning English • Уроки"
                                1 -> currentLesson?.title ?: "Push-to-Talk Практика"
                                2 -> "SRS Тренажер (RU↔EN / Синонимы)"
                                else -> "Настройки ИИ и Прогресс"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (selectedTab) {
                                0 -> "Импорт по ссылке и целевая лексика"
                                1 -> "Голосовой диалог + разбор речи, грамматики и интонации"
                                2 -> "Активное интервальное повторение выражений вслух"
                                else -> "Gemini Multimodal API & Статистика"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = "Уроки") },
                    label = { Text("Уроки BBC") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = "Диалог") },
                    label = { Text("Push-to-Talk") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Box {
                            Icon(Icons.Default.Psychology, contentDescription = "SRS")
                            if (allVocab.isNotEmpty()) {
                                Badge(modifier = Modifier.align(Alignment.TopEnd)) {
                                    Text("${allVocab.size}")
                                }
                            }
                        }
                    },
                    label = { Text("Тренажер") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Настройки") },
                    label = { Text("ИИ Ключ") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> LessonsHubTab(
                    vm = vm,
                    onStartPractice = { lesson ->
                        vm.selectLesson(lesson.id)
                        selectedTab = 1
                    },
                    onOpenSrsForVocab = { vocab ->
                        vm.selectSrsVocab(vocab.id)
                        selectedTab = 2
                    }
                )
                1 -> PushToTalkCoachTab(vm = vm)
                2 -> SrsActiveTrainerTab(vm = vm)
                3 -> SettingsAndStatsTab(vm = vm)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LessonsHubTab(
    vm: FluencyViewModel,
    onStartPractice: (Lesson) -> Unit,
    onOpenSrsForVocab: (VocabularyItem) -> Unit
) {
    val lessons by vm.lessons.collectAsState()
    val isLoadingUrl by vm.isLoadingUrl.collectAsState()
    val urlStatus by vm.urlImportStatus.collectAsState()
    var bbcUrlInput by rememberSaveable {
        mutableStateOf("https://www.bbc.co.uk/learningenglish/english/features/6-minute-english_2026/ep-261029")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AddLink,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Импорт урока BBC 6 Minute English по ссылке",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Вставьте ссылку на любой выпуск BBC Learning English: приложение скачает транскрипт, выделит ключевые идиомы, синонимы и вопросы для устной беседы.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = bbcUrlInput,
                        onValueChange = {
                            bbcUrlInput = it
                            vm.clearUrlStatus()
                        },
                        label = { Text("URL урока BBC Learning English") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoadingUrl) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Разбор страницы...", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Text(
                                text = urlStatus ?: "Готово к загрузке",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            onClick = {
                                vm.importBbcLessonFromUrl(bbcUrlInput) { imported ->
                                    onStartPractice(imported)
                                }
                            },
                            enabled = !isLoadingUrl && bbcUrlInput.isNotBlank()
                        ) {
                            Text("Загрузить и начать")
                        }
                    }
                }
            }
        }

        items(lessons, key = { it.id }) { lesson ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = lesson.dateLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = lesson.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = lesson.topicSummary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Целевые выражения урока (${lesson.vocabulary.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        lesson.vocabulary.forEach { vocab ->
                            AssistChip(
                                onClick = { onOpenSrsForVocab(vocab) },
                                label = {
                                    Text("${vocab.expression} (${vocab.masteryLevel}/5)")
                                },
                                leadingIcon = {
                                    if (vocab.masteryLevel > 0) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onStartPractice(lesson) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Начать голосовой разбор (Push-to-Talk)")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PushToTalkCoachTab(vm: FluencyViewModel) {
    val context = LocalContext.current
    val lessons by vm.lessons.collectAsState()
    val selectedLessonId by vm.selectedLessonId.collectAsState()
    val lesson = lessons.find { it.id == selectedLessonId } ?: lessons.firstOrNull() ?: return
    val conversations by vm.repository.conversations.collectAsState()
    val messages = conversations[lesson.id] ?: vm.repository.getConversationForLesson(lesson.id)

    val isRecording by vm.voiceController.isRecording.collectAsState()
    val livePartialTranscript by vm.voiceController.livePartialTranscript.collectAsState()
    val isProcessingTurn by vm.isProcessingTurn.collectAsState()

    var draftInput by rememberSaveable { mutableStateOf("") }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            vm.voiceController.startPushToTalkRecording { recognized ->
                draftInput = recognized
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Target vocabulary bar at top of conversation
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Слова урока (нажмите, чтобы прослушать пример):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = { vm.resetCurrentConversation() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Сбросить диалог",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    lesson.vocabulary.forEach { vocab ->
                        val used = vocab.timesUsedInConversation > 0
                        AssistChip(
                            onClick = {
                                vm.speakText("${vocab.expression}. ${vocab.exampleSentence}")
                            },
                            label = {
                                Text(
                                    text = "${vocab.expression} — ${vocab.translationRu}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (used) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        )
                    }
                }
            }
        }

        // Chat & Feedback cards list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                if (msg.sender == MessageSender.TUTOR) {
                    TutorMessageBubble(
                        message = msg,
                        onSpeak = { vm.speakText(msg.text) }
                    )
                } else {
                    UserMessageWithAnalysisCard(
                        message = msg,
                        onSpeakText = { vm.speakText(it) },
                        onPracticeRepeatPrompt = { repeatPhrase ->
                            draftInput = repeatPhrase
                            vm.speakText(repeatPhrase)
                        }
                    )
                }
            }

            if (isProcessingTurn) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "ИИ-тьютор анализирует ваш ответ (грамматика, нейтив-фразы, произношение, интонация)...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Push-to-Talk Control Deck
        Surface(
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                AnimatedVisibility(visible = isRecording) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Идет запись голоса (Push-to-Talk)...",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = livePartialTranscript.ifBlank {
                                        "Говорите по-английски, используя выражения из урока. Нажмите СТОП для отправки."
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Big Push-to-Talk Microphone Button
                    Button(
                        onClick = {
                            if (!isRecording) {
                                if (hasMicPermission) {
                                    vm.voiceController.startPushToTalkRecording { recognized ->
                                        draftInput = recognized
                                    }
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                val (audioFile, transcript) = vm.voiceController.stopPushToTalkRecording()
                                val finalSpeech = transcript.ifBlank { draftInput }
                                vm.submitUserTurn(
                                    spokenOrTypedText = finalSpeech,
                                    audioFile = audioFile
                                )
                                draftInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        ),
                        shape = CircleShape,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Push to Talk",
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    OutlinedTextField(
                        value = if (isRecording && livePartialTranscript.isNotBlank()) {
                            livePartialTranscript
                        } else {
                            draftInput
                        },
                        onValueChange = { draftInput = it },
                        placeholder = {
                            Text("Нажмите микрофон (Push-to-Talk) или введите ответ...")
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 3
                    )

                    IconButton(
                        onClick = {
                            if (isRecording) {
                                val (audioFile, transcript) = vm.voiceController.stopPushToTalkRecording()
                                val textToUse = transcript.ifBlank { draftInput }
                                vm.submitUserTurn(textToUse, audioFile)
                                draftInput = ""
                            } else if (draftInput.isNotBlank()) {
                                vm.submitUserTurn(draftInput, null)
                                draftInput = ""
                            }
                        },
                        enabled = !isProcessingTurn && (isRecording || draftInput.isNotBlank())
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Отправить",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorMessageBubble(
    message: ChatMessage,
    onSpeak: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(0.92f),
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BBC AI Tutor",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onSpeak, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Озвучить",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun UserMessageWithAnalysisCard(
    message: ChatMessage,
    onSpeakText: (String) -> Unit,
    onPracticeRepeatPrompt: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Ваш ответ:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        val analysis: SpeechAnalysis? = message.analysis
        if (analysis != null) {
            Spacer(Modifier.height(6.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(0.96f),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Разбор ответа • Общий балл: ${analysis.overallScore}/100",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Произношение: ${analysis.pronunciationScore}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    HorizontalDivider()

                    // 1. Target Vocab Usage
                    if (analysis.targetVocabUsed.isNotEmpty()) {
                        Text(
                            text = "✅ Использованы фразы урока: ${analysis.targetVocabUsed.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    if (analysis.targetVocabSuggestion.isNotBlank()) {
                        Text(
                            text = "🎯 Лексика урока: ${analysis.targetVocabSuggestion}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // 2. Native Rephrasing / Idioms
                    if (analysis.nativeAlternative.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSpeakText(analysis.nativeAlternative) }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🇬🇧 Как скажет носитель (Native Phrasing):",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "\"${analysis.nativeAlternative}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Прослушать",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // 3. Grammar Feedback
                    if (analysis.grammarFeedback.isNotBlank()) {
                        Text(
                            text = "📝 Грамматика: ${analysis.grammarFeedback}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // 4. Pronunciation & Intonation
                    if (analysis.pronunciationNotes.isNotBlank() || analysis.intonationNotes.isNotBlank()) {
                        Text(
                            text = "🎙 Произношение и интонация: ${analysis.pronunciationNotes} ${analysis.intonationNotes}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // 5. Repeat Prompt button
                    if (!analysis.repeatPrompt.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { onPracticeRepeatPrompt(analysis.repeatPrompt) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Повторить вслух: \"${analysis.repeatPrompt}\"",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SrsActiveTrainerTab(vm: FluencyViewModel) {
    val context = LocalContext.current
    val lessons by vm.lessons.collectAsState()
    val allVocab = lessons.flatMap { it.vocabulary }
    val currentVocabId by vm.currentSrsVocabId.collectAsState()
    val selectedMode by vm.selectedSrsMode.collectAsState()
    val lastResult by vm.lastSrsResult.collectAsState()
    val isEvaluating by vm.isEvaluatingSrs.collectAsState()

    val isRecording by vm.voiceController.isRecording.collectAsState()
    val livePartial by vm.voiceController.livePartialTranscript.collectAsState()

    val currentVocab = allVocab.find { it.id == currentVocabId } ?: allVocab.firstOrNull() ?: return
    var userAttemptText by rememberSaveable(currentVocab.id, selectedMode) { mutableStateOf("") }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            vm.voiceController.startPushToTalkRecording { recognized ->
                userAttemptText = recognized
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Mode Selector
        item {
            Text(
                text = "Выберите режим проверки:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SrsExerciseMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { vm.setSrsMode(mode) },
                        label = { Text(mode.titleRu) }
                    )
                }
            }
        }

        // Flashcard & Active Generation Challenge
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Уровень освоения: ${currentVocab.masteryLevel}/5 • Интервал: ${currentVocab.intervalDays} дн.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = currentVocab.expression,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        IconButton(onClick = { vm.speakText("${currentVocab.expression}. ${currentVocab.exampleSentence}") }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Озвучить")
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Значение (EN): ${currentVocab.definitionEn}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Перевод (RU): ${currentVocab.translationRu}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (currentVocab.synonyms.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Синонимы (EN → EN): ${currentVocab.synonyms.joinToString(" • ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Задание (${selectedMode.subtitleRu}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = vm.buildPromptForSrsItem(currentVocab, selectedMode),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = if (isRecording && livePartial.isNotBlank()) livePartial else userAttemptText,
                        onValueChange = { userAttemptText = it },
                        label = { Text("Ваш ответ (голосом через Push-to-Talk или текстом)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!isRecording) {
                                    if (hasMicPermission) {
                                        vm.voiceController.startPushToTalkRecording { recognized ->
                                            userAttemptText = recognized
                                        }
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    val (audioFile, transcript) = vm.voiceController.stopPushToTalkRecording()
                                    val finalSpeech = transcript.ifBlank { userAttemptText }
                                    vm.evaluateSrsResponse(currentVocab, finalSpeech, audioFile)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isRecording) "Стоп и проверить" else "Сказать вслух")
                        }

                        OutlinedButton(
                            onClick = {
                                vm.evaluateSrsResponse(currentVocab, userAttemptText, null)
                            },
                            enabled = !isEvaluating && userAttemptText.isNotBlank()
                        ) {
                            Text("Проверить")
                        }
                    }
                }
            }
        }

        // Result card
        if (lastResult != null) {
            val res = lastResult!!
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (res.passed) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (res.passed) "✅ Отлично усвоено! (${res.score}/100)" else "⚠️ Попробуйте еще раз (${res.score}/100)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = res.feedbackRu,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Эталонный вариант: \"${res.nativePolishedVersion}\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic
                        )
                        Text(
                            text = "🎙 Произношение: ${res.pronunciationTip}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { vm.nextSrsVocab() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Следующее выражение →")
                        }
                    }
                }
            }
        }

        // All saved expressions list
        item {
            Text(
                text = "Все сохраненные выражения (${allVocab.size}):",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        items(allVocab, key = { it.id }) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.selectSrsVocab(item.id) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.expression, fontWeight = FontWeight.Bold)
                        Text(
                            "${item.translationRu} • Синонимы: ${item.synonyms.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Ур. ${item.masteryLevel}/5",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LinearProgressIndicator(
                            progress = { item.masteryLevel / 5f },
                            modifier = Modifier
                                .width(64.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsAndStatsTab(vm: FluencyViewModel) {
    val apiKey by vm.apiKey.collectAsState()
    val lessons by vm.lessons.collectAsState()
    val allVocab = lessons.flatMap { it.vocabulary }
    var keyInput by rememberSaveable(apiKey) { mutableStateOf(apiKey) }
    var savedBanner by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ключ Google Gemini API (Multimodal Audio + Text)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "С ключом Gemini 2.5 Flash приложение отправляет вашу живую голосовую запись (.m4a) напрямую в мультимодальную модель для глубокого разбора произношения, интонации, грамматики и нейтив-фраз, а также автоматически парсит любые новые страницы BBC Learning English. Без ключа работает встроенный офлайн-тренажер.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = {
                            keyInput = it
                            savedBanner = false
                        },
                        label = { Text("Gemini API Key (AIza...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            vm.repository.updateApiKey(keyInput)
                            savedBanner = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сохранить API-ключ")
                    }
                    if (savedBanner) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "✅ Ключ сохранен локально на устройстве.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Статистика вашего прогресса",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("• Загружено уроков BBC Learning English: ${lessons.size}")
                    Text("• Всего выражений и идиом в базе SRS: ${allVocab.size}")
                    Text("• Выражений использовано в живой речи: ${allVocab.count { it.timesUsedInConversation > 0 }}")
                    Text("• Освоено на высоком уровне (3+ из 5): ${allVocab.count { it.masteryLevel >= 3 }}")
                }
            }
        }
    }
}
