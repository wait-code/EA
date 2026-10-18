package com.examreader.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 词库（全局、跨试卷）仓库。
 * - 自动收录：点词查阅走机器翻译命中时，首次入库（source = machine）；
 * - 删除：按 word 删（不影响生词本 / 试卷词典）；
 * - 导入：读取外部 SQLite 词库文件（表规则见 [IMPORT_TABLES] 与 [IMPORT_COLUMNS]），
 *   兼容表名 glossary / words，兼容常见列名，已存在的单词跳过（幂等导入）。
 */
class GlossaryRepository(private val db: AppDatabase) {
    private val dao = db.glossary()

    /** 导入时兼容的表名（按优先级探测） */
    private val importTables = listOf("glossary", "words")

    /** 导入时兼容的列名 -> 标准字段（小写比较） */
    private val importColumns = mapOf(
        "word" to "word",
        "w" to "word",
        "phonetic" to "phonetic",
        "phone" to "phonetic",
        "phonetic_symbol" to "phonetic",
        "partofspeech" to "partOfSpeech",
        "pos" to "partOfSpeech",
        "zh" to "zh",
        "meaning" to "zh",
        "translation" to "zh",
        "source" to "source",
        "origin" to "source"
    )

    fun observeAll(): Flow<List<GlossaryEntity>> = dao.observeAll()

    suspend fun get(word: String): GlossaryEntity? = dao.get(normalize(word))

    suspend fun count(): Int = dao.count()

    /**
     * 首次查阅：机器翻译成功后自动存入词库。
     * 词库中已存在该词时不重复翻译/覆盖（保持首次释义）。
     */
    suspend fun recordMachineLookup(word: String, zh: String) {
        val key = normalize(word)
        if (key.isBlank()) return
        if (dao.get(key) != null) return
        val now = System.currentTimeMillis()
        dao.insert(
            GlossaryEntity(
                word = key,
                zh = zh,
                source = "machine",
                addedAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun delete(word: String) {
        dao.delete(normalize(word))
    }

    /** 外部 SQLite 词库文件导入结果 */
    data class ImportResult(
        val ok: Boolean,
        val imported: Int = 0,
        val skipped: Int = 0,
        val message: String
    )

    /**
     * 导入外部 SQLite 词库文件（.db / .sqlite / .db3）。
     * 表规则：
     *   1) 优先读取名为 `glossary` 的表，其次 `words`；
     *   2) 必须含 word 列（不区分大小写，兼容 word/w）；
     *   3) 可选列：phonetic(phone/phonetic_symbol)、partOfSpeech(pos)、zh(meaning/translation)、source(origin)；
     *   4) 已存在的单词跳过（不覆盖自动收录或之前导入的词）。
     */
    suspend fun importFromSqliteFile(file: File): ImportResult = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            return@withContext ImportResult(false, message = "文件不存在或为空")
        }
        var dbHandle: SQLiteDatabase? = null
        try {
            dbHandle = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            val table = dbHandle.pickImportTable() ?: return@withContext ImportResult(
                false,
                message = "文件里找不到词库表（需要表名 glossary 或 words，且至少包含 word 列）"
            )
            val columns = dbHandle.columnMap(table) ?: return@withContext ImportResult(
                false,
                message = "词库表「$table」缺少 word 列，无法导入"
            )
            val rows = dbHandle.readRows(table, columns)
            val now = System.currentTimeMillis()
            val entries = rows.map { row ->
                GlossaryEntity(
                    word = normalize(row["word"].toString()),
                    phonetic = row["phonetic"].orEmpty(),
                    partOfSpeech = row["partOfSpeech"].orEmpty(),
                    zh = row["zh"].orEmpty(),
                    source = (row["source"] as? String)?.takeIf { it.isNotBlank() } ?: "import",
                    addedAt = now,
                    updatedAt = now
                )
            }.filter { it.word.isNotBlank() }
            if (entries.isEmpty()) {
                ImportResult(ok = true, imported = 0, skipped = 0, message = "词库表为空，没有可导入的词条")
            } else {
                val before = dao.count()
                dao.insertAll(entries)
                val after = dao.count()
                val imported = (after - before).coerceAtLeast(0)
                val skipped = entries.size - imported
                ImportResult(
                    ok = true,
                    imported = imported,
                    skipped = skipped,
                    message = "导入成功：新增 $imported 个单词" + (if (skipped > 0) "，跳过已存在 $skipped 个" else "")
                )
            }
        } catch (e: Exception) {
            ImportResult(false, message = "导入失败：${e.message ?: "未知错误"}")
        } finally {
            runCatching { dbHandle?.close() }
            runCatching { file.delete() }
        }
    }

    /** 探测可导入的表（表名不区分大小写） */
    private fun SQLiteDatabase.pickImportTable(): String? {
        val tables = HashSet<String>()
        rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
        }
        val lower = tables.associateBy { it.lowercase() }
        return importTables.firstNotNullOfOrNull { lower[it] }
    }

    /** 探测表的列，映射到标准字段；至少需要 word 列 */
    private fun SQLiteDatabase.columnMap(table: String): Map<String, String>? {
        val real = ArrayList<String>()
        rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            while (cursor.moveToNext()) {
                real.add(cursor.getString(1))
            }
        }
        val map = real.mapNotNull { name ->
            val std = importColumns[name.lowercase()]
            if (std == null) null else std to name
        }.toMap()
        return if (map.containsKey("word")) map else null
    }

    /** 读取全部词条行（只取需要的列，容忍缺列） */
    private fun SQLiteDatabase.readRows(table: String, columns: Map<String, String>): List<Map<String, String?>> {
        val cols = listOf("word", "phonetic", "partOfSpeech", "zh", "source")
            .filter { columns.containsKey(it) }
        val select = cols.joinToString(",") { "`${columns.getValue(it)}`" }
        val list = ArrayList<Map<String, String?>>()
        rawQuery("SELECT $select FROM `$table`", null).use { cursor ->
            while (cursor.moveToNext()) {
                val row = HashMap<String, String?>()
                cols.forEachIndexed { index, std ->
                    row[std] = cursor.getStringOrNull(index)
                }
                list.add(row)
            }
        }
        return list
    }

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    /** word 规范化：去首尾空白 + 小写，保证主键唯一 */
    private fun normalize(word: String): String = word.trim().lowercase()
}
