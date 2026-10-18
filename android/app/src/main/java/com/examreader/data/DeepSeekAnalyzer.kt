package com.examreader.data

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

/**
 * DeepSeek：只负责「生成解析 + 译文」。
 * 输入：老师已确认的题目与答案（原卷原文、题干、选项、标准答案）
 * 输出：每段中文翻译 + 每题解析（先自己作答，若与老师答案不一致会如实给出自己的答案，App 负责提示「不符」）
 */
class DeepSeekAnalyzer(private val settings: AppSettings) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    val ready: Boolean get() = settings.deepseekReady

    suspend fun analyzeSection(
        sectionTitle: String,
        sectionType: String,
        passage: String,
        paragraphCount: Int,
        questions: List<CQuestion>
    ): AiSectionResult = withContext(Dispatchers.IO) {
        require(settings.deepseekReady) { "还没填 DeepSeek API Key，去「设置 → DeepSeek API Key」填一下" }

        val payload = buildJsonObject {
            put("sectionTitle", sectionTitle)
            put("sectionType", sectionType)
            put("paragraphCount", paragraphCount)
            put("passage", passage)
            putJsonArray("questions") {
                questions.forEach { q ->
                    addJsonObject {
                        put("no", q.no)
                        put("stem", Inline.plainText(q.stem).trim())
                        putJsonArray("options") {
                            q.options.forEach { o ->
                                addJsonObject {
                                    put("label", o.label)
                                    put("text", o.text)
                                }
                            }
                        }
                        put("teacherAnswer", q.answer.letter ?: q.answer.text ?: "")
                    }
                }
            }
        }.toString()

        val content = request(payload)
        parse(content, paragraphCount)
    }

    private fun request(payload: String): String {
        var lastError: String? = null
        AiConfig.DEEPSEEK_CANDIDATES.forEachIndexed { index, model ->
            val body = buildJsonObject {
                put("model", model)
                if (model != "deepseek-reasoner") put("temperature", 0.2)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", payload)
                    }
                }
            }.toString()
            try {
                val response = Http.postJson(
                    url = AiConfig.DEEPSEEK_BASE_URL + "/chat/completions",
                    headers = mapOf("Authorization" to "Bearer ${settings.deepseekApiKey}"),
                    body = body,
                    connectTimeoutMs = 20_000,
                    readTimeoutMs = 300_000
                )
                val content = runCatching {
                    json.parseToJsonElement(response).jsonObject["choices"]!!
                        .jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!
                        .jsonPrimitive.contentOrNull
                }.getOrNull()
                if (!content.isNullOrBlank()) return content
                lastError = "DeepSeek 返回为空"
            } catch (e: Exception) {
                lastError = e.message
                val msg = e.message ?: ""
                val retryNext = msg.startsWith("HTTP 404") ||
                    msg.contains("model_not_found") ||
                    msg.contains("Model not exist") ||
                    msg.contains("model_not_supported")
                if (!retryNext) throw e
            }
        }
        throw IllegalStateException("解析生成失败：${lastError ?: "没有可用模型"}")
    }

    private fun parse(content: String, paragraphCount: Int): AiSectionResult {
        val body = extractJson(content) ?: throw IllegalStateException("DeepSeek 没有返回 JSON：${content.take(300)}")
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw IllegalStateException("DeepSeek 返回的 JSON 无法解析")

        // 字段可能缺失 / 类型不符（模型偶尔给 null），全部做容错，避免整篇解析崩掉
        val parasZh = runCatching {
            root["parasZh"]?.jsonArray?.mapNotNull { it.str() }
        }.getOrNull().orEmpty()

        val questions = runCatching {
            root["questions"]?.jsonArray?.mapNotNull { element ->
                val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                val no = obj["no"].str() ?: return@mapNotNull null
                val aiAnswer = obj["aiAnswer"].str()
                val aiAnswerText = obj["aiAnswerText"].str()

                val analysis = runCatching { obj["analysis"]?.jsonObject }.getOrNull()
                val solutions = runCatching {
                    analysis?.get("solutions")?.jsonArray?.mapNotNull { s ->
                        val so = runCatching { s.jsonObject }.getOrNull() ?: return@mapNotNull null
                        AiSolution(
                            label = so["label"].str().orEmpty(),
                            text = so["text"].str().orEmpty()
                        )
                    }
                }.getOrNull().orEmpty()
                val groups = runCatching {
                    analysis?.get("groups")?.jsonArray?.mapNotNull { g ->
                        val go = runCatching { g.jsonObject }.getOrNull() ?: return@mapNotNull null
                        AiGroup(
                            key = go["key"].str().orEmpty(),
                            label = go["label"].str().orEmpty(),
                            items = runCatching {
                                go["items"]?.jsonArray?.mapNotNull { it.str() }
                            }.getOrNull().orEmpty()
                        )
                    }
                }.getOrNull().orEmpty()
                AiQuestionResult(
                    no = no,
                    aiAnswer = aiAnswer,
                    aiAnswerText = aiAnswerText,
                    solutions = solutions,
                    groups = groups
                )
            }
        }.getOrNull().orEmpty()

        // 段落译文数量不足时补齐空串（避免下标越界）
        val zh = List(paragraphCount) { i -> parasZh.getOrNull(i) ?: "" }
        return AiSectionResult(parasZh = zh, questions = questions)
    }

    /** 安全取字符串：模型可能返回 null / 数字 / 对象 */
    private fun kotlinx.serialization.json.JsonElement?.str(): String? =
        runCatching { this?.jsonPrimitive?.contentOrNull }.getOrNull()

    private fun extractJson(text: String): String? {
        var body = text.trim()
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(body)
        if (fenced != null) body = fenced.groupValues[1]
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return body.substring(start, end + 1)
    }

    companion object {
        private val SYSTEM_PROMPT = """
你是一位资深英语教研员，负责给一份已经扫描好的试卷写「精讲讲评」。老师已经提供了原文、题目、选项和标准答案。

# 你的任务
1. 先独立把每道题做一遍，得出**你自己的答案**（选择题给选项字母；填空题给答案词）。
2. 再给每道题写可直接讲给学生听的解析。
3. 给每一段英文原文写完整、自然的中文翻译。

# 输出格式（只输出一个 JSON，不要 markdown、不要解释）
{
  "parasZh": ["第 1 段中文翻译", "第 2 段中文翻译", "..."],
  "questions": [
    {
      "no": "21",
      "aiAnswer": "C",
      "aiAnswerText": "正确答案原文片段（可选）",
      "analysis": {
        "solutions": [
          {"label": "原文依据", "text": "英文原文定位句 + 中文解释"},
          {"label": "整句翻译", "text": "题干所涉原文的中文翻译"},
          {"label": "正确选项理由", "text": "为什么选 C，<span class=\"ans\">C. 选项内容</span> ..."}
        ],
        "groups": [
          {"key": "distract", "label": "干扰项排除", "items": ["<span class=\"ans\">A. ...</span> 为什么错", "..."]},
          {"key": "trap", "label": "易错点与规律", "items": ["..."]}
        ]
      }
    }
  ]
}

# 硬性规则
1. parasZh 的数量必须严格等于 paragraphCount。
2. questions 必须覆盖全部题目，no 用原题号。
3. **若你判断的答案与老师给的 teacherAnswer 不同，输出你自己的真实答案，不要迎合老师**；App 会把差异提示给老师。
4. 解析要具体：原文依据要引用英文原句；干扰项要逐个说明错因；不要写空话。
5. 解析文本可用 <span class="ans">正确项/干扰项</span>、<span class="q">考点短语</span> 做标记，不要用其他 HTML。
6. 不要编造原文中不存在的内容；信息不足时写「原文信息不足」，不要瞎补。
7. 语法填空/完形填空的答案按原题号给；七选五给选项字母。
"""
    }
}

data class AiSolution(val label: String, val text: String)

data class AiGroup(val key: String, val label: String, val items: List<String>)

data class AiQuestionResult(
    val no: String,
    val aiAnswer: String?,
    val aiAnswerText: String?,
    val solutions: List<AiSolution>,
    val groups: List<AiGroup>
)

data class AiSectionResult(
    val parasZh: List<String>,
    val questions: List<AiQuestionResult>
)
