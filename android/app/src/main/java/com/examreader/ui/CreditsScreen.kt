package com.examreader.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

private data class Credit(
    val name: String,
    val role: String,
    val url: String? = null,
    val license: String? = null
)

private val CREDITS = listOf(
    Credit(name = "Mr.Guo", role = "English teacher"),
    Credit(name = "DjangoDancer", role = "作者 ", url = "https://github.com/wait-code"),
    Credit(name = "DeepSeek Harness", role = "探索未知之境", url = "https://www.deepseek.com/harness/"),
    Credit(
        name = "Kotlin & Jetpack Compose",
        role = "Android 原生界面(优雅到极致)",
        url = "https://kotlinlang.org",
        license = "Apache-2.0"
    ),
    Credit(
        name = "AndroidX / Material 3",
        role = "系统组件、Navigation、Material 3 主题(伟大无需多言)",
        url = "https://developer.android.com/jetpack/androidx",
        license = "Apache-2.0"
    ),
    Credit(
        name = "Room",
        role = "本地 SQLite 数据库",
        url = "https://developer.android.com/training/data-storage/room",
        license = "Apache-2.0"
    ),
    Credit(
        name = "Coil",
        role = "思维导图 SVG 与图片加载",
        url = "https://github.com/coil-kt/coil",
        license = "Apache-2.0"
    ),
    Credit(
        name = "kotlinx.serialization",
        role = "JSON 解析与序列化",
        url = "https://github.com/Kotlin/kotlinx.serialization",
        license = "Apache-2.0"
    ),
    Credit(name = "通义千问", role = "扫描", url = "https://bailian.console.aliyun.com"),
    Credit(name = "DeepSeek", role = "分析", url = "https://platform.deepseek.com"),
)


private const val LINUS_QUOTE =
    "In many ways, I actually think the real idea of open source is for it to allow everybody to " +
        "be 'selfish', not about trying to get everybody to contribute to some common good. In other " +
        "words, I do not see open source as some big goody-goody 'let's all sing kumbaya around the " +
        "campfire and make the world a better place'. No, open source only really works if everybody " +
        "is contributing for their own selfish reasons."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(nav: NavController) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开源致谢", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        // 响应式：手机满宽，平板/横屏限宽居中；字号放大时文字自动换行、卡片自动变高
        AdaptiveLazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Linus Torvalds",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            LINUS_QUOTE,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "— Linus Torvalds",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { openUrl("https://github.com/torvalds") }) {
                                Text("打开", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "感谢以下项目、服务与作者",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            items(CREDITS) { credit ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(credit.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                credit.role,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!credit.license.isNullOrBlank()) {
                                Text(
                                    "License: ${credit.license}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        credit.url?.let { url ->
                            TextButton(onClick = { openUrl(url) }) { Text("打开", fontSize = 12.sp) }
                        }
                    }
                }
            }


        }
    }
}
