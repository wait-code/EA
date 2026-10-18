package com.examreader.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 试卷内容仓库：.epak 导入 / 读取 / 删除。
 * 包格式见 framework/src/epak.mjs（manifest.json + exam.json + mindmaps/*.svg）。
 */
 * */

class ExamRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    private val content = db.content()
    private val json = SegCodec.json
    private val outlineCodec = ListSerializer(COutline.serializer())
    private val migrationCodec = ListSerializer(CMigration.serializer())

    private val mindmapRoot: File
        get() = File(context.filesDir, "mindmaps")

    fun observePapers(): Flow<List<PaperSummary>> = content.observePapers().map { list -> list.map { it.toSummary() } }

    fun observeSections(paperId: String): Flow<List<SectionSummary>> =
        content.observeSections(paperId).map { list -> list.map { it.toSummary() } }

    suspend fun paper(paperId: String): PaperSummary? = content.paper(paperId)?.toSummary()

    suspend fun sectionSummaries(paperId: String): List<SectionSummary> = content.sections(paperId).map { it.toSummary() }

    suspend fun importFromUri(bytes: ByteArray, sourceLabel: String): ImportOutcome = importEpak(bytes, sourceLabel)

    suspend fun importEpak(bytes: ByteArray, sourceLabel: String): ImportOutcome = withContext(Dispatchers.IO) {
        val entries = readZip(bytes)
        val manifestBytes = entries["manifest.json"]
            ?: return@withContext ImportOutcome(false, "", "", "不是有效的 .epak：缺少 manifest.json")
        val manifest = runCatching {
            json.decodeFromString(CManifest.serializer(), manifestBytes.decodeToString())
        }.getOrElse { return@withContext ImportOutcome(false, "", "", "manifest.json 解析失败：${it.message}") }
        if (manifest.format != "epak") {
            return@withContext ImportOutcome(false, manifest.id, "", "不是 epak 包（format=${manifest.format}）")
        }
        if (manifest.formatVersion != 1) {
            return@withContext ImportOutcome(false, manifest.id, "", "包版本不兼容：${manifest.formatVersion}，本应用支持 1")
        }
        val examBytes = entries["exam.json"]
            ?: return@withContext ImportOutcome(false, manifest.id, "", "epak 缺少 exam.json")
        val exam = runCatching {
            json.decodeFromString(CExam.serializer(), examBytes.decodeToString())
        }.getOrElse { return@withContext ImportOutcome(false, manifest.id, "", "exam.json 解析失败：${it.message}") }
        if (exam.id.isBlank() || exam.sections.isEmpty()) {
            return@withContext ImportOutcome(false, exam.id, "", "试卷内容为空（sections 为空），请先用 framework 校验后再打包")
        }

        // 思维导图落盘：库里只存路径，避免塞大段 SVG
        val paperDir = File(mindmapRoot, exam.id)
        paperDir.deleteRecursively()
        paperDir.mkdirs()
        val mindmapPaths = HashMap<String, String>()
        exam.sections.forEach { section ->
            val file = section.mindmap?.file
            val data = if (file != null) entries[file] else null
            if (file != null && data != null) {
                val target = File(paperDir, section.id + ".svg")
                target.writeBytes(data)
                mindmapPaths[section.id] = target.absolutePath
            }
        }

        val now = System.currentTimeMillis()
        db.withTransaction {
            // 同 id 重导入 = 覆盖旧版本（清掉旧学习痕迹，避免题目对不上）
            content.deletePaper(exam.id)

            val sections = exam.sections
            val questionTotal = sections.sumOf { it.questions.size }
            content.insertPaper(
                PaperEntity(
                    id = exam.id,
                    title = exam.meta.title.ifBlank { manifest.title },
                    sub = exam.meta.sub,
                    full = exam.meta.full.ifBlank { exam.meta.title },
                    paperLine = exam.meta.paperLine,
                    subject = exam.meta.subject,
                    language = exam.meta.language,
                    schemaVersion = exam.schemaVersion,
                    importedAt = now,
                    sourceLabel = sourceLabel,
                    stage = exam.stage.ifBlank { "analyzed" },
                    sectionCount = sections.size,
                    questionCount = questionTotal,
                    dictCount = exam.dict.size,
                    hasQuiz = exam.quiz != null,
                    hasMindmap = mindmapPaths.isNotEmpty()
                )
            )
            content.insertSections(
                sections.map { s ->
                    SectionEntity(
                        paperId = exam.id,
                        id = s.id,
                        ordinal = s.index,
                        type = s.type,
                        tabLabel = s.tabLabel,
                        chip = s.chip.ifBlank { s.id },
                        title = s.title,
                        passageTitle = s.passageTitle,
                        topic = s.topic,
                        qnosJson = SegCodec.encodeStrings(s.qnos),
                        outlineJson = json.encodeToString(outlineCodec, s.outline),
                        migrationsJson = json.encodeToString(migrationCodec, s.migrations),
                        questionCount = s.questions.size,
                        vocabCount = s.vocab.size,
                        mindmapPath = mindmapPaths[s.id]
                    )
                }
            )
            content.insertParagraphs(
                sections.flatMap { s ->
                    s.paras.map { p ->
                        ParagraphEntity(
                            paperId = exam.id,
                            sectionId = s.id,
                            ordinal = p.index,
                            heading = p.heading,
                            enJson = SegCodec.encode(p.en),
                            zhJson = SegCodec.encode(p.zh)
                        )
                    }
                }
            )
            content.insertQuestions(
                sections.flatMap { s ->
                    s.questions.map { q ->
                        QuestionEntity(
                            paperId = exam.id,
                            id = q.id,
                            sectionId = s.id,
                            no = q.no,
                            ordinal = q.index,
                            kind = q.kind,
                            stemJson = SegCodec.encode(q.stem),
                            answerJson = SegCodec.encodeAnswer(q.answer),
                            optionsJson = SegCodec.encodeOptions(q.options),
                            analysisJson = SegCodec.encodeAnalysis(q.analysis),
                            isChoice = q.options.isNotEmpty()
                        )
                    }
                }
            )
            content.insertVocab(
                sections.flatMap { s ->
                    s.vocab.map { v ->
                        VocabEntity(exam.id, v.id, s.id, v.index, v.word, v.phonetic, v.partOfSpeech, v.zh)
                    }
                }
            )
            if (exam.dict.isNotEmpty()) {
                content.insertDict(exam.dict.map { DictEntryEntity(exam.id, it.word, it.phonetic, it.partOfSpeech, it.zh) })
            }
            exam.quiz?.let { quiz ->
                content.insertQuizParts(
                    quiz.parts.map { p ->
                        QuizPartEntity(
                            paperId = exam.id,
                            id = p.id,
                            ordinal = p.index,
                            no = p.no,
                            name = p.name,
                            desc = p.desc,
                            perScore = p.per,
                            boxJson = SegCodec.encodeStrings(p.box),
                            passageJson = SegCodec.encode(p.passage),
                            answersJson = SegCodec.encodeStrings(p.answers),
                            itemCount = p.items.size + p.answers.size
                        )
                    }
                )
                content.insertQuizItems(
                    quiz.parts.flatMap { p ->
                        p.items.map { item ->
                            QuizItemEntity(
                                paperId = exam.id,
                                id = item.id,
                                partId = p.id,
                                ordinal = item.index,
                                no = item.no,
                                answer = item.answer,
                                phonetic = item.phonetic,
                                partOfSpeech = item.partOfSpeech,
                                zh = item.zh,
                                preJson = SegCodec.encode(item.pre),
                                post = item.post
                            )
                        }
                    }
                )
            }
            content.insertImportLog(
                ImportLogEntity(
                    paperId = exam.id,
                    title = exam.meta.title,
                    sourceLabel = sourceLabel,
                    importedAt = now,
                    countsJson = json.encodeToString(CCounts.serializer(), exam.counts ?: CCounts()),
                    ok = true,
                    message = "导入成功"
                )
            )
        }

        ImportOutcome(
            ok = true,
            paperId = exam.id,
            title = exam.meta.title,
            message = "导入成功",
            sectionCount = exam.sections.size,
            questionCount = exam.sections.sumOf { it.questions.size }
        )
    }

    suspend fun deletePaper(paperId: String) = withContext(Dispatchers.IO) {
        content.deletePaper(paperId)
        File(mindmapRoot, paperId).deleteRecursively()
        Unit
    }

    suspend fun loadSection(paperId: String, sectionId: String): SectionDetail? = withContext(Dispatchers.IO) {
        val section = content.section(paperId, sectionId) ?: return@withContext null
        val paragraphs = content.paragraphs(paperId, sectionId).map { p ->
            CParagraph(
                index = p.ordinal,
                heading = p.heading,
                en = SegCodec.decode(p.enJson),
                zh = SegCodec.decode(p.zhJson)
            )
        }
        val questions = content.questions(paperId, sectionId).map { q ->
            CQuestion(
                id = q.id,
                no = q.no,
                index = q.ordinal,
                stem = SegCodec.decode(q.stemJson),
                kind = q.kind,
                answer = SegCodec.decodeAnswer(q.answerJson),
                options = SegCodec.decodeOptions(q.optionsJson),
                analysis = SegCodec.decodeAnalysis(q.analysisJson)
            )
        }
        val vocab = content.vocab(paperId, sectionId).map {
            CVocab(it.id, it.ordinal, it.word, it.phonetic, it.partOfSpeech, it.zh)
        }
        SectionDetail(
            paperId = paperId,
            id = section.id,
            type = section.type,
            tabLabel = section.tabLabel,
            chip = section.chip,
            title = section.title,
            passageTitle = section.passageTitle,
            topic = section.topic,
            paragraphs = paragraphs,
            questions = questions,
            vocab = vocab,
            migrations = runCatching { json.decodeFromString(migrationCodec, section.migrationsJson) }
                .getOrDefault(emptyList()),
            outline = runCatching { json.decodeFromString(outlineCodec, section.outlineJson) }
                .getOrDefault(emptyList()),
            mindmapFile = section.mindmapPath?.let { File(it) }?.takeIf { it.exists() }
        )
    }

    suspend fun quizParts(paperId: String): List<QuizPartDetail> = withContext(Dispatchers.IO) {
        content.quizParts(paperId).map { p ->
            val items = content.quizItems(paperId, p.id).map { item ->
                CQuizItem(
                    id = item.id,
                    index = item.ordinal,
                    no = item.no,
                    answer = item.answer,
                    phonetic = item.phonetic,
                    partOfSpeech = item.partOfSpeech,
                    zh = item.zh,
                    pre = SegCodec.decode(item.preJson),
                    post = item.post
                )
            }
            QuizPartDetail(
                paperId = paperId,
                id = p.id,
                no = p.no,
                name = p.name,
                desc = p.desc,
                perScore = p.perScore,
                box = SegCodec.decodeStrings(p.boxJson),
                passage = SegCodec.decode(p.passageJson),
                answers = SegCodec.decodeStrings(p.answersJson),
                items = items
            )
        }
    }

    suspend fun lookup(paperId: String, word: String): DictEntryEntity? = content.lookup(paperId, word.trim())

    suspend fun prefixSearch(paperId: String, prefix: String): List<DictEntryEntity> =
        if (prefix.isBlank()) emptyList() else content.prefixSearch(paperId, prefix.trim())

    /**
     * App 内「保存」：草稿 -> compiled -> 生成 .epak -> 走同一条导入路径进 SQLite。
     * 这就是「.epak 自动生成」的实现：老师不用碰命令行。
     */
    suspend fun importCompiled(
        compiled: CExam,
        mindmaps: Map<String, String> = emptyMap(),
        sourceLabel: String = "应用内生成"
    ): ImportOutcome = withContext(Dispatchers.IO) {
        val bytes = EpakWriter.build(compiled, mindmaps)
        importEpak(bytes, sourceLabel)
    }

    /** 把库里已有试卷还原成 compiled + 思维导图 SVG，供「编辑原文/讲解/翻译」用 */
    suspend fun loadCompiled(paperId: String): Pair<CExam, Map<String, String>>? = withContext(Dispatchers.IO) {
        val paper = content.paper(paperId) ?: return@withContext null
        val sectionRows = content.sections(paperId)
        val mindmaps = LinkedHashMap<String, String>()
        val sections = sectionRows.map { row ->
            row.mindmapPath?.let { path ->
                val file = File(path)
                if (file.exists()) mindmaps[row.id] = file.readText()
            }
            val paragraphs = content.paragraphs(paperId, row.id).map { p ->
                CParagraph(p.ordinal, p.heading, SegCodec.decode(p.enJson), SegCodec.decode(p.zhJson))
            }
            val questions = content.questions(paperId, row.id).map { q ->
                CQuestion(
                    id = q.id,
                    no = q.no,
                    index = q.ordinal,
                    stem = SegCodec.decode(q.stemJson),
                    kind = q.kind,
                    answer = SegCodec.decodeAnswer(q.answerJson),
                    options = SegCodec.decodeOptions(q.optionsJson),
                    analysis = SegCodec.decodeAnalysis(q.analysisJson)
                )
            }
            val vocab = content.vocab(paperId, row.id).map {
                CVocab(it.id, it.ordinal, it.word, it.phonetic, it.partOfSpeech, it.zh)
            }
            CSection(
                id = row.id,
                index = row.ordinal,
                type = row.type,
                tabLabel = row.tabLabel,
                chip = row.chip,
                title = row.title,
                passageTitle = row.passageTitle,
                topic = row.topic,
                qnos = SegCodec.decodeStrings(row.qnosJson),
                paras = paragraphs,
                vocab = vocab,
                migrations = runCatching { json.decodeFromString(migrationCodec, row.migrationsJson) }
                    .getOrDefault(emptyList()),
                outline = runCatching { json.decodeFromString(outlineCodec, row.outlineJson) }
                    .getOrDefault(emptyList()),
                questions = questions,
                mindmap = row.mindmapPath?.let { CMindmap(file = "mindmaps/${row.id}.svg") }
            )
        }
        val parts = quizParts(paperId)
        val quiz = if (parts.isEmpty()) {
            null
        } else {
            CQuiz(
                title = paper.title,
                total = parts.sumOf { it.perScore * (it.items.size + it.answers.size) },
                parts = parts.mapIndexed { index, p ->
                    CQuizPart(
                        id = p.id,
                        index = index,
                        no = p.no,
                        name = p.name,
                        desc = p.desc,
                        per = p.perScore,
                        box = p.box,
                        passage = p.passage,
                        answers = p.answers,
                        items = p.items
                    )
                }
            )
        }
        val dict = content.allDict(paperId).map { CDict(it.word, it.phonetic, it.partOfSpeech, it.zh) }
        val compiled = CExam(
            schemaVersion = paper.schemaVersion,
            id = paper.id,
            stage = paper.stage,
            meta = CMeta(
                title = paper.title,
                sub = paper.sub,
                full = paper.full,
                paperLine = paper.paperLine,
                subject = paper.subject,
                language = paper.language
            ),
            sections = sections,
            dict = dict,
            quiz = quiz,
            counts = CCounts(
                sections = sections.size,
                questions = sections.sumOf { it.questions.size },
                paragraphs = sections.sumOf { it.paras.size },
                sectionVocab = sections.sumOf { it.vocab.size },
                dict = dict.size,
                quizParts = quiz?.parts?.size ?: 0,
                quizItems = quiz?.parts?.sumOf { it.items.size + it.answers.size } ?: 0,
                mindmaps = mindmaps.size
            )
        )
        compiled to mindmaps
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    out[entry.name.replace('\\', '/')] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return out
    }

    private fun PaperEntity.toSummary() = PaperSummary(
        id = id,
        title = title,
        full = full,
        paperLine = paperLine,
        sourceLabel = sourceLabel,
        importedAt = importedAt,
        stage = stage,
        sectionCount = sectionCount,
        questionCount = questionCount,
        dictCount = dictCount,
        hasQuiz = hasQuiz,
        hasMindmap = hasMindmap
    )

    private fun SectionEntity.toSummary() = SectionSummary(
        id = id,
        ordinal = ordinal,
        type = type,
        tabLabel = tabLabel,
        chip = chip,
        title = title,
        passageTitle = passageTitle,
        topic = topic,
        qnos = SegCodec.decodeStrings(qnosJson),
        questionCount = questionCount,
        vocabCount = vocabCount,
        hasMindmap = mindmapPath != null
    )
}
