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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.examreader.ui.nav.Routes
import kotlinx.coroutines.launch

/**
 * 收藏本（跨试卷）：把各卷的生词与句子汇总到一处，可搜索、能看到来自哪份试卷。
 * 每份试卷自己的「生词本 / 收藏句」入口保持不变。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(nav: NavController) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val words by app.studyRepository.observeCollectionWords()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val sentences by app.studyRepository.observeCollectionSentences()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val keyword = query.trim().lowercase()

    val filteredWords = remember(words, keyword) {
        if (keyword.isEmpty()) words
        else words.filter {
            it.word.lowercase().contains(keyword) ||
                it.zh.lowercase().contains(keyword) ||
                it.paperTitle.lowercase().contains(keyword)
        }
    }
    val filteredSentences = remember(sentences, keyword) {
        if (keyword.isEmpty()) sentences
        else sentences.filter {
            it.text.lowercase().contains(keyword) || it.paperTitle.lowercase().contains(keyword)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收藏本", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                singleLine = true,
                label = { Text("搜索单词 / 释义 / 句子 / 试卷名") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "清空")
                        }
                    }
                }
            )
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("生词（${filteredWords.size}）", fontSize = 13.sp) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("句子（${filteredSentences.size}）", fontSize = 13.sp) })
            }
            if (tab == 0) {
                if (filteredWords.isEmpty()) {
                    EmptyCollection("还没有收藏生词\n在原文里点单词即可加入")
                } else {
                    AdaptiveLazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredWords, key = { it.paperId + "#" + it.word }) { item ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(item.word, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                            if (item.phonetic.isNotBlank()) {
                                                Text(
                                                    "  " + item.phonetic,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Text(
                                            "${item.partOfSpeech} ${item.zh}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "来自：${item.paperTitle}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = {
                                        scope.launch { app.studyRepository.removeWord(item.paperId, item.word) }
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "移除")
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            } else {
                if (filteredSentences.isEmpty()) {
                    EmptyCollection("还没有收藏句子\n在题卡上点「收藏此句」")
                } else {
                    AdaptiveLazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredSentences, key = { it.id }) { item ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.text, fontSize = 14.sp)
                                        Text(
                                            "来自：${item.paperTitle}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = {
                                        scope.launch { app.studyRepository.removeSentence(item.id) }
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "移除")
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCollection(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
