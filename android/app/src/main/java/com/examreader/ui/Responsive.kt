package com.examreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 响应式基础：
 *   · 手机（<600dp）内容满宽
 *   · 平板 / 横屏（≥600dp）内容限宽并居中，避免一行文字拉得过长
 *   · 与字号缩放配合：因为限的是「宽度」而不是高度，字号变大时文字自动换行、卡片自动变高
 */

val ContentMaxWidth: Dp = 900.dp

enum class WindowSize { Compact, Medium, Expanded }

@Composable
fun windowSize(): WindowSize {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp >= 840 -> WindowSize.Expanded
        widthDp >= 600 -> WindowSize.Medium
        else -> WindowSize.Compact
    }
}

/**
 * 自适应列表：自动限宽 + 居中，各页面统一用它，避免平板/横屏上一行拉太宽。
 * 用法与 LazyColumn 完全一致。
 */
@Composable
fun AdaptiveLazyColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    maxWidth: Dp = ContentMaxWidth,
    content: LazyListScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxHeight(),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}

/** 需要自己控制 LazyColumn 参数时，用这个取「限宽后的 Modifier」 */
fun Modifier.adaptiveWidth(maxWidth: Dp = ContentMaxWidth): Modifier =
    this.widthIn(max = maxWidth)

/** 小标题（列表内用）：字随系统/App 字号缩放，不做截断 */
@Composable
fun AdaptiveSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
