package com.examreader.data

import com.examreader.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 英中翻译：只用通义千问（`qwen-plus`，自动降级 `qwen-turbo`），与试卷识别共用同一个 API Key。
 * 不再接入阿里云机器翻译等按次计费的翻译服务，成本只跟 token 走。
 * 模型名/站点同样走候选列表，遇到 404 自动换下一个。
 */
class TranslateService(private val settings: AppSettings) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val available: Boolean get() = settings.qwenReady

    val providerLabel: String get() = "千问翻译"

    suspend fun enToZh(text: String): String = withContext(Dispatchers.IO) {
        val content = text.trim()
        require(content.isNotEmpty()) { "内容为空，没什么可翻译的" }
        require(settings.qwenReady) { "还没填千问 API Key，去「设置 → 千问 API Key」填一下（翻译也用它）" }

        var lastError: String? = null
        for ((base, model) in AiConfig.TEXT_CANDIDATES) {
            try {
                return@withContext translate(base, model, content)
            } catch (e: Exception) {
                val message = e.message ?: ""
                when {
                    isModelMissing(message) -> {
                        lastError = message
                        continue // 换下一个模型
                    }

                    message.contains("HTTP 401") || message.contains("HTTP 403") -> {
                        lastError = message
                        continue // 换站点（国内 / 国际）
                    }

                    else -> throw e
                }
            }
        }
        throw IllegalStateException(
            "翻译失败：没有可用的千问文本模型。" +
                (lastError?.let { "最后错误：$it。" } ?: "") +
                "请确认 Key 有效、百炼已开通 qwen-plus。"
        )
    }

    private fun translate(baseUrl: String, model: String, text: String): String {
        val prompt = "把下面的英文翻译成自然、准确的中文。只输出译文本身，不要解释、不要加引号。\n\n$text"
        val body = buildString {
            append("{\"model\":")
            append(JsonStr.quote(model))
            append(",\"temperature\":0.2,\"messages\":[{\"role\":\"user\",\"content\":")
            append(JsonStr.quote(prompt))
            append("}]}")
        }
        val response = Http.postJson(
            url = baseUrl + "/chat/completions",
            headers = mapOf("Authorization" to "Bearer ${settings.qwenApiKey}"),
            body = body,
            readTimeoutMs = 60_000
        )
        val content = runCatching {
            json.parseToJsonElement(response).jsonObject["choices"]!!.jsonArray[0]
                .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.contentOrNull
        }.getOrNull()
        return content?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("千问翻译返回为空")
    }

    private fun isModelMissing(message: String): Boolean =
        message.startsWith("HTTP 404") ||
            message.contains("model_not_found") ||
            message.contains("Model not exist") ||
            message.contains("模型不存在")
}

/** 极简 JSON 字符串转义（不额外引依赖） */
object JsonStr {
    fun quote(value: String): String {
        val sb = StringBuilder("\"")
        value.forEach { ch ->
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
