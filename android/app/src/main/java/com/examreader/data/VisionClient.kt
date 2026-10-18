package com.examreader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.examreader.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream

/**
 * 第一阶段：Qwen-VL **只负责看图转文字**。
 *
 * 只抽取：篇目结构 / 英文原文 / 题干 / 选项 / 题号 / 重点词。
 * 不答题、不写解析、不翻译（这些交给第二阶段 DeepSeek）。
 * 图表类题目不支持：只转写文字，图片内容忽略。
 *
 * 模型名会变，所以内置候选自动探测（国内站/国际站），失败自动降级。
 */
class VisionClient(private val settings: AppSettings) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    suspend fun scan(
        context: Context,
        uris: List<Uri>,
        onProgress: (done: Int, total: Int, message: String) -> Unit
    ): KExam = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty()) { "至少选择一张试卷图片" }
        require(settings.qwenReady) { "还没填千问 API Key，去「设置 → 千问 API Key」填一下" }

        var merged: KExam? = null
        uris.forEachIndexed { index, uri ->
            onProgress(index, uris.size, "正在识别第 ${index + 1}/${uris.size} 张图片…")
            val dataUrl = runCatching { uriToDataUrl(context, uri) }
                .getOrElse { throw IllegalStateException("读取图片失败（第 ${index + 1} 张）：${it.message}") }
            val one = recognizeWithRepair(dataUrl)
            merged = if (merged == null) normalize(one) else merge(merged!!, normalize(one))
        }
        onProgress(uris.size, uris.size, "识别完成")
        val result = merged ?: throw IllegalStateException("模型没有返回可用的试卷结构")
        if (result.sections.isEmpty()) {
            throw IllegalStateException("模型没有识别出任何篇目。换更清晰的图片，或按「一篇一张图」切图重试。")
        }
        result
    }

    // ------------------------------------------------------------------ 识别 + 自修复

    private fun recognizeWithRepair(imageDataUrl: String): KExam {
        var parsed = request(imageDataUrl, USER_PROMPT)
        var errors = validateCanonical(parsed)
        if (errors.isEmpty()) return parsed

        val repaired = runCatching {
            request(imageDataUrl, REPAIR_PROMPT, previousJson = json.encodeToString(KExam.serializer(), parsed), errors = errors)
        }.getOrNull()
        if (repaired != null) {
            val newErrors = validateCanonical(repaired)
            if (newErrors.size < errors.size) parsed = repaired
        }
        return parsed
    }

    private fun request(
        imageDataUrl: String,
        userPrompt: String,
        previousJson: String? = null,
        errors: List<String> = emptyList()
    ): KExam {
        val userText = buildString {
            append(userPrompt)
            if (previousJson != null) {
                append("\n\n上一次的输出：\n")
                append(previousJson)
                append("\n\n校验发现这些问题，请只修正这些地方后重新输出完整 JSON：\n")
                errors.take(20).forEach { append("- ").append(it).append('\n') }
            }
        }

        var lastError: String? = null
        var attempts = 0
        while (attempts <= AiConfig.VISION_CANDIDATES.size) {
            attempts += 1
            val target = ensureTarget()
            try {
                return requestOnce(imageDataUrl, userText, target)
            } catch (e: IllegalStateException) {
                if (isModelMissing(e.message)) {
                    QwenVisionTarget.markFailed(target.second)
                    lastError = e.message
                } else {
                    throw e
                }
            }
        }
        throw IllegalStateException(
            "没有可用的视觉模型。已尝试：${QwenVisionTarget.failedList().joinToString()}。" +
                (lastError?.let { "最后错误：$it。" } ?: "") +
                "请到百炼控制台确认已开通视觉模型。"
        )
    }

    private fun requestOnce(imageDataUrl: String, userText: String, target: Pair<String, String>): KExam {
        val body = buildJsonObject {
            put("model", target.second)
            put("temperature", 0.1)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", userText)
                        }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", imageDataUrl) }
                        }
                    }
                }
            }
        }.toString()

        val response = Http.postJson(
            url = target.first + "/chat/completions",
            headers = mapOf("Authorization" to "Bearer ${settings.qwenApiKey}"),
            body = body,
            connectTimeoutMs = 20_000,
            readTimeoutMs = 240_000
        )
        val content = runCatching {
            json.parseToJsonElement(response).jsonObject["choices"]!!
                .jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.contentOrNull
        }.getOrNull() ?: throw IllegalStateException("千问返回格式异常：${response.take(300)}")

        val payload = extractJson(content) ?: throw IllegalStateException("模型没有返回 JSON：${content.take(300)}")
        return runCatching { json.decodeFromString(KExam.serializer(), payload) }
            .getOrElse { throw IllegalStateException("模型返回的 JSON 结构不合法：${it.message}") }
    }

    private fun ensureTarget(): Pair<String, String> {
        QwenVisionTarget.get()?.let { return it }
        for ((base, model) in AiConfig.VISION_CANDIDATES) {
            if (QwenVisionTarget.hasFailed(model)) continue
            val body = """{"model":${JsonStr.quote(model)},"messages":[{"role":"user","content":"ping"}],"max_tokens":1}"""
            try {
                Http.postJson(
                    url = base + "/chat/completions",
                    headers = mapOf("Authorization" to "Bearer ${settings.qwenApiKey}"),
                    body = body,
                    connectTimeoutMs = 15_000,
                    readTimeoutMs = 45_000
                )
                val target = base to model
                QwenVisionTarget.set(target)
                return target
            } catch (e: Exception) {
                val message = e.message ?: ""
                when {
                    isModelMissing(message) -> {
                        QwenVisionTarget.markFailed(model)
                        continue
                    }

                    message.contains("HTTP 401") || message.contains("HTTP 403") -> continue
                    else -> throw IllegalStateException(
                        "模型连通性检查失败：$message（先检查 Key 是否正确 / 是否欠费 / 网络是否通）"
                    )
                }
            }
        }
        throw IllegalStateException(
            "当前 Key 下找不到可用的百炼视觉模型。已尝试：${QwenVisionTarget.failedList().joinToString()}。" +
                "请确认：① Key 复制完整 ② 百炼里已开通视觉模型 ③ 国内站/国际站 Key 与站点匹配。"
        )
    }

    private fun isModelMissing(message: String?): Boolean {
        val text = message ?: return false
        return text.startsWith("HTTP 404") ||
            text.contains("model_not_found") ||
            text.contains("Model not exist") ||
            text.contains("模型不存在") ||
            text.contains("ModelNotExist")
    }

    private fun extractJson(text: String?): String? {
        if (text.isNullOrBlank()) return null
        var body = text.trim()
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(body)
        if (fenced != null) body = fenced.groupValues[1]
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return body.substring(start, end + 1)
    }

    /** 第一阶段只查「结构是否可用」：答案/解析/译文缺失都不算错 */
    private fun validateCanonical(exam: KExam): List<String> {
        val errors = mutableListOf<String>()
        if (exam.sections.isEmpty()) errors.add("sections 为空：没有识别出任何篇目")
        val allNos = mutableSetOf<String>()
        exam.sections.forEach { section ->
            if (section.id.isBlank()) errors.add("有 section 缺少 id（用 A/B/C/S/Z/G）")
            if (section.paras.isEmpty()) errors.add("篇目 ${section.id} 没有 paras（英文原文段落）")
            section.paras.forEachIndexed { i, p ->
                if (p.en.isBlank()) errors.add("篇目 ${section.id} 第 ${i + 1} 段 en 为空")
            }
            if (section.questions.isEmpty()) errors.add("篇目 ${section.id} 没有 questions")
            section.questions.forEach { q ->
                if (q.no.isBlank()) errors.add("篇目 ${section.id} 有题目缺少 no")
                else if (!allNos.add(q.no)) errors.add("题号 ${q.no} 在整卷重复出现")
                val labels = q.options.map { it.label }
                if (labels.size != labels.distinct().size) errors.add("第 ${q.no} 题选项字母重复")
                q.options.forEach { o ->
                    if (o.text.isBlank()) errors.add("第 ${q.no} 题选项 ${o.label} 内容为空")
                }
            }
            val blankNos = section.paras.flatMap { p ->
                Regex("""\{\{\s*(\d+)""").findAll(p.en).map { it.groupValues[1] }.toList()
            }.distinct()
            if (blankNos.isNotEmpty()) {
                val missing = blankNos.filter { it !in section.questions.map { q -> q.no } }
                if (missing.isNotEmpty()) {
                    errors.add("篇目 ${section.id} 原文有 ${missing.joinToString(",")} 空位但没有对应题目")
                }
            }
        }
        return errors
    }

    // ------------------------------------------------------------------ 归一 / 合并

    private fun normalize(exam: KExam): KExam {
        val meta = exam.meta.copy(
            full = exam.meta.full.ifBlank { exam.meta.title.ifBlank { "未命名试卷" } },
            title = exam.meta.title.ifBlank { "未命名试卷" }
        )
        val sections = exam.sections.mapIndexed { index, section ->
            var id = section.id.ifBlank { ('A' + index).toString() }
            id = id.take(8)
            section.copy(
                id = id,
                title = section.title.ifBlank { section.passageTitle.ifBlank { "第 $id 篇" } },
                chip = section.chip.ifBlank { id },
                qnos = if (section.qnos.isNotEmpty()) section.qnos else section.questions.map { it.no },
                questions = section.questions.map { q ->
                    q.copy(
                        no = q.no.filter { it.isDigit() }.ifBlank { q.no },
                        // 第一阶段：答案与解析一律清空，等第二阶段生成
                        answer = KAnswer(),
                        analysis = KAnalysis()
                    )
                }
            )
        }
        val id = exam.id.ifBlank { "exam-" + System.currentTimeMillis().toString().takeLast(8) }
        return exam.copy(id = normalizeSlug(id), meta = meta, sections = sections)
    }

    private fun merge(a: KExam, b: KExam): KExam {
        val byId = a.sections.associateBy { it.id }.toMutableMap()
        b.sections.forEach { incoming ->
            val exist = byId[incoming.id]
            if (exist == null) {
                byId[incoming.id] = incoming
            } else {
                val qByNo = exist.questions.associateBy { it.no }.toMutableMap()
                incoming.questions.forEach { qByNo[it.no] = it }
                byId[incoming.id] = exist.copy(
                    paras = if (exist.paras.isNotEmpty()) exist.paras else incoming.paras,
                    questions = qByNo.values.sortedBy { it.no.toIntOrNull() ?: 0 },
                    vocab = (exist.vocab + incoming.vocab).distinctBy { it.word },
                    passageTitle = exist.passageTitle.ifBlank { incoming.passageTitle }
                )
            }
        }
        val dict = (a.dict + b.dict).distinctBy { it.word }
        val quiz = when {
            a.quiz == null -> b.quiz
            b.quiz == null -> a.quiz
            else -> a.quiz.copy(parts = (a.quiz.parts + b.quiz.parts).distinctBy { it.no })
        }
        return a.copy(sections = byId.values.sortedBy { it.id }, dict = dict, quiz = quiz)
    }

    private fun normalizeSlug(id: String): String {
        val cleaned = id.lowercase().replace(Regex("[^a-z0-9-]"), "-").trim('-')
        val base = cleaned.ifBlank { "exam" }
        return if (base.length < 3) base + "-paper" else base.take(64)
    }

    // ------------------------------------------------------------------ 图片

    private fun uriToDataUrl(context: Context, uri: Uri, maxSide: Int = 1600): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("打不开图片")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalStateException("不是有效图片")

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
        }) ?: throw IllegalStateException("图片解码失败")

        val longest = maxOf(decoded.width, decoded.height)
        val bitmap = if (longest > maxSide) {
            val ratio = maxSide.toFloat() / longest
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (bitmap !== decoded) bitmap.recycle()
        decoded.recycle()
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        private val SCHEMA_EXAMPLE = """
{
  "schemaVersion": 1,
  "id": "school-2026-11-english",
  "meta": {"title":"高三英语月考","full":"某某中学 2026 年 11 月月考英语","paperLine":"满分 150 分 · 时间 120 分钟","subject":"english","language":"zh-CN"},
  "sections": [
    {
      "id": "Z", "type": "cloze", "tabLabel": "完形填空", "chip": "Z · 41–55",
      "title": "完形填空", "passageTitle": "Silence speaks louder", "topic": "语篇主题一句话",
      "qnos": ["41","42"],
      "paras": [
        {"en":"In a world filled with noise, silence can become a universal language. The saying {{41}} a modern example.","zh":""}
      ],
      "vocab": [{"word":"universal","phonetic":"/ˌjuːnɪˈvɜːsl/","partOfSpeech":"adj.","zh":"通用的；普遍的"}],
      "questions": [
        {
          "no":"41","stem":"","kind":"动词辨析",
          "options":[{"label":"A","text":"needs"},{"label":"B","text":"lacks"},{"label":"C","text":"finds"},{"label":"D","text":"sets"}]
        }
      ]
    }
  ]
}
"""

        private val SYSTEM_PROMPT = """
你是试卷数字化专家。任务：把试卷图片里的**文字内容**如实转写成结构化 JSON，供后续另一个模型写解析。

⚠️ 你只做「转写」：不答题、不写解析、不翻译。

# 输出纪律
1. 只输出一个 JSON 对象。不要 markdown 代码块、不要解释、不要前后缀。
2. 字段名、层级严格按示例；没有内容的字段用空字符串 / 空数组。

# 硬性规则
1. 一篇 = 一个 section。type 只能取：read（阅读）、seven（七选五）、cloze（完形）、gram（语法填空）。
2. 挖空题必须把空位写进 paras[].en：完形/七选五写 {{题号}}；语法填空若原卷给了提示词写 {{题号|提示词}}，例如 {{56|it}}。
   空位编号必须与 questions[].no 完全一致。
3. 选择题：给全部 options（A/B/C/D 或 A–G）与题干；**不要给 answer 字段**（答案留空）。
4. **不要写 analysis（解析）**；**不要写中文翻译**（paras[].zh 一律留空）。
5. paras 必须逐段拆开，一段一个元素；不摘要、不合并、不改写原文。
6. 题号用原卷题号，不要重排。
7. 看不清的字符用 [?] 标注，绝不编造原文。
8. 图表/图片类题目：只转写能看到的文字，图片内容无法转写就跳过，不要瞎猜。
9. vocab 可给本篇 5–10 个重点词（word/phonetic/partOfSpeech/zh），这是唯一允许写中文的地方。
10. 一张图有多篇就输出多个 section；图上没有的内容不要补。

# 结构示例（字段名以此为准）
$SCHEMA_EXAMPLE
"""

        private val USER_PROMPT = """
识别这张试卷图片，输出 JSON。只做结构转写，特别注意：
- 每段英文原文是否完整（不要漏段、不要把两段合成一段）。
- 空位是否都写成 {{题号}}（语法填空带提示词写成 {{题号|提示词}}）。
- 每道题是否都有题号、题干（完形可空）、全部选项。
- 不要给答案、不要写解析、不要写中文翻译。
"""

        private val REPAIR_PROMPT = """
你上一次输出的 JSON 有结构问题（见下方错误清单）。请只修正这些问题，其余内容保持不变，重新输出完整 JSON。
注意：只做文字转写；不要补答案、不要写解析、不要写翻译。
"""
    }
}

/** 探测结果：站点 + 模型（进程内缓存，避免每张图都重新探测） */
private object QwenVisionTarget {
    @Volatile
    private var base: String? = null

    @Volatile
    private var model: String? = null

    private val failed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun get(): Pair<String, String>? {
        val b = base ?: return null
        val m = model ?: return null
        return b to m
    }

    fun set(target: Pair<String, String>) {
        base = target.first
        model = target.second
    }

    fun markFailed(failedModel: String) {
        failed.add(failedModel)
        if (model == failedModel) {
            base = null
            model = null
        }
    }

    fun hasFailed(candidate: String): Boolean = failed.contains(candidate)

    fun failedList(): List<String> = failed.toList()
}
