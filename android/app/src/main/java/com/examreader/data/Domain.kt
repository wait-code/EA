package com.examreader.data

import java.io.File

/** 界面用的轻量视图模型（由 Room 行 + JSON 反序列化组装） */

data class PaperSummary(
    val id: String,
    val title: String,
    val full: String,
    val paperLine: String,
    val sourceLabel: String,
    val importedAt: Long,
    /** scanned = 扫描卷；analyzed = 解析卷 */
    val stage: String = "analyzed",
    val sectionCount: Int,
    val questionCount: Int,
    val dictCount: Int,
    val hasQuiz: Boolean,
    val hasMindmap: Boolean
)

data class SectionSummary(
    val id: String,
    val ordinal: Int,
    val type: String,
    val tabLabel: String,
    val chip: String,
    val title: String,
    val passageTitle: String,
    val topic: String,
    val qnos: List<String>,
    val questionCount: Int,
    val vocabCount: Int,
    val hasMindmap: Boolean
)

data class SectionDetail(
    val paperId: String,
    val id: String,
    val type: String,
    val tabLabel: String,
    val chip: String,
    val title: String,
    val passageTitle: String,
    val topic: String,
    val paragraphs: List<CParagraph>,
    val questions: List<CQuestion>,
    val vocab: List<CVocab>,
    val migrations: List<CMigration>,
    val outline: List<COutline>,
    val mindmapFile: File?
)

data class QuizPartDetail(
    val paperId: String,
    val id: String,
    val no: String,
    val name: String,
    val desc: String,
    val perScore: Double,
    val box: List<String>,
    val passage: List<CSeg>,
    val answers: List<String>,
    val items: List<CQuizItem>
)

data class ImportOutcome(
    val ok: Boolean,
    val paperId: String,
    val title: String,
    val message: String,
    val sectionCount: Int = 0,
    val questionCount: Int = 0
)

/** 收藏本（跨试卷）：生词 */
data class CollectionWord(
    val paperId: String,
    val paperTitle: String,
    val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val zh: String,
    val addedAt: Long
)

/** 收藏本（跨试卷）：句子 */
data class CollectionSentence(
    val id: Long,
    val paperId: String,
    val paperTitle: String,
    val questionId: String?,
    val text: String,
    val addedAt: Long
)
