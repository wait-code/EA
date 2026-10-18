package com.examreader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.examreader.R
import com.examreader.data.ImportOutcome
import com.examreader.data.PaperSummary
import com.examreader.ui.nav.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(nav: NavController) {
    val app = rememberApp()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val papers by app.examRepository.observePapers().collectAsStateWithLifecycle(initialValue = emptyList())
    val hasDraft by app.draftRepository.hasDraft.collectAsStateWithLifecycle(initialValue = false)
    val snackbar = remember { SnackbarHostState() }
    var importing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PaperSummary?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            val label = uri.lastPathSegment?.substringAfterLast('/') ?: "所选文件"
            val outcome = if (bytes == null) {
                ImportOutcome(false, "", "", "读取文件失败（可能没有存储权限或文件已移动）")
            } else {
                app.examRepository.importFromUri(bytes, label)
            }
            importing = false
            snackbar.showSnackbar(
                if (outcome.ok) {
                    "导入成功：${outcome.title}（${outcome.sectionCount} 篇 / ${outcome.questionCount} 题）"
                } else {
                    "导入失败：${outcome.message}"
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.library_title), fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.collection()) }) {
                        Icon(Icons.Filled.Star, contentDescription = "收藏本")
                    }
                    IconButton(onClick = { nav.navigate(Routes.glossary()) }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "词库")
                    }
                    IconButton(onClick = { nav.navigate(Routes.settings("-")) }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreate = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "出卷或导入")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (papers.isEmpty() && !importing) {
                EmptyLibraryHint(modifier = Modifier.align(Alignment.Center))
            } else {
                AdaptiveLazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        AdaptiveSectionLabel(
                            "共 ${papers.size} 份试卷 · 点右下角「＋」扫描或导入"
                        )
                    }
                    if (hasDraft) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("有未保存的草稿", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            "扫描/编辑中的内容已自动保存，点此继续",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(onClick = { nav.navigate(Routes.editor("-")) }) {
                                        Text("继续", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    items(papers, key = { it.id }) { paper ->
                        PaperCard(
                            paper = paper,
                            onOpen = { nav.navigate(Routes.paper(paper.id)) },
                            onDelete = { pendingDelete = paper }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
            if (importing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    pendingDelete?.let { paper ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除试卷？") },
            text = {
                Text("「${paper.title}」中的生词本会被一并删除，无法恢复。")
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = paper.id
                    pendingDelete = null
                    scope.launch {
                        app.examRepository.deletePaper(id)
                        snackbar.showSnackbar("已删除")
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
    if (showCreate) {
        ModalBottomSheet(onDismissRequest = { showCreate = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                AssistChip(
                    onClick = {
                        showCreate = false
                        nav.navigate(Routes.scan())
                    },
                    label = { Text("使用AI扫描试卷") }
                )
                Spacer(Modifier.height(6.dp))
                AssistChip(
                    onClick = {
                        showCreate = false
                        runCatching { picker.launch(arrayOf("*/*")) }
                    },
                    label = { Text("导入试卷") }
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun EmptyLibraryHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("还没有试卷", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "点右下角「导入试卷包」选择 .epak 文件；\n也可以用 AI 扫描试卷自动生成后打包。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaperCard(paper: PaperSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onDelete),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(paper.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    if (paper.stage == "analyzed") "解析卷" else "扫描卷",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (paper.full.isNotBlank() && paper.full != paper.title) {
                Text(
                    paper.full,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (paper.paperLine.isNotBlank()) {
                Text(
                    paper.paperLine,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                StatBlock(paper.sectionCount.toString(), "篇目")
                StatBlock(paper.questionCount.toString(), "题目")
                StatBlock(paper.dictCount.toString(), "查词")
                if (paper.hasQuiz) StatBlock("有", "词卷")
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(onClick = onOpen, label = { Text("开始讲解") })
                Spacer(Modifier.weight(1f))
                Text(
                    "导入于 ${formatTime(paper.importedAt)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
