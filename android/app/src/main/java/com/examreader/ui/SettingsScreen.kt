package com.examreader.ui

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.examreader.data.AiConfig
import com.examreader.data.AppSettings
import com.examreader.data.TranslateService
import com.examreader.ui.nav.Routes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController, paperId: String) {
    val app = rememberApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val snackbar = remember { SnackbarHostState() }
    val hasPaper = paperId.isNotBlank() && paperId != "-"

    var fontScale by remember(settings.fontScale) { mutableStateOf(settings.fontScale) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var qwenKey by remember(settings.qwenApiKey) { mutableStateOf(settings.qwenApiKey) }
    var deepseekKey by remember(settings.deepseekApiKey) { mutableStateOf(settings.deepseekApiKey) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            val label = uri.lastPathSegment?.substringAfterLast('/') ?: "所选文件"
            val outcome = if (bytes == null) null else app.examRepository.importFromUri(bytes, label)
            snackbar.showSnackbar(
                when {
                    outcome == null -> "读取文件失败"
                    outcome.ok -> "导入成功：${outcome.title}"
                    else -> "导入失败：${outcome.message}"
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        AdaptiveLazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---------------- 外观 ----------------
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("阅读外观", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "字号 ${(fontScale * 100).roundToInt()}%",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        Slider(
                            value = fontScale,
                            onValueChange = { fontScale = it },
                            onValueChangeFinished = { scope.launch { app.settingsRepository.setFontScale(fontScale) } },
                            valueRange = 0.8f..1.6f,
                            steps = 7
                        )
                    }
                }
            }

            // ---------------- 千问 Key（识别 + 翻译共用） ----------------
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("千问 API Key", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "扫描用视觉模型（首选 ${AiConfig.VISION_MODEL}，自动适配）；编辑器里点「译」也用它。解析另需下面的 DeepSeek Key。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            if (settings.qwenReady) "已配置（识别 + 翻译可用）" else "未配置",
                            fontSize = 11.sp,
                            color = if (settings.qwenReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        OutlinedTextField(
                            value = qwenKey,
                            onValueChange = { qwenKey = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            label = { Text("API Key（sk-…）") },
                            singleLine = true
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                scope.launch {
                                    app.settingsRepository.setQwenKey(qwenKey)
                                    snackbar.showSnackbar("已保存")
                                }
                            }) { Text("保存", fontSize = 12.sp) }
                            TextButton(onClick = {
                                scope.launch {
                                    val result = runCatching { TranslateService(settings).enToZh("silent") }
                                    snackbar.showSnackbar(
                                        result.fold(
                                            onSuccess = { "翻译测试成功：silent → $it" },
                                            onFailure = { "翻译测试失败：${it.message}" }
                                        )
                                    )
                                }
                            }) { Text("测试翻译", fontSize = 12.sp) }
                            Spacer(Modifier.weight(1f))
                            Text(
                                settings.translateProviderLabel,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---------------- DeepSeek（解析） ----------------
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("DeepSeek API Key（生成解析）", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "扫描只出原文/题目；点「生成解析」时用 ${AiConfig.DEEPSEEK_MODEL} 写译文与解析，并核对 AI 答案与你的答案是否一致。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            if (settings.deepseekReady) "已配置" else "未配置",
                            fontSize = 11.sp,
                            color = if (settings.deepseekReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        OutlinedTextField(
                            value = deepseekKey,
                            onValueChange = { deepseekKey = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            label = { Text("DeepSeek API Key（sk-…）") },
                            singleLine = true
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                scope.launch {
                                    app.settingsRepository.setDeepSeekKey(deepseekKey)
                                    snackbar.showSnackbar("已保存")
                                }
                            }) { Text("保存", fontSize = 12.sp) }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "解析模型：${AiConfig.DEEPSEEK_MODEL}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---------------- 开源致谢 ----------------
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("开源致谢", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "本项目依赖的开源库、AI 服务、数据来源与作者",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { nav.navigate(Routes.credits()) }) { Text("查看", fontSize = 12.sp) }
                    }
                }
            }

            // ---------------- 内容管理 ----------------
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("内容管理", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        AssistChip(
                            onClick = { runCatching { picker.launch(arrayOf("*/*")) } },
                            label = { Text("导入试卷包") },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        if (hasPaper) {
                            TextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(top = 4.dp)) {
                                Text("清空这份试卷的作答记录", fontSize = 12.sp)
                            }
                            TextButton(onClick = { confirmDelete = true }) {
                                Text("删除这份试卷", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空作答记录？") },
            text = { Text("只清空本题卷的作答与答案显示状态，收藏本保留。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        app.studyRepository.clearStates(paperId)
                        snackbar.showSnackbar("已清空作答记录")
                    }
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这份试卷？") },
            text = { Text("试卷内容、作答、收藏会一起删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        app.examRepository.deletePaper(paperId)
                        nav.popBackStack()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}
