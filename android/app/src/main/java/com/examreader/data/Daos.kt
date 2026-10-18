package com.examreader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {
    // ---- 试卷 ----
    @Query("SELECT * FROM papers ORDER BY importedAt DESC")
    fun observePapers(): Flow<List<PaperEntity>>

    @Query("SELECT * FROM papers ORDER BY importedAt DESC")
    suspend fun papers(): List<PaperEntity>

    @Query("SELECT * FROM papers WHERE id = :paperId")
    suspend fun paper(paperId: String): PaperEntity?

    @Query("SELECT COUNT(*) FROM papers")
    suspend fun paperCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaper(paper: PaperEntity): Long

    @Query("DELETE FROM papers WHERE id = :paperId")
    suspend fun deletePaper(paperId: String): Int

    // ---- 篇目 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(list: List<SectionEntity>): List<Long>

    @Query("SELECT * FROM sections WHERE paperId = :paperId ORDER BY ordinal")
    suspend fun sections(paperId: String): List<SectionEntity>

    @Query("SELECT * FROM sections WHERE paperId = :paperId ORDER BY ordinal")
    fun observeSections(paperId: String): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections WHERE paperId = :paperId AND id = :sectionId")
    suspend fun section(paperId: String, sectionId: String): SectionEntity?

    // ---- 段落 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParagraphs(list: List<ParagraphEntity>): List<Long>

    @Query("SELECT * FROM paragraphs WHERE paperId = :paperId AND sectionId = :sectionId ORDER BY ordinal")
    suspend fun paragraphs(paperId: String, sectionId: String): List<ParagraphEntity>

    // ---- 题目 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(list: List<QuestionEntity>): List<Long>

    @Query("SELECT * FROM questions WHERE paperId = :paperId AND sectionId = :sectionId ORDER BY ordinal")
    suspend fun questions(paperId: String, sectionId: String): List<QuestionEntity>

    @Query("SELECT COUNT(*) FROM questions WHERE paperId = :paperId")
    suspend fun questionCount(paperId: String): Int

    // ---- 篇目词汇 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocab(list: List<VocabEntity>): List<Long>

    @Query("SELECT * FROM section_vocab WHERE paperId = :paperId AND sectionId = :sectionId ORDER BY ordinal")
    suspend fun vocab(paperId: String, sectionId: String): List<VocabEntity>

    // ---- 词典 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDict(list: List<DictEntryEntity>): List<Long>

    @Query("SELECT * FROM dict_entries WHERE paperId = :paperId AND LOWER(word) = LOWER(:word) LIMIT 1")
    suspend fun lookup(paperId: String, word: String): DictEntryEntity?

    @Query("SELECT * FROM dict_entries WHERE paperId = :paperId AND LOWER(word) LIKE LOWER(:prefix || '%') ORDER BY LENGTH(word) LIMIT 20")
    suspend fun prefixSearch(paperId: String, prefix: String): List<DictEntryEntity>

    @Query("SELECT * FROM dict_entries WHERE paperId = :paperId ORDER BY word")
    suspend fun allDict(paperId: String): List<DictEntryEntity>

    // ---- 词汇检测卷 ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizParts(list: List<QuizPartEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizItems(list: List<QuizItemEntity>): List<Long>

    @Query("SELECT * FROM quiz_parts WHERE paperId = :paperId ORDER BY ordinal")
    suspend fun quizParts(paperId: String): List<QuizPartEntity>

    @Query("SELECT * FROM quiz_parts WHERE paperId = :paperId AND id = :partId")
    suspend fun quizPart(paperId: String, partId: String): QuizPartEntity?

    @Query("SELECT * FROM quiz_items WHERE paperId = :paperId AND partId = :partId ORDER BY ordinal")
    suspend fun quizItems(paperId: String, partId: String): List<QuizItemEntity>

    // ---- 导入日志 ----
    @Insert
    suspend fun insertImportLog(log: ImportLogEntity): Long

    @Query("SELECT * FROM import_log ORDER BY importedAt DESC LIMIT 50")
    suspend fun importLogs(): List<ImportLogEntity>
}

@Dao
interface StudyDao {
    @Query("SELECT * FROM study_state WHERE paperId = :paperId")
    fun observeStates(paperId: String): Flow<List<StudyStateEntity>>

    @Query("SELECT * FROM study_state WHERE paperId = :paperId")
    suspend fun states(paperId: String): List<StudyStateEntity>

    @Query("SELECT * FROM study_state WHERE paperId = :paperId AND questionId = :questionId")
    suspend fun state(paperId: String, questionId: String): StudyStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: StudyStateEntity): Long

    @Query("DELETE FROM study_state WHERE paperId = :paperId")
    suspend fun clearStates(paperId: String): Int

    // ---- 词卷作答 ----
    @Query("SELECT * FROM quiz_answers WHERE paperId = :paperId AND partId = :partId")
    fun observeQuizAnswers(paperId: String, partId: String): Flow<List<QuizAnswerEntity>>

    @Query("SELECT * FROM quiz_answers WHERE paperId = :paperId AND partId = :partId")
    suspend fun quizAnswers(paperId: String, partId: String): List<QuizAnswerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuizAnswer(answer: QuizAnswerEntity): Long

    @Query("DELETE FROM quiz_answers WHERE paperId = :paperId AND partId = :partId")
    suspend fun clearQuizAnswers(paperId: String, partId: String): Int

    // ---- 生词本 ----
    @Query("SELECT * FROM wordbook WHERE paperId = :paperId ORDER BY addedAt DESC")
    fun observeWordbook(paperId: String): Flow<List<WordbookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addWord(entry: WordbookEntity): Long

    @Query("DELETE FROM wordbook WHERE paperId = :paperId AND word = :word")
    suspend fun removeWord(paperId: String, word: String): Int

    // ---- 收藏句 ----
    @Query("SELECT * FROM sentences WHERE paperId = :paperId ORDER BY addedAt DESC")
    fun observeSentences(paperId: String): Flow<List<SentenceEntity>>

    @Insert
    suspend fun addSentence(sentence: SentenceEntity): Long

    @Query("DELETE FROM sentences WHERE id = :id")
    suspend fun removeSentence(id: Long): Int

    // ---- 收藏本（跨试卷，带来源试卷名，供检索） ----
    @Query(
        "SELECT w.paperId AS paperId, p.title AS paperTitle, w.word AS word, w.phonetic AS phonetic, " +
            "w.partOfSpeech AS partOfSpeech, w.zh AS zh, w.addedAt AS addedAt " +
            "FROM wordbook w INNER JOIN papers p ON p.id = w.paperId ORDER BY w.addedAt DESC"
    )
    fun observeCollectionWords(): Flow<List<CollectionWord>>

    @Query(
        "SELECT s.id AS id, s.paperId AS paperId, p.title AS paperTitle, s.questionId AS questionId, " +
            "s.text AS text, s.addedAt AS addedAt " +
            "FROM sentences s INNER JOIN papers p ON p.id = s.paperId ORDER BY s.addedAt DESC"
    )
    fun observeCollectionSentences(): Flow<List<CollectionSentence>>
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings")
    suspend fun all(): List<SettingEntity>

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity): Long

    @Query("DELETE FROM settings")
    suspend fun clear(): Int
}
@Dao
interface DraftDao {
    /** 草稿是否存在（用 COUNT 而不是可空 Long，Room 支持更稳） */
    @Query("SELECT COUNT(*) FROM drafts")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM drafts WHERE id = 'current'")
    suspend fun load(): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(draft: DraftEntity): Long

    @Query("DELETE FROM drafts")
    suspend fun clear(): Int
}

@Dao
interface GlossaryDao {
    @Query("SELECT * FROM glossary ORDER BY word")
    fun observeAll(): Flow<List<GlossaryEntity>>

    @Query("SELECT * FROM glossary WHERE word = :word LIMIT 1")
    suspend fun get(word: String): GlossaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: GlossaryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<GlossaryEntity>): List<Long>

    @Query("DELETE FROM glossary WHERE word = :word")
    suspend fun delete(word: String): Int

    @Query("SELECT COUNT(*) FROM glossary")
    suspend fun count(): Int
}