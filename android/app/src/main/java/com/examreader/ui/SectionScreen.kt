package com.examreader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.examreader.data.AppSettings
import com.examreader.data.CParagraph
import com.examreader.data.DictEntryEntity
import com.examreader.data.SectionDetail
import com.examreader.data.SegCodec
import com.examreader.data.StudyStateEntity
import com.examreader.data.TranslateService
import com.examreader.ui.components.QuestionCard
import com.examreader.ui.components.TappableSegmentsText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 悬浮小岛里展示的查词结果 */
private data class WordInfo(
    val word: String,
    val phonetic: String = "",
    val partOfSpeech: String = "",
    val zh: String = "",
    val note: String = "",
    val loading: Boolean = false,
    val added: Boolean = false
)

/**
 * 篇目学习页：原文 / 题目 / 拓展。
 * 原文里**每个英文单词**都能点，点完底部浮出查词小岛（词典 → 机器翻译兜底）；
 * 译文默认不展开，顶栏总开关 + 每段「展开译文」按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionScreen(nav: NavController, paperId: String, sectionId: String) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val states by app.studyRepository.observeStates(paperId)
        .collectAsStateWithLifecycle(initialValue = emptyMap<String, StudyStateEntity>())
    val snackbar = remember { SnackbarHostState() }

    var detail by remember(sectionId) { mutableStateOf<SectionDetail?>(null) }
    var loading by remember(sectionId) { mutableStateOf(true) }
    var tab by remember(sectionId) { mutableIntStateOf(0) }
    var showZh by remember(sectionId) { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var wordInfo by remember { mutableStateOf<WordInfo?>(null) }
    // 查词缓存 + 请求序号：同一个词不重复请求；快速连点只采用最后一次结果
    val wordCache = remember { mutableStateMapOf<String, WordInfo>() }
    var wordReq by remember { mutableLongStateOf(0L) }

    LaunchedEffect(paperId, sectionId) {
        detail = app.examRepository.loadSection(paperId, sectionId)
        loading = false
    }

    fun lookupWord(word: String) {
        wordCache[word]?.let { cached ->
            wordInfo = cached
            return
        }
        val req = ++wordReq
        // 保留小岛，只把内容切成「查询中」，避免整个小岛消失再出现
        wordInfo = WordInfo(word = word, loading = true, note = "查询中…")
        scope.launch {
            val startedAt = System.currentTimeMillis()
            val entry = app.examRepository.lookup(paperId, word)
            val result = if (entry != null) {
                WordInfo(entry.word, entry.phonetic, entry.partOfSpeech, entry.zh, "词典收录")
            } else {
                val gloss = app.glossaryRepository.get(word)
                if (gloss != null) {
                    WordInfo(gloss.word, gloss.phonetic, gloss.partOfSpeech, gloss.zh, "词库收录")
                } else {
                    val translated = runCatching { TranslateService(settings).enToZh(word) }
                    if (translated.isSuccess) {
                        val zh = translated.getOrThrow()
                        // 首次查阅：机器翻译成功即自动存入词库（词库中已存在则跳过）
                        app.glossaryRepository.recordMachineLookup(word, zh)
                        WordInfo(word, zh = zh, note = "机器翻译 · 已存入词库")
                    } else {
                        WordInfo(word, note = translated.exceptionOrNull()?.message ?: "词典未收录")
                    }
                }
            }
            wordCache[word] = result
            // 至少停留 260ms：有「很短等待」的过渡感，不闪烁
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < 260) delay(260 - elapsed)
            if (req == wordReq) wordInfo = result
        }
    }

    fun addWord(info: WordInfo) {
        scope.launch {
            app.studyRepository.addWord(paperId, info.word, info.phonetic, info.partOfSpeech, info.zh)
            val updated = info.copy(added = true, note = "已加入生词本")
            wordCache[info.word] = updated
            if (wordInfo?.word == info.word) wordInfo = updated
        }
    }

    val d = detail

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        d?.title ?: "篇目",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showZh = !showZh }) {
                        Text(if (showZh) "收起译文" else "展开译文", fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (loading || d == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth > 900.dp
                if (wide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        PassagePane(
                            detail = d,
                            showZh = showZh,
                            onWordTap = ::lookupWord,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                        QuestionsPane(
                            detail = d,
                            paperId = paperId,
                            states = states,
                            snackbar = snackbar,
                            onWordTap = ::lookupWord,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                            listOf("原文", "题目", "拓展").forEachIndexed { index, title ->
                                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title, fontSize = 13.sp) })
                            }
                        }
                        when (tab) {
                            0 -> PassagePane(
                                detail = d,
                                showZh = showZh,
                                onWordTap = ::lookupWord,
                                modifier = Modifier.fillMaxSize()
                            )

                            1 -> QuestionsPane(
                                detail = d,
                                paperId = paperId,
                                states = states,
                                snackbar = snackbar,
                                onWordTap = ::lookupWord,
                                modifier = Modifier.fillMaxSize()
                            )

                            else -> ExtrasPane(detail = d, modifier = Modifier.fillMaxSize())
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(onClick = { sheet = true }) { Text("对照原文", fontSize = 12.sp) }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = wordInfo != null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 3 }
            ) {
                val info = wordInfo
                if (info != null) {
                    WordIsland(
                        info = info,
                        onAdd = { addWord(info) },
                        onClose = { wordInfo = null }
                    )
                }
            }
        }
    }

    if (sheet && d != null) {
        ModalBottomSheet(onDismissRequest = { sheet = false }) {
            Box(Modifier.fillMaxHeight(0.85f)) {
                PassagePane(
                    detail = d,
                    showZh = showZh,
                    onWordTap = ::lookupWord,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/* ------------------------------ 查词悬浮小岛 ------------------------------ */

@Composable
private fun WordIsland(
    info: WordInfo,
    onAdd: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 单词做内容切换动画：换词时淡入淡出，而不是整卡重建
                AnimatedContent(
                    targetState = info.word,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "word-title"
                ) { word -> Text(word, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                if (info.phonetic.isNotBlank()) {
                    Text(
                        "  " + info.phonetic,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                if (info.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else if (info.note.isNotBlank()) {
                    Text(info.note, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onClose) { Text("关闭", fontSize = 11.sp) }
            }

            AnimatedContent(
                targetState = info,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "word-body"
            ) { current ->
                Column {
                    if (current.loading) {
                        Text(
                            "正在查词典 / 翻译…",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        val body = buildString {
                            if (current.partOfSpeech.isNotBlank()) append(current.partOfSpeech).append("  ")
                            if (current.zh.isNotBlank()) append(current.zh)
                            else if (current.note.isNotBlank()) append(current.note)
                        }
                        Text(body, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                        Row(modifier = Modifier.padding(top = 4.dp)) {
                            if (current.zh.isNotBlank() && !current.added) {
                                TextButton(onClick = onAdd) { Text("加入生词本", fontSize = 12.sp) }
                            } else if (current.added) {
                                Text("已加入生词本", fontSize = 12.sp, color = OkGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------- 原文 ------------------------------- */

@Composable
private fun PassagePane(
    detail: SectionDetail,
    showZh: Boolean,
    onWordTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<DictEntryEntity>>(emptyList()) }
    var addedWord by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query, detail.paperId) {
        results = app.examRepository.prefixSearch(detail.paperId, query)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(detail.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (detail.passageTitle.isNotBlank()) {
                        Text(detail.passageTitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    if (detail.topic.isNotBlank()) {
                        Text(
                            detail.topic,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Text(
                        "点任意单词可查释义",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        item {
            WordLookupCard(
                query = query,
                onQueryChange = { query = it },
                results = results,
                addedWord = addedWord,
                onAdd = { entry ->
                    scope.launch {
                        app.studyRepository.addWord(detail.paperId, entry.word, entry.phonetic, entry.partOfSpeech, entry.zh)
                        addedWord = entry.word
                    }
                }
            )
        }
        items(detail.paragraphs, key = { it.index }) { para ->
            ParagraphCard(para = para, globalShowZh = showZh, onWordTap = onWordTap)
        }
        if (detail.vocab.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("本篇重点词汇 ${detail.vocab.size} 个", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "点词可查释义并加入生词本",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            detail.vocab.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { v ->
                                        AssistChip(
                                            onClick = {
                                                query = v.word
                                                scope.launch {
                                                    app.studyRepository.addWord(
                                                        detail.paperId,
                                                        v.word,
                                                        v.phonetic,
                                                        v.partOfSpeech,
                                                        v.zh
                                                    )
                                                    addedWord = v.word
                                                }
                                            },
                                            label = { Text("${v.word} ${v.partOfSpeech}", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParagraphCard(para: CParagraph, globalShowZh: Boolean, onWordTap: (String) -> Unit) {
    var localOpen by remember(para.index) { mutableStateOf(false) }
    val open = globalShowZh || localOpen
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (!para.heading.isNullOrBlank()) {
                Text(
                    para.heading ?: "",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            TappableSegmentsText(
                segs = para.en,
                onWordTap = onWordTap,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium
            )
            if (para.zh.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!globalShowZh) {
                        TextButton(
                            onClick = { localOpen = !localOpen },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                        ) {
                            Text(if (localOpen) "收起译文" else "展开译文", fontSize = 11.sp)
                        }
                    } else {
                        Text("译文", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (open) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            SegCodec.toPlain(para.zh),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WordLookupCard(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<DictEntryEntity>,
    addedWord: String?,
    onAdd: (DictEntryEntity) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("手动查词（英文）") },
                singleLine = true
            )
            results.take(6).forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.word + "  " + entry.phonetic, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "${entry.partOfSpeech} ${entry.zh}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onAdd(entry) }) {
                        Text(if (addedWord == entry.word) "已加入" else "＋生词本", fontSize = 11.sp)
                    }
                }
            }
            if (query.isNotBlank() && results.isEmpty()) {
                Text(
                    "词典没找到，点正文里的单词可以调用翻译。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/* ------------------------------- 题目 ------------------------------- */

@Composable
private fun QuestionsPane(
    detail: SectionDetail,
    paperId: String,
    states: Map<String, StudyStateEntity>,
    snackbar: SnackbarHostState,
    onWordTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(detail.questions, key = { it.id }) { q ->
            val state = states[q.id] ?: StudyStateEntity(paperId, q.id, null, "", false, 0L)
            QuestionCard(
                q = q,
                state = state,
                onPick = { letter -> scope.launch { app.studyRepository.setPicked(paperId, q.id, letter) } },
                onFill = { text -> scope.launch { app.studyRepository.setFilled(paperId, q.id, text) } },
                onToggleAnswer = {
                    scope.launch { app.studyRepository.setAnswerShown(paperId, q.id, !state.answerShown) }
                },
                onCollect = {
                    scope.launch {
                        val text = SegCodec.toPlain(q.stem).ifBlank { "第 ${q.no} 题" }
                        app.studyRepository.addSentence(paperId, q.id, text)
                        snackbar.showSnackbar("已收藏到收藏本")
                    }
                },
                onLookupWord = { word -> onWordTap(word) },
                onWordTap = onWordTap
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/* ------------------------------- 拓展 ------------------------------- */

@Composable
private fun ExtrasPane(detail: SectionDetail, modifier: Modifier = Modifier) {
    val app = rememberApp()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (detail.outline.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("语篇结构", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            detail.outline.forEach { node ->
                                val indent = when (node.level) {
                                    "l1" -> 0.dp
                                    "l2" -> 14.dp
                                    else -> 28.dp
                                }
                                Text(
                                    node.text,
                                    fontSize = if (node.level == "l1") 13.sp else 12.sp,
                                    fontWeight = if (node.level == "l1") FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (node.level == "l3") MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = indent, top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        if (detail.migrations.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("写作迁移", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        detail.migrations.forEach { m ->
                            Text(
                                m.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                            Text(m.detail, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (detail.mindmapFile != null) {
            item { MindmapCard(detail.mindmapFile) }
        }
        if (detail.outline.isEmpty() && detail.migrations.isEmpty() && detail.mindmapFile == null) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    Text("本篇没有拓展内容", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/* ------------------------------- 思维导图（可放大） ------------------------------- */

@Composable
private fun MindmapCard(file: java.io.File) {
    val app = rememberApp()
    var zoomOpen by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("思维导图", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { zoomOpen = true }) { Text("放大查看", fontSize = 12.sp) }
            }
            AsyncImage(
                model = file,
                imageLoader = app.imageLoader,
                contentDescription = "思维导图",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(top = 8.dp)
                    .clickable { zoomOpen = true }
            )
            Text(
                "点图可放大，双指缩放 / 拖动查看",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
    if (zoomOpen) {
        Dialog(
            onDismissRequest = { zoomOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                ZoomableImage(
                    model = file,
                    imageLoader = app.imageLoader,
                    modifier = Modifier.fillMaxSize()
                )
                TextButton(
                    onClick = { zoomOpen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) { Text("关闭", color = Color.White, fontSize = 14.sp) }
                Text(
                    "双指缩放 / 拖动查看",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(model: Any?, imageLoader: coil.ImageLoader, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = model,
        imageLoader = imageLoader,
        contentDescription = "思维导图",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset += pan
                }
            }
    )
}
