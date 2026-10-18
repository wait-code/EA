package com.examreader.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.examreader.data.CSeg
import com.examreader.ui.AnswerRed
import com.examreader.ui.PointAmber

/**
 * 行内标记渲染：普通文字 / 正确选项（红）/ 考点短语（橙底）/ 空位序号 / 粗体 / 斜体 / 换行。
 */
fun segmentsAnnotated(segs: List<CSeg>, base: SpanStyle = SpanStyle()): AnnotatedString = buildAnnotatedString {
    segs.forEach { s ->
        when (s.t) {
            "blank" -> withStyle(SpanStyle(color = AnswerRed, fontWeight = FontWeight.Bold)) {
                append("〔")
                append(s.no ?: "")
                append("〕")
            }

            "br" -> append('\n')
            "ans" -> withStyle(
                SpanStyle(color = AnswerRed, fontWeight = FontWeight.Bold, background = AnswerRed.copy(alpha = 0.08f))
            ) { append(s.v ?: "") }

            "q" -> withStyle(
                SpanStyle(color = PointAmber, background = PointAmber.copy(alpha = 0.12f), fontWeight = FontWeight.Medium)
            ) { append(s.v ?: "") }

            "blk" -> withStyle(SpanStyle(color = PointAmber, fontWeight = FontWeight.Bold)) { append(s.v ?: "") }
            "b" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.v ?: "") }
            "i" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.v ?: "") }
            else -> withStyle(base) { append(s.v ?: "") }
        }
    }
}

@Composable
fun SegmentsText(
    segs: List<CSeg>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    if (segs.isEmpty()) return
    Text(text = segmentsAnnotated(segs), modifier = modifier, style = style, color = color)
}

private fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\'' || c == '’' || c == '-'

/** 从点击偏移量里取出完整的英文单词（手指点在标点上时不响应） */
private fun wordAt(text: String, offset: Int): String? {
    if (text.isEmpty()) return null
    val index = offset.coerceIn(0, text.length - 1)
    if (!isWordChar(text[index])) return null
    var start = index
    while (start > 0 && isWordChar(text[start - 1])) start -= 1
    var end = index
    while (end < text.length - 1 && isWordChar(text[end + 1])) end += 1
    return text.substring(start, end + 1).trim('\'', '-', '’').takeIf { word ->
        word.length >= 2 && word.any { it.isLetter() }
    }
}

/**
 * 可点词的正文：点任意英文单词 → 回调（用于「悬浮小岛」查词）。
 * 用 TextLayoutResult 把点击坐标换算成字符偏移，再向两侧扩成单词。
 */
@Composable
fun TappableSegmentsText(
    segs: List<CSeg>,
    onWordTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    if (segs.isEmpty()) return
    val annotated = remember(segs) { segmentsAnnotated(segs) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { position ->
                val result = layout ?: return@detectTapGestures
                val offset = runCatching { result.getOffsetForPosition(position) }.getOrDefault(-1)
                if (offset < 0) return@detectTapGestures
                wordAt(annotated.text, offset)?.let(onWordTap)
            }
        },
        onTextLayout = { layout = it },
        style = style,
        color = color
    )
}

/**
 * 带输入框的行内空位（词卷短文填空用）。
 * values: 空位序号 -> 已填内容；onValueChange(序号, 新值)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SegmentsWithBlanks(
    segs: List<CSeg>,
    values: Map<String, String>,
    onValueChange: (String, String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    answerKey: Map<String, String> = emptyMap(),
    showAnswer: Boolean = false
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        segs.forEach { s ->
            when (s.t) {
                "blank" -> {
                    val no = s.no ?: ""
                    OutlinedTextField(
                        value = values[no] ?: "",
                        onValueChange = { onValueChange(no, it) },
                        modifier = Modifier.width(112.dp),
                        singleLine = true,
                        enabled = enabled,
                        label = { Text(no, fontSize = 11.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        supportingText = if (showAnswer && answerKey[no] != null) {
                            { Text(answerKey[no] ?: "", fontSize = 11.sp, color = AnswerRed) }
                        } else null
                    )
                }

                "br" -> Text("\n")
                else -> Text(text = segmentsAnnotated(listOf(s)), style = LocalTextStyle.current)
            }
        }
    }
}
