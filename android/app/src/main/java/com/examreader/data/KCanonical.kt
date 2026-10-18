package com.examreader.data

import kotlinx.serialization.Serializable

/**
 * vision 模型返回的 canonical 结构（与 framework 的 canonical schema 对齐）。
 * 只用于接收扫描结果；App 内部编辑与保存走 DraftModel + compiled。
 */
@Serializable
data class KMeta(
    val title: String = "",
    val sub: String = "",
    val full: String = "",
    val paperLine: String = "",
    val subject: String = "english",
    val language: String = "zh-CN"
)

@Serializable
data class KAnswer(val letter: String? = null, val text: String? = null, val zh: String? = null, val hint: String? = null)

@Serializable
data class KOption(val label: String = "", val text: String = "")

@Serializable
data class KSolution(val label: String = "", val text: String = "", val loc: String? = null)

@Serializable
data class KGroup(val key: String = "", val label: String = "", val items: List<String> = emptyList())

@Serializable
data class KAnalysis(
    val solutionLabel: String = "解析",
    val solutions: List<KSolution> = emptyList(),
    val groups: List<KGroup> = emptyList()
)

@Serializable
data class KQuestion(
    val no: String = "",
    val stem: String = "",
    val kind: String = "",
    val answer: KAnswer = KAnswer(),
    val options: List<KOption> = emptyList(),
    val analysis: KAnalysis = KAnalysis()
)

@Serializable
data class KPara(val heading: String? = null, val en: String = "", val zh: String = "")

@Serializable
data class KVocab(val word: String = "", val phonetic: String = "", val partOfSpeech: String = "", val zh: String = "")

@Serializable
data class KMigration(val title: String = "", val detail: String = "")

@Serializable
data class KOutline(val level: String = "l2", val text: String = "")

@Serializable
data class KSection(
    val id: String = "",
    val type: String = "read",
    val tabLabel: String = "",
    val chip: String = "",
    val title: String = "",
    val passageTitle: String = "",
    val topic: String = "",
    val qnos: List<String> = emptyList(),
    val paras: List<KPara> = emptyList(),
    val vocab: List<KVocab> = emptyList(),
    val migrations: List<KMigration> = emptyList(),
    val outline: List<KOutline> = emptyList(),
    val questions: List<KQuestion> = emptyList()
)

@Serializable
data class KQuizItem(
    val no: String = "",
    val answer: String = "",
    val phonetic: String? = null,
    val partOfSpeech: String? = null,
    val zh: String? = null,
    val pre: String = "",
    val post: String? = null
)

@Serializable
data class KQuizPart(
    val no: String = "",
    val name: String = "",
    val desc: String = "",
    val per: Double = 1.0,
    val box: List<String> = emptyList(),
    val segments: List<String> = emptyList(),
    val answers: List<String> = emptyList(),
    val items: List<KQuizItem> = emptyList()
)

@Serializable
data class KQuiz(val title: String = "", val total: Double = 0.0, val parts: List<KQuizPart> = emptyList())

@Serializable
data class KExam(
    val schemaVersion: Int = 1,
    val id: String = "",
    val meta: KMeta = KMeta(),
    val sections: List<KSection> = emptyList(),
    val dict: List<CDict> = emptyList(),
    val quiz: KQuiz? = null
)
