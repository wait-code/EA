package com.examreader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * .epak 包内 exam.json 的数据结构（由 framework 编译产出）。
 * 注意：与 framework/src/compile.mjs 的 compiled 结构一一对应，改一边要同步另一边。
 */
@Serializable
data class CSeg(val t: String = "text", val v: String? = null, val no: String? = null, val hint: String? = null)

@Serializable
data class CCounts(
    val sections: Int = 0,
    val questions: Int = 0,
    val paragraphs: Int = 0,
    val sectionVocab: Int = 0,
    val dict: Int = 0,
    val quizParts: Int = 0,
    val quizItems: Int = 0,
    val mindmaps: Int = 0
)

@Serializable
data class CMeta(
    val title: String = "",
    val sub: String = "",
    val full: String = "",
    val paperLine: String = "",
    val subject: String = "",
    val language: String = "zh-CN"
)

@Serializable
data class CAnswer(
    val letter: String? = null,
    val text: String? = null,
    val zh: String? = null,
    val hint: String? = null
)

@Serializable
data class COption(val id: String = "", val label: String = "", val text: String = "", val index: Int = 0)

@Serializable
data class CSolution(
    val id: String = "",
    val index: Int = 0,
    val label: String = "",
    val text: List<CSeg> = emptyList(),
    val loc: String? = null
)

@Serializable
data class CGroupItem(val id: String = "", val index: Int = 0, val text: List<CSeg> = emptyList())

@Serializable
data class CGroup(
    val id: String = "",
    val index: Int = 0,
    val key: String = "",
    val label: String = "",
    val items: List<CGroupItem> = emptyList()
)

@Serializable
data class CAnalysis(
    val solutionLabel: String = "解析",
    val solutions: List<CSolution> = emptyList(),
    val groups: List<CGroup> = emptyList()
)

@Serializable
data class CQuestion(
    val id: String,
    val no: String = "",
    val index: Int = 0,
    val stem: List<CSeg> = emptyList(),
    val kind: String = "",
    val answer: CAnswer = CAnswer(),
    val options: List<COption> = emptyList(),
    val analysis: CAnalysis = CAnalysis()
)

@Serializable
data class CParagraph(
    val index: Int = 0,
    val heading: String? = null,
    val en: List<CSeg> = emptyList(),
    val zh: List<CSeg> = emptyList()
)

@Serializable
data class CVocab(
    val id: String = "",
    val index: Int = 0,
    val word: String = "",
    val phonetic: String = "",
    val partOfSpeech: String = "",
    val zh: String = ""
)

@Serializable
data class CMigration(val id: String = "", val index: Int = 0, val title: String = "", val detail: String = "")

@Serializable
data class COutline(val index: Int = 0, val level: String = "l2", val text: String = "")

@Serializable
data class CMindmap(val file: String = "")

@Serializable
data class CSection(
    val id: String,
    val index: Int = 0,
    val type: String = "read",
    val tabLabel: String = "",
    val chip: String = "",
    val title: String = "",
    val passageTitle: String = "",
    val topic: String = "",
    val qnos: List<String> = emptyList(),
    val paras: List<CParagraph> = emptyList(),
    val vocab: List<CVocab> = emptyList(),
    val migrations: List<CMigration> = emptyList(),
    val outline: List<COutline> = emptyList(),
    val questions: List<CQuestion> = emptyList(),
    val mindmap: CMindmap? = null
)

@Serializable
data class CDict(
    val word: String = "",
    val phonetic: String = "",
    val partOfSpeech: String = "",
    val zh: String = ""
)

@Serializable
data class CQuizItem(
    val id: String = "",
    val index: Int = 0,
    val no: String = "",
    val answer: String = "",
    val phonetic: String? = null,
    val partOfSpeech: String? = null,
    val zh: String? = null,
    val pre: List<CSeg> = emptyList(),
    val post: String? = null
)

@Serializable
data class CQuizPart(
    val id: String = "",
    val index: Int = 0,
    val no: String = "",
    val name: String = "",
    val desc: String = "",
    val per: Double = 1.0,
    val box: List<String> = emptyList(),
    val passage: List<CSeg> = emptyList(),
    val answers: List<String> = emptyList(),
    val items: List<CQuizItem> = emptyList()
)

@Serializable
data class CQuiz(val title: String = "", val total: Double = 0.0, val parts: List<CQuizPart> = emptyList())

@Serializable
data class CExam(
    val schemaVersion: Int = 1,
    val id: String,
    val stage: String = "analyzed",
    val meta: CMeta = CMeta(),
    val sections: List<CSection> = emptyList(),
    val dict: List<CDict> = emptyList(),
    val quiz: CQuiz? = null,
    val counts: CCounts? = null
)

@Serializable
data class CManifestFiles(
    val exam: String = "exam.json",
    val mindmaps: List<String> = emptyList()
)

@Serializable
data class CManifest(
    val format: String = "",
    val formatVersion: Int = 1,
    val id: String = "",
    val title: String = "",
    val full: String = "",
    val builtAt: String = "",
    val generator: String = "",
    val counts: CCounts? = null,
    val files: CManifestFiles? = null
)

/** 段落 / 解析里的行内标记列表 <-> JSON 字符串 */
object SegCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val segsCodec = ListSerializer(CSeg.serializer())
    private val strListCodec = ListSerializer(String.serializer())
    private val optionsCodec = ListSerializer(COption.serializer())

    fun encode(list: List<CSeg>): String = json.encodeToString(segsCodec, list)

    fun decode(text: String?): List<CSeg> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(segsCodec, text) }.getOrDefault(emptyList())
    }

    fun encodeStrings(list: List<String>): String = json.encodeToString(strListCodec, list)

    fun decodeStrings(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(strListCodec, text) }.getOrDefault(emptyList())
    }

    fun encodeOptions(list: List<COption>): String = json.encodeToString(optionsCodec, list)

    fun decodeOptions(text: String?): List<COption> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(optionsCodec, text) }.getOrDefault(emptyList())
    }

    fun encodeAnswer(answer: CAnswer): String = json.encodeToString(CAnswer.serializer(), answer)

    fun decodeAnswer(text: String?): CAnswer {
        if (text.isNullOrBlank()) return CAnswer()
        return runCatching { json.decodeFromString(CAnswer.serializer(), text) }.getOrDefault(CAnswer())
    }

    fun encodeAnalysis(analysis: CAnalysis): String = json.encodeToString(CAnalysis.serializer(), analysis)

    fun decodeAnalysis(text: String?): CAnalysis {
        if (text.isNullOrBlank()) return CAnalysis()
        return runCatching { json.decodeFromString(CAnalysis.serializer(), text) }.getOrDefault(CAnalysis())
    }

    /** 纯文本（查词 / 搜索 / 复制） */
    fun toPlain(segs: List<CSeg>): String = buildString {
        segs.forEach { s ->
            when (s.t) {
                "blank" -> append(" ").append("____").append(" ")
                "br" -> append('\n')
                else -> append(s.v ?: "")
            }
        }
    }
}