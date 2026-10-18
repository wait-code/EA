package com.examreader.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 草稿快照：编译后的试卷 + 思维导图 SVG + 来源。
 * 每 10 秒自动覆盖写进 `drafts` 表；保存正式试卷后清空。
 */
@Serializable
data class DraftSnapshot(
    val compiled: CExam,
    val mindmaps: Map<String, String> = emptyMap(),
    val sourceLabel: String = "应用内扫描",
    val savedAt: Long = 0L
)

class DraftRepository(private val db: AppDatabase) {
    private val dao = db.draft()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    val hasDraft: Flow<Boolean> = dao.observeCount().map { it > 0 }

    suspend fun save(compiled: CExam, mindmaps: Map<String, String>, sourceLabel: String): Long {
        val now = System.currentTimeMillis()
        val snapshot = DraftSnapshot(
            compiled = compiled,
            mindmaps = mindmaps,
            sourceLabel = sourceLabel,
            savedAt = now
        )
        dao.save(
            DraftEntity(
                id = "current",
                json = json.encodeToString(DraftSnapshot.serializer(), snapshot),
                updatedAt = now
            )
        )
        return now
    }

    suspend fun load(): DraftSnapshot? {
        val row = dao.load() ?: return null
        return runCatching { json.decodeFromString(DraftSnapshot.serializer(), row.json) }.getOrNull()
    }

    suspend fun clear() = dao.clear()
}
