package com.examreader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examreader.data.CQuestion
import com.examreader.data.SegCodec
import com.examreader.data.StudyStateEntity
import com.examreader.ui.AnswerRed
import com.examreader.ui.OkGreen
import com.examreader.ui.OkGreenBg
import com.examreader.ui.PickBlueBg
import com.examreader.ui.WrongRedBg

/**
 * 一道题的完整交互：选择 / 填空 / 显示答案与解析 / 收藏。
 * 完全由数据驱动：有 options 就按选择题渲染，没有就按填空题渲染。
 * 题干里的单词也可点（查词小岛）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QuestionCard(
    q: CQuestion,
    state: StudyStateEntity,
    onPick: (String?) -> Unit,
    onFill: (String) -> Unit,
    onToggleAnswer: () -> Unit,
    onCollect: () -> Unit,
    onLookupWord: (String) -> Unit,
    onWordTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val answerShown = state.answerShown
    var filled by remember(q.id) { mutableStateOf(state.filled) }
    LaunchedEffect(q.id) { if (filled != state.filled) filled = state.filled }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "第 ${q.no} 题",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (q.kind.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(q.kind, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onToggleAnswer) {
                    Text(if (answerShown) "收起解析" else "显示答案", fontSize = 12.sp)
                }
            }

            if (q.stem.isNotEmpty()) {
                TappableSegmentsText(
                    segs = q.stem,
                    onWordTap = onWordTap,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                )
            }

            if (q.options.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    q.options.forEach { option ->
                        val picked = state.picked == option.label
                        val isCorrect = q.answer.letter == option.label
                        val bg = when {
                            answerShown && isCorrect -> OkGreenBg
                            answerShown && picked && !isCorrect -> WrongRedBg
                            picked -> PickBlueBg
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val border = when {
                            answerShown && isCorrect -> BorderStroke(1.5.dp, OkGreen)
                            picked -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        }
                        Surface(
                            onClick = { onPick(if (picked) null else option.label) },
                            shape = RoundedCornerShape(8.dp),
                            color = bg,
                            border = border,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    "${option.label}.",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (answerShown && isCorrect) OkGreen else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(option.text, fontSize = 14.sp)
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = filled,
                    onValueChange = {
                        filled = it
                        onFill(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("填写答案") },
                    singleLine = true,
                    enabled = !answerShown
                )
                if (answerShown) {
                    Text(
                        "答案：" + (q.answer.text ?: "") +
                            (if (!q.answer.zh.isNullOrBlank()) "（${q.answer.zh}）" else ""),
                        color = AnswerRed,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (!q.answer.hint.isNullOrBlank()) {
                        Text(
                            "所给词：${q.answer.hint}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (answerShown) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    if (q.analysis.solutions.isEmpty() && q.analysis.groups.isEmpty()) {
                        Text(
                            "这道题还没有解析（扫描卷）——去「编辑试卷内容 → 生成解析并保存解析卷」补上",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    if (q.options.isNotEmpty()) {
                        val correctText = q.options.firstOrNull { it.label == q.answer.letter }?.text ?: ""
                        val answerLine = if (q.answer.letter.isNullOrBlank()) {
                            if (correctText.isBlank()) "正确答案：未填" else "正确答案：$correctText"
                        } else {
                            "正确答案：${q.answer.letter}. $correctText"
                        }
                        Text(
                            answerLine,
                            color = AnswerRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    if (!q.answer.zh.isNullOrBlank() && q.options.isNotEmpty()) {
                        Text("（${q.answer.zh}）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (q.analysis.solutions.isNotEmpty()) {
                        Text(
                            q.analysis.solutionLabel,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    q.analysis.solutions.forEach { sol ->
                        Column(modifier = Modifier.padding(top = 6.dp)) {
                            if (sol.label.isNotBlank()) {
                                Text(
                                    sol.label,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            TappableSegmentsText(
                                segs = sol.text,
                                onWordTap = onWordTap,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    q.analysis.groups.forEach { group ->
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Text(
                                group.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            group.items.forEach { item ->
                                TappableSegmentsText(
                                    segs = item.text,
                                    onWordTap = onWordTap,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.padding(top = 6.dp)) {
                TextButton(onClick = onCollect) { Text("收藏此句", fontSize = 12.sp) }
                if (q.stem.isNotEmpty()) {
                    TextButton(onClick = {
                        val plain = SegCodec.toPlain(q.stem)
                        plain.split(Regex("[^A-Za-z'-]+")).filter { it.length >= 3 }.take(1)
                            .firstOrNull()?.let(onLookupWord)
                    }) { Text("查词", fontSize = 12.sp) }
                }
            }
        }
    }
}
