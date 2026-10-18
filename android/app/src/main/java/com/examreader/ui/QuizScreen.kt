package com.examreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.examreader.data.QuizPartDetail
import com.examreader.data.SegCodec
import com.examreader.ui.components.SegmentsText
import com.examreader.ui.components.SegmentsWithBlanks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 词汇检测卷：按数据自动区分「音标写词 / 挖空句选词 / 短文选词填空」三种形态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(nav: NavController, paperId: String) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()

    var parts by remember(paperId) { mutableStateOf<List<QuizPartDetail>>(emptyList()) }
    var loading by remember(paperId) { mutableStateOf(true) }
    var selected by remember(paperId) { mutableIntStateOf(0) }
    var inputs by remember(paperId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var graded by remember(paperId) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var showKey by remember(paperId) { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(paperId) {
        parts = app.examRepository.quizParts(paperId)
        loading = false
    }

    // 切到某个部分时，把库里已有的作答回填到输入框
    LaunchedEffect(selected, parts) {
        val part = parts.getOrNull(selected) ?: return@LaunchedEffect
        val saved = app.studyRepository.observeQuizAnswers(paperId, part.id).first()
        if (saved.isNotEmpty()) {
            inputs = inputs + saved.map { (k, v) -> key(part.id, k) to v.input }
            graded = graded + saved.map { (k, v) -> key(part.id, k) to v.correct }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("词汇检测", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showKey = !showKey }) {
                        Text(if (showKey) "隐藏答案" else "显示答案", fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (parts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("这份试卷没有词汇检测卷")
            }
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selected, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.surface) {
                parts.forEachIndexed { index, part ->
                    Tab(
                        selected = selected == index,
                        onClick = { selected = index },
                        text = { Text(part.no + " " + part.name, fontSize = 12.sp) }
                    )
                }
            }
            val part = parts[selected]
            val itemKeys = remember(part) { partItemKeys(part) }
            AdaptiveLazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        part.desc,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (part.box.isNotEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("选词框（每词限用一次）", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    part.box.chunked(4).forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            row.forEach { word ->
                                                Text(
                                                    word,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (part.items.isNotEmpty()) {
                    itemsIndexed(part.items, key = { _, it -> it.id }) { index, item ->
                        val k = key(part.id, itemKeyOf(item.no, item.no))
                        ItemRow(
                            index = index,
                            part = part,
                            prompt = promptOf(part, item),
                            value = inputs[k] ?: "",
                            onValueChange = { inputs = inputs + (k to it) },
                            correct = graded[k],
                            answer = item.answer,
                            showKey = showKey
                        )
                    }
                }
                if (part.passage.isNotEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(12.dp)) {
                                SegmentsText(part.passage, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    item {
                        val values = part.answers.indices.associate { i ->
                            val no = (i + 1).toString()
                            no to (inputs[key(part.id, blankKey(no))] ?: "")
                        }
                        val answerKey = part.answers.indices.associate { i ->
                            (i + 1).toString() to part.answers[i]
                        }
                        SegmentsWithBlanks(
                            segs = part.passage,
                            values = values,
                            enabled = graded.isEmpty(),
                            onValueChange = { no, text ->
                                inputs = inputs + (key(part.id, blankKey(no)) to text)
                            },
                            answerKey = answerKey,
                            showAnswer = showKey
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(onClick = {
                            val newInputs = inputs.filterKeys { it.startsWith(part.id + "#") }
                            val nextGraded = graded.toMutableMap()
                            var correct = 0
                            itemKeys.forEach { (k, answer) ->
                                val value = newInputs[key(part.id, k)] ?: ""
                                val ok = norm(value) == norm(answer)
                                if (ok) correct++
                                nextGraded[key(part.id, k)] = ok
                                scope.launch {
                                    app.studyRepository.saveQuizAnswer(paperId, part.id, k, value, ok)
                                }
                            }
                            graded = nextGraded
                            val total = itemKeys.size * part.perScore
                            val got = correct * part.perScore
                            resultText = "得分 $got / $total（答对 $correct / ${itemKeys.size}）"
                        }) { Text("提交评分") }

                        TextButton(onClick = {
                            itemKeys.forEach { (k, _) ->
                                inputs = inputs + (key(part.id, k) to "")
                            }
                            graded = graded.filterKeys { !it.startsWith(part.id + "#") }
                            scope.launch { app.studyRepository.clearQuizAnswers(paperId, part.id) }
                        }) { Text("重做本部分") }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    resultText?.let { text ->
        AlertDialog(
            onDismissRequest = { resultText = null },
            title = { Text("本部分评分") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    resultText = null
                    showKey = true
                }) { Text("查看答案") }
            },
            dismissButton = { TextButton(onClick = { resultText = null }) { Text("关闭") } }
        )
    }
}

@Composable
private fun ItemRow(
    index: Int,
    part: QuizPartDetail,
    prompt: String,
    value: String,
    onValueChange: (String) -> Unit,
    correct: Boolean?,
    answer: String,
    showKey: Boolean
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}.",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier.width(30.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                if (prompt.isNotBlank()) {
                    Text(prompt, fontSize = 13.sp)
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    singleLine = true,
                    label = { Text("你的答案") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(8.dp)
                )
                if (showKey) {
                    Text(
                        "答案：$answer",
                        fontSize = 12.sp,
                        color = AnswerRed,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (correct != null) {
                Text(
                    if (correct) "✓" else "✗",
                    fontSize = 18.sp,
                    color = if (correct) OkGreen else AnswerRed,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

private fun key(partId: String, itemKey: String) = "$partId#$itemKey"

private fun itemKeyOf(no: String, _extra: String) = "i:$no"

private fun blankKey(no: String) = "b:$no"

private fun partItemKeys(part: QuizPartDetail): List<Pair<String, String>> =
    part.items.map { itemKeyOf(it.no, it.no) to it.answer } +
        part.answers.mapIndexed { i, answer -> blankKey((i + 1).toString()) to answer }

private fun promptOf(part: QuizPartDetail, item: com.examreader.data.CQuizItem): String {
    val sb = StringBuilder()
    if (!item.phonetic.isNullOrBlank()) sb.append(item.phonetic).append("  ")
    if (!item.partOfSpeech.isNullOrBlank()) sb.append(item.partOfSpeech).append(" ")
    if (!item.zh.isNullOrBlank()) sb.append(item.zh)
    if (item.pre.isNotEmpty()) {
        if (sb.isNotEmpty()) sb.append('\n')
        sb.append(SegCodec.toPlain(item.pre))
        sb.append(" ______")
        if (!item.post.isNullOrBlank()) sb.append(item.post)
    }
    if (sb.isEmpty()) sb.append("第 ${item.no} 题")
    return sb.toString()
}

private fun norm(text: String): String =
    text.trim().lowercase()
        .replace(Regex("\\s+"), " ")
        .trimEnd('.', ',', ';', ':', '!', '?', '"', '\'', ')')
