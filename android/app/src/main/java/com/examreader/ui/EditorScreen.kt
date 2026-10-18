package com.examreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.examreader.data.AiConfig
import com.examreader.data.AppSettings
import com.examreader.data.DraftGroup
import com.examreader.data.DraftMapper
import com.examreader.data.DraftModel
import com.examreader.data.DraftParagraph
import com.examreader.data.DraftQuestion
import com.examreader.data.CQuestion
import com.examreader.data.DeepSeekAnalyzer
import com.examreader.data.DraftSection
import com.examreader.data.DraftSolution
import com.examreader.data.DraftStore
import com.examreader.data.DraftValidator
import com.examreader.data.Inline
import com.examreader.data.TranslateService
import com.examreader.ui.nav.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 编辑器：只允许改「原文 / 讲解 / 翻译」三类内容（选项与标准答案只读）。
 * 保存时：校验 → compiled → 自动生成 .epak → 导入 SQLite → 跳转试卷目录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(nav: NavController, paperId: String) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val snackbar = remember { SnackbarHostState() }

    var draft by remember { mutableStateOf<DraftModel?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var errorDialog by remember { mutableStateOf<List<String>?>(null) }
    var autosavedAt by remember { mutableStateOf(0L) }
    var generating by remember { mutableStateOf(false) }
    var genProgress by remember { mutableStateOf("") }
    var conflicts by remember { mutableStateOf<List<AnswerConflict>?>(null) }
    var savedOk by remember { mutableStateOf(false) }

    LaunchedEffect(paperId) {
        val stored = DraftStore.draft
        draft = when {
            stored != null && (paperId == "-" || stored.editingPaperId == paperId) -> stored
            paperId != "-" -> app.examRepository.loadCompiled(paperId)?.let { (compiled, mindmaps) ->
                DraftMapper.fromCompiled(compiled, mindmaps).apply {
                    editingPaperId = paperId
                    sourceLabel = "应用内编辑"
                }
            }

            else -> app.draftRepository.load()?.let { snapshot ->
                DraftMapper.fromCompiled(snapshot.compiled, snapshot.mindmaps).apply {
                    sourceLabel = snapshot.sourceLabel
                }
            }
        }
        loading = false
    }

    // 每 10 秒自动保存一次草稿（覆盖写 drafts 表），进程被杀也能续上。
    // 只给「扫描/新建草稿」自动存，改已有试卷时不占用唯一草稿槽（避免覆盖未保存的扫描草稿）
    LaunchedEffect(draft) {
        val model = draft ?: return@LaunchedEffect
        if (model.editingPaperId != null) return@LaunchedEffect
        while (!savedOk) {
            delay(10_000)
            if (savedOk) break
            runCatching {
                val compiled = DraftMapper.toCompiled(model)
                val mindmaps = model.sections.mapNotNull { s -> s.mindmapSvg?.let { s.id to it } }.toMap()
                autosavedAt = app.draftRepository.save(compiled, mindmaps, model.sourceLabel)
            }
        }
    }

    // 离开编辑器时补存一次（同上：只保存扫描/新建草稿）
    DisposableEffect(draft) {
        onDispose {
            val model = draft
            if (model != null && !savedOk && model.editingPaperId == null) {
                runCatching {
                    val compiled = DraftMapper.toCompiled(model)
                    val mindmaps = model.sections.mapNotNull { s -> s.mindmapSvg?.let { s.id to it } }.toMap()
                    app.backgroundScope.launch {
                        app.draftRepository.save(compiled, mindmaps, model.sourceLabel)
                    }
                }
            }
        }
    }

    fun translate(text: String, key: String, onDone: (String) -> Unit) {
        if (text.isBlank()) {
            scope.launch { snackbar.showSnackbar("英文原文是空的，没法翻译") }
            return
        }
        scope.launch {
            busyKey = key
            runCatching { TranslateService(settings).enToZh(text) }
                .onSuccess { onDone(it) }
                .onFailure { snackbar.showSnackbar("翻译失败：${it.message}") }
            busyKey = null
        }
    }

    fun saveAs(model: DraftModel, stage: String) {
        if (model.id.isBlank()) {
            model.id = "exam-" + System.currentTimeMillis().toString().takeLast(8)
        }
        // 先校验再改阶段：校验失败时不能让草稿误标成「解析卷」
        val errors = DraftValidator.errors(model, stage)
        if (errors.isNotEmpty()) {
            errorDialog = errors
            return
        }
        model.stage = stage
        val warnings = DraftValidator.warnings(model)
        val compiled = DraftMapper.toCompiled(model)
        val mindmaps = model.sections.mapNotNull { s -> s.mindmapSvg?.let { s.id to it } }.toMap()
        scope.launch {
            saving = true
            val outcome = app.examRepository.importCompiled(compiled, mindmaps, model.sourceLabel)
            saving = false
            if (outcome.ok) {
                savedOk = true
                DraftStore.draft = null
                app.draftRepository.clear()
                val label = if (stage == "analyzed") "解析卷" else "扫描卷"
                snackbar.showSnackbar("$label 已保存" + if (warnings.isNotEmpty()) "（${warnings.size} 条提醒）" else "")
                nav.navigate(Routes.paper(outcome.paperId)) { popUpTo(Routes.LIBRARY) }
            } else {
                snackbar.showSnackbar("保存失败：${outcome.message}")
            }
        }
    }

    /**
     * 第二阶段：DeepSeek 生成「答案 + 译文 + 解析」。
     * 老师填过答案的题：AI 独立作答，不一致就提示「不符」；
     * 老师没填答案的题：直接采用 DeepSeek 的答案（默认由 AI 给答案）。
     */
    fun generateAnalysis(model: DraftModel) {
        val analyzer = DeepSeekAnalyzer(settings)
        if (!analyzer.ready) {
            scope.launch { snackbar.showSnackbar("还没填 DeepSeek API Key（设置 → DeepSeek API Key）") }
            return
        }
        scope.launch {
            generating = true
            val found = mutableListOf<AnswerConflict>()
            var failedSections = 0
            var autoFilled = 0
            model.sections.forEachIndexed { si, section ->
                genProgress = "正在生成解析 ${si + 1}/${model.sections.size}：${section.title}"
                val passage = section.paragraphs.joinToString("\n\n") { it.en }
                val questions = section.questions.map { q ->
                    CQuestion(
                        id = "${section.id}-${q.no}",
                        no = q.no,
                        stem = Inline.parse(q.stem),
                        kind = q.kind,
                        answer = q.answer,
                        options = q.options
                    )
                }
                runCatching {
                    analyzer.analyzeSection(
                        sectionTitle = section.title,
                        sectionType = section.type,
                        passage = passage,
                        paragraphCount = section.paragraphs.size,
                        questions = questions
                    )
                }.onSuccess { result ->
                    result.parasZh.forEachIndexed { i, zh -> section.paragraphs.getOrNull(i)?.zh = zh }
                    result.questions.forEach { aq ->
                        val dq = section.questions.firstOrNull { it.no == aq.no } ?: return@forEach
                        dq.solutions.clear()
                        aq.solutions.forEach { dq.solutions.add(DraftSolution(it.label).apply { text = it.text }) }
                        dq.groups.clear()
                        aq.groups.forEach { g -> dq.groups.add(DraftGroup(g.key, g.label).apply { items.addAll(g.items) }) }
                        val teacher = dq.answer.letter ?: dq.answer.text ?: ""
                        val aiLetter = aq.aiAnswer?.let { firstLetter(it) }
                        val aiText = aq.aiAnswerText?.trim().takeUnless { it.isNullOrBlank() }
                            ?: aq.aiAnswer?.trim()

                        if (teacher.isBlank()) {
                            // 默认没答案：采用 DeepSeek 给出的答案
                            if (dq.hasOptions && !aiLetter.isNullOrBlank()) {
                                dq.answer = dq.answer.copy(letter = aiLetter)
                                autoFilled += 1
                            } else if (!dq.hasOptions && !aiText.isNullOrBlank()) {
                                dq.answer = dq.answer.copy(text = aiText)
                                autoFilled += 1
                            }
                        } else {
                            val ai = aiLetter ?: aiText ?: ""
                            if (ai.isNotBlank() && !answersMatch(teacher, ai)) {
                                found.add(AnswerConflict(dq.no, teacher, ai))
                            }
                        }
                    }
                }.onFailure {
                    failedSections += 1
                    snackbar.showSnackbar("第 ${si + 1} 篇解析失败：${it.message}")
                }

                runCatching {
                    val compiled = DraftMapper.toCompiled(model)
                    val mindmaps = model.sections.mapNotNull { s -> s.mindmapSvg?.let { s.id to it } }.toMap()
                    autosavedAt = app.draftRepository.save(compiled, mindmaps, model.sourceLabel)
                }
            }
            generating = false
            genProgress = ""
            if (autoFilled > 0) {
                snackbar.showSnackbar("DeepSeek 已自动补全 $autoFilled 道题的答案")
            }
            if (failedSections > 0) {
                snackbar.showSnackbar("有 $failedSections 篇解析失败，这些题暂时没有解析（可再点一次生成重试）")
            }
            if (found.isEmpty()) {
                // 没有答案冲突：直接保存为解析卷
                saveAs(model, "analyzed")
            } else {
                // 有冲突：先让老师逐条确认，确认后再保存
                conflicts = found
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (if (draft?.stage == "analyzed") "编辑解析卷" else "编辑扫描卷"),
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
                    TextButton(
                        onClick = { draft?.let { saveAs(it, it.stage) } },
                        enabled = draft != null && !saving
                    ) {
                        Text(if (saving) "保存中…" else "保存", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val model = draft
        if (loading || model == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator() else Text("没有可编辑的内容")
            }
            return@Scaffold
        }

        AdaptiveLazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { MetaEditor(model) }

            items(model.sections.size, key = { model.sections[it].id + it }) { index ->
                val section = model.sections[index]
                SectionEditor(
                    section = section,
                    statePrefix = "s$index",
                    busyKey = busyKey,
                    onTranslate = { text, key, done -> translate(text, key, done) }
                )
            }

            item {
                Button(
                    onClick = { saveAs(model, "scanned") },
                    enabled = !saving && !generating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (saving) "正在保存…" else "保存为扫描卷（只有原文/题目）")
                }
                Button(
                    onClick = { generateAnalysis(model) },
                    enabled = !saving && !generating,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(if (generating) "正在生成解析…" else "生成解析并保存解析卷")
                }
                if (generating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(genProgress, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "扫描卷 = 只扫描出的原文/题干/选项；生成解析时：你填过的答案会被 AI 核对（不符会提示），没填的答案由 DeepSeek 直接补上。",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    "草稿每 10 秒自动保存（退出 App 再进来可继续编辑）。",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (autosavedAt > 0) {
                    Text(
                        "草稿已自动保存 · ${hhmm(autosavedAt)}",
                        fontSize = 10.sp,
                        color = OkGreen,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    errorDialog?.let { errors ->
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            title = { Text("还有 ${errors.size} 处需要先修") },
            text = {
                Column {
                    errors.take(12).forEach { Text("· $it", fontSize = 12.sp) }
                    if (errors.size > 12) Text("…还有 ${errors.size - 12} 条", fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { errorDialog = null }) { Text("去修改") } }
        )
    }

    // AI 答案与老师答案不一致时逐个确认（这是「不符就提出」的落点）
    conflicts?.let { list ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("AI 答案与你的答案不一致（${list.size} 题）") },
            text = {
                Column {
                    Text(
                        "解析已生成。下面是 AI 独立作答与你填的标准答案不一致的题，请逐条确认（保留你的 / 采用 AI 的）。",
                        fontSize = 12.sp
                    )
                    list.take(10).forEach { conflict ->
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(
                                "第 ${conflict.no} 题：你的 ${conflict.teacher} ／ AI ${conflict.ai}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row {
                                TextButton(onClick = { conflicts = list - conflict }) {
                                    Text("保留我的", fontSize = 11.sp)
                                }
                                TextButton(onClick = {
                                    val target = draft?.sections
                                        ?.flatMap { it.questions }
                                        ?.firstOrNull { it.no == conflict.no }
                                    if (target != null) {
                                        target.answer = if (target.hasOptions) {
                                            target.answer.copy(letter = firstLetter(conflict.ai))
                                        } else {
                                            target.answer.copy(text = conflict.ai)
                                        }
                                    }
                                    conflicts = list - conflict
                                }) { Text("采用 AI 答案", fontSize = 11.sp) }
                            }
                        }
                    }
                    if (list.size > 10) {
                        Text("…还有 ${list.size - 10} 题稍后再看", fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    conflicts = null
                    draft?.let { saveAs(it, "analyzed") }
                }) { Text("确认并保存解析卷") }
            }
        )
    }
}

/* ------------------------------- 元信息 ------------------------------- */

@Composable
private fun MetaEditor(model: DraftModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Text("试卷信息", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            OutlinedTextField(
                value = model.title,
                onValueChange = { model.title = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("标题") },
                singleLine = true
            )
            OutlinedTextField(
                value = model.full,
                onValueChange = { model.full = it },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                label = { Text("全名（学校+月份+科目）") },
                singleLine = true
            )
            OutlinedTextField(
                value = model.paperLine,
                onValueChange = { model.paperLine = it },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                label = { Text("卷面信息（如 满分 150 分 · 时间 120 分钟）") },
                singleLine = true
            )
            OutlinedTextField(
                value = model.id,
                onValueChange = { model.id = it },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                label = { Text("试卷 id（小写字母/数字/连字符，可留空自动生成）") },
                singleLine = true
            )
            Text(
                "共 ${model.sections.size} 篇 / ${model.totalQuestions} 题　答案已填 ${model.answeredCount} 题",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                "当前阶段：" + if (model.stage == "analyzed") "解析卷（已生成解析）" else "扫描卷（待填答案、生成解析）",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/* ------------------------------- 篇目 ------------------------------- */

@Composable
private fun SectionEditor(
    section: DraftSection,
    statePrefix: String,
    busyKey: String?,
    onTranslate: (String, String, (String) -> Unit) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${section.chip}  ${section.title}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        "${section.type} · ${section.paragraphs.size} 段 · ${section.questions.size} 题",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { section.expanded = !section.expanded }) {
                    Icon(
                        if (section.expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (section.expanded) "收起" else "展开"
                    )
                }
            }

            section.blankCheck()?.let { check ->
                Text(
                    check,
                    fontSize = 11.sp,
                    color = if (check.startsWith("空位与题号一致")) MaterialTheme.colorScheme.primary
                    else com.examreader.ui.AnswerRed,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            if (!section.expanded) return@Column

            // 原文 + 翻译
            section.paragraphs.forEachIndexed { index, para ->
                ParagraphEditor(
                    para = para,
                    key = "$statePrefix-p$index",
                    busy = busyKey == "$statePrefix-p$index",
                    onTranslate = onTranslate
                )
            }

            // 重点词汇翻译
            if (section.vocab.isNotEmpty()) {
                Text(
                    "重点词汇（可补翻译）",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
                section.vocab.forEachIndexed { index, vocab ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${vocab.word}  ${vocab.phonetic} ${vocab.partOfSpeech}", fontSize = 12.sp)
                        }
                        OutlinedTextField(
                            value = vocab.zh,
                            onValueChange = { vocab.zh = it },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true,
                            label = { Text("释义", fontSize = 10.sp) },
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        TranslateIcon(
                            busy = busyKey == "$statePrefix-v$index",
                            onClick = {
                                onTranslate(vocab.word, "$statePrefix-v$index") { result -> vocab.zh = result }
                            }
                        )
                    }
                }
            }

            // 题目（题干 + 讲解）
            section.questions.forEachIndexed { index, question ->
                QuestionEditor(
                    question = question,
                    key = "$statePrefix-q$index",
                    busyKey = busyKey
                )
            }
        }
    }
}

@Composable
private fun ParagraphEditor(
    para: DraftParagraph,
    key: String,
    busy: Boolean,
    onTranslate: (String, String, (String) -> Unit) -> Unit
) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(
            "第 ${para.index + 1} 段" + (if (!para.heading.isNullOrBlank()) "：${para.heading}" else ""),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        OutlinedTextField(
            value = para.en,
            onValueChange = { para.en = it },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            label = { Text("英文原文（挖空写 {{题号}} 或 {{题号|提示}}）", fontSize = 10.sp) },
            minLines = 3,
            textStyle = MaterialTheme.typography.bodySmall
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = para.zh,
                onValueChange = { para.zh = it },
                modifier = Modifier.weight(1f).padding(top = 4.dp),
                label = { Text("中文翻译", fontSize = 10.sp) },
                minLines = 2,
                textStyle = MaterialTheme.typography.bodySmall
            )
            TranslateIcon(
                busy = busy,
                onClick = { onTranslate(para.en, key) { result -> para.zh = result } }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionEditor(question: DraftQuestion, key: String, busyKey: String?) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("第 ${question.no} 题", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            if (question.kind.isNotBlank()) {
                Text("  ${question.kind}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "答案：" + question.answerDisplay(),
                fontSize = 11.sp,
                color = if (question.answerFilled()) MaterialTheme.colorScheme.primary else AnswerRed
            )
        }
        if (question.options.isNotEmpty()) {
            Text(
                question.options.joinToString("　") { "${it.label}. ${it.text}" },
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text("选择正确答案（可自己拟定）", fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
            // 七选五有 7 个选项，字号放大/窄屏时用 FlowRow 自动换行
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                question.options.forEach { option ->
                    FilterChip(
                        selected = question.answer.letter == option.label,
                        onClick = {
                            question.answer = question.answer.copy(
                                letter = if (question.answer.letter == option.label) null else option.label
                            )
                        },
                        label = { Text(option.label, fontSize = 11.sp) }
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = question.answer.text ?: "",
                onValueChange = { question.answer = question.answer.copy(text = it) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                label = { Text("标准答案（自己拟定）", fontSize = 10.sp) },
                singleLine = true
            )
            if (!question.answer.hint.isNullOrBlank()) {
                Text(
                    "原卷所给词：${question.answer.hint}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedTextField(
            value = question.stem,
            onValueChange = { question.stem = it },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            label = { Text("题干", fontSize = 10.sp) },
            minLines = 1,
            textStyle = MaterialTheme.typography.bodySmall
        )
        question.solutions.forEachIndexed { index, solution ->
            OutlinedTextField(
                value = solution.text,
                onValueChange = { solution.text = it },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                label = { Text("讲解：${solution.label.ifBlank { "解析" }}", fontSize = 10.sp) },
                minLines = 2,
                textStyle = MaterialTheme.typography.bodySmall
            )
        }
        question.groups.forEach { group ->
            Text(
                "讲解分组：${group.label}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 6.dp)
            )
            group.items.forEachIndexed { index, item ->
                OutlinedTextField(
                    value = item,
                    onValueChange = { group.items[index] = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                    label = { Text("第 ${index + 1} 条", fontSize = 10.sp) },
                    minLines = 2,
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (question.solutions.isEmpty() && question.groups.isEmpty()) {
            Text(
                "还没有解析：点页面底部「生成解析并保存解析卷」由 DeepSeek 生成",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun TranslateIcon(busy: Boolean, onClick: () -> Unit) {
    if (busy) {
        Box(Modifier.padding(8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    } else {
        TextButton(onClick = onClick, modifier = Modifier.padding(start = 4.dp)) {
            Text("译", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
/** 自动保存时间显示（HH:mm:ss） */
private fun hhmm(ts: Long): String =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date(ts))
/** AI 与老师答案不一致的记录 */
private data class AnswerConflict(val no: String, val teacher: String, val ai: String)

private fun normAnswer(value: String?): String =
    (value ?: "").trim().lowercase()
        .replace(Regex("\\s+"), " ")
        .trimEnd('.', ',', ';', ':', '!', '?', '"', '\'', ')')

private fun answersMatch(a: String?, b: String?): Boolean {
    val x = normAnswer(a)
    val y = normAnswer(b)
    if (x.isEmpty() || y.isEmpty()) return true
    return x == y
}

/** 从 AI 的答案文本里取选项字母（兼容 "C" / "C. ..." / "c"） */
private fun firstLetter(value: String): String =
    value.trim().uppercase().firstOrNull { it in 'A'..'H' }?.toString() ?: value.trim()