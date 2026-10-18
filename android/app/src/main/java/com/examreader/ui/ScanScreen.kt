package com.examreader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.examreader.data.AiConfig
import com.examreader.data.AppSettings
import com.examreader.data.DraftMapper
import com.examreader.data.DraftStore
import com.examreader.data.ScanJobBus
import com.examreader.data.ScanState
import com.examreader.service.ScanService
import com.examreader.ui.nav.Routes
import kotlinx.coroutines.launch

/**
 * 出卷第一步：选试卷图片 → 交给**前台服务**在后台识别（可离开本页）→ 完成后通知 + 顶部横幅提醒 → 进编辑器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(nav: NavController) {
    val app = rememberApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scanState by ScanJobBus.state.collectAsStateWithLifecycle()

    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }

    val running = scanState is ScanState.Running
    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    fun startScan() {
        if (images.isEmpty()) return
        notice = null
        ScanJobBus.idle()
        ScanService.start(context, images)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationGranted = granted
        if (!granted) {
            notice = "没给通知权限：扫描仍在后台进行，App 顶部横幅会提示，但通知栏/灵动岛可能看不到。"
        }
        startScan()
    }

    // 扫描完成 → 自动把草稿放进内存并进编辑器（用户此时可能已经离开本页，横幅那边也能进）
    LaunchedEffect(scanState) {
        val current = scanState
        if (current is ScanState.Success) {
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

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        if (list.isNotEmpty()) images = images + list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫描试卷出卷", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { nav.navigate(Routes.settings("-")) }) { Text("AI 设置", fontSize = 12.sp) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        AdaptiveLazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("三步出卷", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "① 选图　② 后台识别（可离开本页）　③ 在编辑器里改原文·讲解·翻译，保存即自动生成试卷包",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            "识别模型：自动适配（首选 " + AiConfig.VISION_MODEL + "）" +
                                if (settings.qwenReady) "" else "　未配置 Key",
                            fontSize = 11.sp,
                            color = if (settings.qwenReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        if (!settings.qwenReady) {
                            Text(
                                "去「设置 → 千问 API Key」填一下",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AssistChip(
                                onClick = { nav.navigate(Routes.settings("-")) },
                                label = { Text("去配置") },
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { runCatching { picker.launch(arrayOf("image/*")) } },
                        enabled = !running
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 3.dp))
                        Text("选择图片")
                    }
                    if (images.isNotEmpty() && !running) {
                        TextButton(onClick = { images = emptyList() }) { Text("清空") }
                    }
                }
            }

            if (images.isNotEmpty()) {
                item {
                    Text(
                        "已选 ${images.size} 张（同篇跨图会自动合并）",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(images.size) { index ->
                    val uri = images[index]
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "第 ${index + 1} 张：" + (uri.lastPathSegment?.substringAfterLast('/') ?: "图片"),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                images = images.filterIndexed { i, _ -> i != index }
                            }, enabled = !running) {
                                Icon(Icons.Filled.Delete, contentDescription = "移除")
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            startScan()
                        }
                    },
                    enabled = images.isNotEmpty() && settings.qwenReady && !running,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (running) "后台扫描中…" else "开始识别（后台进行）")
                }

                if (running) {
                    val s = scanState as ScanState.Running
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(
                        "${s.message}（${s.done}/${s.total}）　可离开本页，完成会通知你",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    TextButton(onClick = {
                        ScanService.stop(context)
                        ScanJobBus.idle()
                    }) { Text("取消扫描", fontSize = 12.sp) }
                }

                notice?.let {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(it, fontSize = 11.sp, modifier = Modifier.padding(10.dp))
                    }
                }

                (scanState as? ScanState.Failed)?.let {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("扫描失败", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(it.message, fontSize = 12.sp)
                            Text(
                                "常见原因：Key 无效 / 未开通视觉模型 / 网络不通 / 图片太糊。App 会自动尝试可用的千问视觉模型。",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            if (running) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
