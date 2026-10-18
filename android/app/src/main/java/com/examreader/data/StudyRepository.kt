package com.examreader.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 学习痕迹：作答状态 / 词卷答案 / 生词本 / 收藏句 */
class StudyRepository(private val db: AppDatabase) {
    private val dao = db.study()

    fun observeStates(paperId: String): Flow<Map<String, StudyStateEntity>> =
        dao.observeStates(paperId).map { list -> list.associateBy { it.questionId } }

    suspend fun state(paperId: String, questionId: String): StudyStateEntity =
        dao.state(paperId, questionId) ?: StudyStateEntity(paperId, questionId, null, "", false, 0L)

    suspend fun setPicked(paperId: String, questionId: String, picked: String?) {
        val old = state(paperId, questionId)
        dao.upsertState(old.copy(picked = picked, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setFilled(paperId: String, questionId: String, filled: String) {
        val old = state(paperId, questionId)
        dao.upsertState(old.copy(filled = filled, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setAnswerShown(paperId: String, questionId: String, shown: Boolean) {
        val old = state(paperId, questionId)
        dao.upsertState(old.copy(answerShown = shown, updatedAt = System.currentTimeMillis()))
    }

    suspend fun clearStates(paperId: String) = dao.clearStates(paperId)

    // ---- 词卷 ----
    fun observeQuizAnswers(paperId: String, partId: String): Flow<Map<String, QuizAnswerEntity>> =
        dao.observeQuizAnswers(paperId, partId).map { list -> list.associateBy { it.itemKey } }

    suspend fun saveQuizAnswer(paperId: String, partId: String, itemKey: String, input: String, correct: Boolean) {
        dao.upsertQuizAnswer(
            QuizAnswerEntity(paperId, partId, itemKey, input, correct, System.currentTimeMillis())
        )
    }

    suspend fun clearQuizAnswers(paperId: String, partId: String) = dao.clearQuizAnswers(paperId, partId)

    // ---- 收藏本（跨试卷） ----
    fun observeCollectionWords(): Flow<List<CollectionWord>> = dao.observeCollectionWords()

    fun observeCollectionSentences(): Flow<List<CollectionSentence>> = dao.observeCollectionSentences()

    // ---- 生词本 ----
    fun observeWordbook(paperId: String): Flow<List<WordbookEntity>> = dao.observeWordbook(paperId)

    suspend fun addWord(paperId: String, word: String, phonetic: String, pos: String, zh: String) {
        dao.addWord(WordbookEntity(paperId, word, phonetic, pos, zh, System.currentTimeMillis()))
    }

    suspend fun removeWord(paperId: String, word: String) = dao.removeWord(paperId, word)

    // ---- 收藏句 ----
    fun observeSentences(paperId: String): Flow<List<SentenceEntity>> = dao.observeSentences(paperId)

    suspend fun addSentence(paperId: String, questionId: String?, text: String) {
        dao.addSentence(SentenceEntity(0, paperId, questionId, text, System.currentTimeMillis()))
    }

    suspend fun removeSentence(id: Long) = dao.removeSentence(id)
}
