package com.examreader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.examreader.data.DraftMapper
import com.examreader.data.DraftStore
import com.examreader.data.ScanJobBus
import com.examreader.data.ScanState
import com.examreader.service.ScanService
import com.examreader.ui.nav.Routes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 后台扫描的**悬浮胶囊**：不再整条盖住页面顶部，而是一个小卡片，
 * 可以按住拖动到任意位置（默认右上角）；完成后点「查看」进编辑器。
 */
@Composable
fun ScanBanner(nav: NavController) {
    val app = rememberApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by ScanJobBus.state.collectAsStateWithLifecycle()

    var dragOffset by remember { mutableStateOf(IntOffset(0, 0)) }
    val visible = state !is ScanState.Idle

    fun openDraft() {
        scope.launch {
            if (DraftStore.take() == null) {
                app.draftRepository.load()?.let { snapshot ->
                    DraftStore.put(
                        DraftMapper.fromCompiled(snapshot.compiled, snapshot.mindmaps).apply {
                            sourceLabel = snapshot.sourceLabel
                        }
                    )
                }
            }
            ScanJobBus.idle()
            nav.navigate(Routes.editor("-"))
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWidthPx = constraints.maxWidth.toFloat()
        val maxHeightPx = constraints.maxHeight.toFloat()

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 2 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 2 }
        ) {
            Surface(
                modifier = Modifier
                    .padding(top = 46.dp, end = 12.dp)
                    .offset { dragOffset }
                    .widthIn(max = 300.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val maxLeft = -(maxWidthPx * 0.72f)
                            dragOffset = IntOffset(
                                (dragOffset.x + drag.x).roundToInt().coerceIn(maxLeft.roundToInt(), 0),
                                (dragOffset.y + drag.y).roundToInt().coerceIn(-80, (maxHeightPx * 0.7f).roundToInt())
                            )
                        }
                    },
                shape = RoundedCornerShape(18.dp),
                color = when (state) {
                    is ScanState.Failed -> MaterialTheme.colorScheme.errorContainer
                    is ScanState.Success -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surface
                },
                tonalElevation = 2.dp,
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (val s = state) {
                        is ScanState.Running -> {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.width(112.dp)) {
                                Text("后台扫描 ${s.done}/${s.total}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text("按住可拖动", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                ScanService.stop(context)
                                ScanJobBus.idle()
                            }) { Text("取消", fontSize = 11.sp) }
                        }

                        is ScanState.Success -> {
                            Column(Modifier.width(130.dp)) {
                                Text("扫描完成", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(
                                    s.title.ifBlank { "点查看进编辑器" }.take(14),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { openDraft() }) { Text("查看", fontSize = 11.sp) }
                            TextButton(onClick = { ScanJobBus.idle() }) { Text("×", fontSize = 13.sp) }
                        }

                        is ScanState.Failed -> {
                            Column(Modifier.width(140.dp)) {
                                Text("扫描失败", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(s.message.take(40), fontSize = 10.sp, maxLines = 2)
                            }
                            TextButton(onClick = { ScanJobBus.idle() }) { Text("关闭", fontSize = 11.sp) }
                        }

                        ScanState.Idle -> Spacer(Modifier)
                    }
                }
            }
        }
    }
}
