package com.examreader.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SQLite（Room）表结构。
 * 试卷库 -> 篇目 -> 段落 / 题目 / 词汇，学习痕迹（作答、生词本、收藏句、词卷）单独成表。
 */

@Entity(tableName = "papers")
data class PaperEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sub: String,
    val full: String,
    val paperLine: String,
    val subject: String,
    val language: String,
    val schemaVersion: Int,
    val importedAt: Long,
    val sourceLabel: String,
    /** scanned = 扫描卷（无解析）；analyzed = 解析卷 */
    val stage: String = "analyzed",
    val sectionCount: Int,
    val questionCount: Int,
    val dictCount: Int,
    val hasQuiz: Boolean,
    val hasMindmap: Boolean
)

@Entity(
    tableName = "sections",
    primaryKeys = ["paperId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paperId")]
)
data class SectionEntity(
    val paperId: String,
    val id: String,
    val ordinal: Int,
    val type: String,
    val tabLabel: String,
    val chip: String,
    val title: String,
    val passageTitle: String,
    val topic: String,
    val qnosJson: String,
    val outlineJson: String,
    val migrationsJson: String,
    val questionCount: Int,
    val vocabCount: Int,
    val mindmapPath: String?
)

@Entity(
    tableName = "paragraphs",
    primaryKeys = ["paperId", "sectionId", "ordinal"],
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paperId"), Index("sectionId")]
)
data class ParagraphEntity(
    val paperId: String,
    val sectionId: String,
    val ordinal: Int,
    val heading: String?,
    val enJson: String,
    val zhJson: String
)

@Entity(
    tableName = "questions",
    primaryKeys = ["paperId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paperId"), Index("sectionId")]
)
data class QuestionEntity(
    val paperId: String,
    val id: String,
    val sectionId: String,
    val no: String,
    val ordinal: Int,
    val kind: String,
    val stemJson: String,
    val answerJson: String,
    val optionsJson: String,
    val analysisJson: String,
    val isChoice: Boolean
)

@Entity(
    tableName = "section_vocab",
    primaryKeys = ["paperId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paperId"), Index("sectionId")]
)
data class VocabEntity(
    val paperId: String,
    val id: String,
    val sectionId: String,
    val ordinal: Int,
    val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val zh: String
)

@Entity(
    tableName = "dict_entries",
    primaryKeys = ["paperId", "word"],
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paperId")]
)
data class DictEntryEntity(
    val paperId: String,
    val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val zh: String
)

@Entity(
    tableName = "quiz_parts",
    primaryKeys = ["paperId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paperId")]
)
data class QuizPartEntity(
    val paperId: String,
    val id: String,
    val ordinal: Int,
    val no: String,
    val name: String,
    val desc: String,
    val perScore: Double,
    val boxJson: String,
    val passageJson: String,
    val answersJson: String,
    val itemCount: Int
)

@Entity(
    tableName = "quiz_items",
    primaryKeys = ["paperId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paperId"), Index("partId")]
)
data class QuizItemEntity(
    val paperId: String,
    val id: String,
    val partId: String,
    val ordinal: Int,
    val no: String,
    val answer: String,
    val phonetic: String?,
    val partOfSpeech: String?,
    val zh: String?,
    val preJson: String,
    val post: String?
)

/** 选择题选了哪项 / 填空题填了什么 / 是否看过答案解析 */
@Entity(
    tableName = "study_state",
    primaryKeys = ["paperId", "questionId"],
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paperId")]
)
data class StudyStateEntity(
    val paperId: String,
    val questionId: String,
    val picked: String?,
    val filled: String,
    val answerShown: Boolean,
    val updatedAt: Long
)

@Entity(
    tableName = "quiz_answers",
    primaryKeys = ["paperId", "partId", "itemKey"],
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paperId")]
)
data class QuizAnswerEntity(
    val paperId: String,
    val partId: String,
    val itemKey: String,
    val input: String,
    val correct: Boolean,
    val updatedAt: Long
)

@Entity(
    tableName = "wordbook",
    primaryKeys = ["paperId", "word"],
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paperId")]
)
data class WordbookEntity(
    val paperId: String,
    val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val zh: String,
    val addedAt: Long
)

@Entity(
    tableName = "sentences",
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paperId")]
)
data class SentenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paperId: String,
    val questionId: String?,
    val text: String,
    val addedAt: Long
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "import_log")
data class ImportLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paperId: String,
    val title: String,
    val sourceLabel: String,
    val importedAt: Long,
    val countsJson: String,
    val ok: Boolean,
    val message: String
)
/** 未保存的扫描/编辑草稿（每 10 秒自动覆盖写入，只有一份） */
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val id: String,
    val json: String,
    val updatedAt: Long
)

/**
 * 词库（全局、跨试卷）：首次查阅走机器翻译的词自动收录，也可从外部 SQLite 词库导入。
 * 与「生词本 wordbook（按试卷、用户主动收藏）」/「试卷词典 dict_entries（随包导入）」相互独立：
 * 词库是查词兜底缓存 + 可积累可导入的全局词汇表，删除词库不影响生词本与试卷词典。
 * 表规则：表名 glossary；主键 word（小写规范化后存储）；字段 word/phonetic/partOfSpeech/zh/source/addedAt/updatedAt。
 */
@Entity(tableName = "glossary")
data class GlossaryEntity(
    @PrimaryKey val word: String,
    val phonetic: String = "",
    val partOfSpeech: String = "",
    val zh: String = "",
    /** 来源：machine = 首次查阅机器翻译自动收录；import = 外部 SQLite 词库导入 */
    val source: String = "machine",
    val addedAt: Long,
    val updatedAt: Long
)