package com.examreader.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 可编辑草稿模型：App 内「扫描 → 答案确认 → 生成解析 → 保存」的核心。
 *
 * 两阶段：
 *   scanned  只扫描出原文/题干/选项（答案可空）→ 保存为「扫描卷」
 *   analyzed 老师确认答案 + DeepSeek 生成解析 → 保存为「解析卷」
 */

class DraftParagraph(val index: Int) {
    var heading by mutableStateOf<String?>(null)
    var en by mutableStateOf("")
    var zh by mutableStateOf("")
}

class DraftSolution(val label: String) {
    var text by mutableStateOf("")
}

class DraftGroup(val key: String, val label: String) {
    val items = mutableStateListOf<String>()
}

class DraftQuestion(
    val no: String,
    val initialKind: String,
    initialAnswer: CAnswer,
    val options: List<COption>
) {
    var stem by mutableStateOf("")
    var kind by mutableStateOf(initialKind)

    /** 答案可编辑：老师自己拟定，或确认/采纳 AI 答案 */
    var answer by mutableStateOf(initialAnswer)

    val solutions = mutableStateListOf<DraftSolution>()
    val groups = mutableStateListOf<DraftGroup>()

    val hasOptions: Boolean get() = options.isNotEmpty()

    /** 选择题的正确选项字母 / 填空题的答案文本 */
    fun answerDisplay(): String = answer.letter ?: answer.text ?: "（未填）"

    fun answerFilled(): Boolean = !answer.letter.isNullOrBlank() || !answer.text.isNullOrBlank()
}

class DraftVocab(val word: String, val phonetic: String, val partOfSpeech: String) {
    var zh by mutableStateOf("")
}

class DraftSection(
    val id: String,
    val type: String,
    var tabLabel: String,
    var chip: String,
    var title: String,
    var passageTitle: String,
    var topic: String,
    var qnos: List<String>,
    var outline: List<COutline>,
    var migrations: List<CMigration>,
    var mindmapSvg: String?
) {
    val paragraphs = mutableStateListOf<DraftParagraph>()
    val questions = mutableStateListOf<DraftQuestion>()
    val vocab = mutableStateListOf<DraftVocab>()
    var expanded by mutableStateOf(true)

    fun blankCheck(): String? {
        val blankNos = paragraphs.flatMap { p ->
            Regex("""\{\{\s*(\d+)""").findAll(p.en).map { it.groupValues[1] }.toList()
        }
        if (blankNos.isEmpty()) return null
        val questionNos = questions.map { it.no }
        val missingQuestion = blankNos.filter { it !in questionNos }.distinct()
        val missingBlank = questionNos.filter { it !in blankNos }
        return when {
            missingQuestion.isEmpty() && missingBlank.isEmpty() -> "空位与题号一致（${blankNos.size} 个空）"
            else -> buildString {
                if (missingQuestion.isNotEmpty()) append("原文有 ${missingQuestion.joinToString(", ")} 但没有对应题目；")
                if (missingBlank.isNotEmpty()) append("题号 ${missingBlank.joinToString(", ")} 在原文里找不到空位；")
            }
        }
    }
}

class DraftModel {
    var id by mutableStateOf("")
    var title by mutableStateOf("")
    var sub by mutableStateOf("")
    var full by mutableStateOf("")
    var paperLine by mutableStateOf("")
    var subject by mutableStateOf("english")
    var language by mutableStateOf("zh-CN")

    /** scanned / analyzed */
    var stage by mutableStateOf("scanned")

    val sections = mutableStateListOf<DraftSection>()
    var quiz: CQuiz? = null
    var dict: List<CDict> = emptyList()
    var sourceLabel: String = "应用内生成"

    // 注意：与 DraftStore.editingPaperId 语义重叠，建议只保留一处；
    // 若保留此处，DraftStore.editingPaperId 应删除或仅作镜像。
    var editingPaperId: String? = null

    val totalQuestions: Int get() = sections.sumOf { it.questions.size }

    val answeredCount: Int get() =
        sections.sumOf { s -> s.questions.count { it.answerFilled() } }

    val hasAnyAnalysis: Boolean get() =
        sections.any { s -> s.questions.any { it.solutions.isNotEmpty() } }
}

/** canonical（扫描结果） / compiled（库里已有试卷） → 草稿 */
object DraftMapper {

    fun fromCanonical(exam: KExam): DraftModel {
        val model = DraftModel()
        model.id = exam.id
        model.title = exam.meta.title
        model.sub = exam.meta.sub
        model.full = exam.meta.full
        model.paperLine = exam.meta.paperLine
        model.subject = exam.meta.subject.ifBlank { "english" }
        model.language = exam.meta.language.ifBlank { "zh-CN" }
        model.stage = "scanned"
        model.dict = exam.dict
        model.quiz = exam.quiz?.let { quiz ->
            CQuiz(
                title = quiz.title,
                total = quiz.total,
                parts = quiz.parts.mapIndexed { pi, part ->
                    CQuizPart(
                        id = "quiz-${pi + 1}",
                        index = pi,
                        no = part.no,
                        name = part.name,
                        desc = part.desc,
                        per = part.per,
                        box = part.box,
                        passage = Inline.compileQuizPassage(part.segments, part.answers),
                        answers = part.answers,
                        items = part.items.mapIndexed { ii, item ->
                            CQuizItem(
                                id = "quiz-${pi + 1}-${item.no}",
                                index = ii,
                                no = item.no,
                                answer = item.answer,
                                phonetic = item.phonetic,
                                partOfSpeech = item.partOfSpeech,
                                zh = item.zh,
                                pre = Inline.parse(item.pre),
                                post = item.post
                            )
                        }
                    )
                }
            )
        }
        exam.sections.forEachIndexed { si, section ->
            val id = section.id.ifBlank { ('A' + si).toString() }
            val draftSection = DraftSection(
                id = id,
                type = section.type.ifBlank { "read" },
                tabLabel = section.tabLabel,
                chip = section.chip.ifBlank { id },
                title = section.title.ifBlank { section.passageTitle.ifBlank { "第 $id 篇" } },
                passageTitle = section.passageTitle,
                topic = section.topic,
                qnos = if (section.qnos.isNotEmpty()) section.qnos else section.questions.map { it.no },
                // 修复：补上 outline 的 index，避免全部为 0
                outline = section.outline.mapIndexed { oi, o ->
                    COutline(index = oi, level = o.level, text = o.text)
                },
                migrations = section.migrations.mapIndexed { mi, m ->
                    CMigration(id = "$id-mig-$mi", index = mi, title = m.title, detail = m.detail)
                },
                mindmapSvg = null
            )
            section.paras.forEach { para ->
                draftSection.paragraphs.add(
                    DraftParagraph(draftSection.paragraphs.size).apply {
                        heading = para.heading
                        en = para.en
                        zh = para.zh
                    }
                )
            }
            section.questions.forEach { q ->
                val answer = CAnswer(letter = q.answer.letter, text = q.answer.text, zh = q.answer.zh, hint = q.answer.hint)
                val options = q.options.mapIndexed { oi, o -> COption(id = "$id-${q.no}-${o.label}", label = o.label, text = o.text, index = oi) }
                val dq = DraftQuestion(q.no, q.kind, answer, options)
                dq.stem = q.stem
                q.analysis.solutions.forEach { dq.solutions.add(DraftSolution(it.label).apply { text = it.text }) }
                q.analysis.groups.forEach { g ->
                    dq.groups.add(DraftGroup(g.key, g.label).apply { items.addAll(g.items) })
                }
                draftSection.questions.add(dq)
            }
            section.vocab.forEach { v ->
                draftSection.vocab.add(DraftVocab(v.word, v.phonetic, v.partOfSpeech).apply { zh = v.zh })
            }
            model.sections.add(draftSection)
        }
        return model
    }

    fun fromCompiled(exam: CExam, mindmaps: Map<String, String>): DraftModel {
        val model = DraftModel()
        model.id = exam.id
        model.title = exam.meta.title
        model.sub = exam.meta.sub
        model.full = exam.meta.full
        model.paperLine = exam.meta.paperLine
        // 修复：与 fromCanonical 对齐，空值时回落到默认值
        model.subject = exam.meta.subject.ifBlank { "english" }
        model.language = exam.meta.language.ifBlank { "zh-CN" }
        model.stage = exam.stage.ifBlank { "analyzed" }
        model.quiz = exam.quiz
        model.dict = exam.dict
        exam.sections.forEach { section ->
            val draftSection = DraftSection(
                id = section.id,
                type = section.type,
                tabLabel = section.tabLabel,
                chip = section.chip,
                title = section.title,
                passageTitle = section.passageTitle,
                topic = section.topic,
                qnos = section.qnos,
                outline = section.outline,
                migrations = section.migrations,
                mindmapSvg = mindmaps[section.id]
            )
            section.paras.forEach { para ->
                draftSection.paragraphs.add(
                    DraftParagraph(draftSection.paragraphs.size).apply {
                        heading = para.heading
                        en = Inline.toRaw(para.en)
                        zh = Inline.toRaw(para.zh)
                    }
                )
            }
            section.questions.forEach { q ->
                val dq = DraftQuestion(q.no, q.kind, q.answer, q.options)
                dq.stem = Inline.toRaw(q.stem)
                q.analysis.solutions.forEach { sol ->
                    dq.solutions.add(DraftSolution(sol.label).apply { text = Inline.toRaw(sol.text) })
                }
                q.analysis.groups.forEach { g ->
                    dq.groups.add(DraftGroup(g.key, g.label).apply {
                        g.items.forEach { items.add(Inline.toRaw(it.text)) }
                    })
                }
                draftSection.questions.add(dq)
            }
            section.vocab.forEach { v ->
                draftSection.vocab.add(DraftVocab(v.word, v.phonetic, v.partOfSpeech).apply { zh = v.zh })
            }
            model.sections.add(draftSection)
        }
        return model
    }

    /** 草稿 → compiled（保存时调用） */
    fun toCompiled(model: DraftModel): CExam {
        val sections = model.sections.mapIndexed { si, s ->
            val questions = s.questions.mapIndexed { qi, q ->
                val qid = "${s.id}-${q.no}"
                CQuestion(
                    id = qid,
                    no = q.no,
                    index = qi,
                    stem = Inline.parse(q.stem),
                    kind = q.kind,
                    answer = q.answer,
                    options = q.options.mapIndexed { oi, o ->
                        COption(id = "$qid-${o.label}", label = o.label, text = o.text, index = oi)
                    },
                    analysis = CAnalysis(
                        solutionLabel = "解析",
                        solutions = q.solutions.mapIndexed { i, sol ->
                            CSolution(id = "$qid-sol-$i", index = i, label = sol.label, text = Inline.parse(sol.text))
                        },
                        groups = q.groups.mapIndexed { gi, g ->
                            CGroup(
                                id = "$qid-grp-$gi",
                                index = gi,
                                key = g.key,
                                label = g.label,
                                items = g.items.mapIndexed { ii, item ->
                                    CGroupItem(id = "$qid-grp-$gi-$ii", index = ii, text = Inline.parse(item))
                                }
                            )
                        }
                    )
                )
            }
            CSection(
                id = s.id,
                index = si,
                type = s.type,
                tabLabel = s.tabLabel,
                chip = s.chip,
                title = s.title,
                passageTitle = s.passageTitle,
                topic = s.topic,
                qnos = if (s.qnos.isNotEmpty()) s.qnos else questions.map { it.no },
                paras = s.paragraphs.mapIndexed { pi, p ->
                    CParagraph(
                        index = pi,
                        heading = p.heading?.takeIf { it.isNotBlank() },
                        en = Inline.parse(p.en),
                        zh = Inline.parse(p.zh)
                    )
                },
                vocab = s.vocab.mapIndexed { vi, v ->
                    CVocab(
                        id = "${s.id}-w-$vi",
                        index = vi,
                        word = v.word,
                        phonetic = v.phonetic,
                        partOfSpeech = v.partOfSpeech,
                        zh = v.zh
                    )
                },
                migrations = s.migrations,
                outline = s.outline,
                questions = questions,
                mindmap = s.mindmapSvg?.let { CMindmap(file = "mindmaps/${s.id}.svg") }
            )
        }
        val counts = CCounts(
            sections = sections.size,
            questions = sections.sumOf { it.questions.size },
            paragraphs = sections.sumOf { it.paras.size },
            sectionVocab = sections.sumOf { it.vocab.size },
            dict = model.dict.size,
            quizParts = model.quiz?.parts?.size ?: 0,
            quizItems = model.quiz?.parts?.sumOf { it.items.size + it.answers.size } ?: 0,
            mindmaps = sections.count { it.mindmap != null }
        )
        return CExam(
            schemaVersion = 1,
            id = model.id,
            stage = model.stage,
            meta = CMeta(
                title = model.title,
                sub = model.sub,
                full = model.full.ifBlank { model.title },
                paperLine = model.paperLine,
                subject = model.subject,
                language = model.language
            ),
            sections = sections,
            dict = model.dict,
            quiz = model.quiz,
            counts = counts
        )
    }
}

/** 保存前的校验。stage = scanned 时允许答案/解析为空 */
object DraftValidator {
    fun errors(model: DraftModel, stage: String = model.stage): List<String> {
        val errors = mutableListOf<String>()
        if (!Regex("""^[a-z0-9][a-z0-9-]{2,63}$""").matches(model.id)) {
            errors.add("试卷 id 只能是小写字母/数字/连字符（3–64 字符），当前「${model.id}」")
        }
        if (model.title.isBlank()) errors.add("试卷标题不能为空")
        if (model.sections.isEmpty()) errors.add("至少要有 1 篇内容")
        val allNos = mutableSetOf<String>()
        model.sections.forEach { section ->
            if (section.id.isBlank()) errors.add("有篇目缺少编号")
            if (section.questions.isEmpty()) errors.add("第 ${section.id} 篇没有题目")
            if (section.paragraphs.isEmpty()) errors.add("第 ${section.id} 篇没有原文段落")
            section.paragraphs.forEachIndexed { i, p ->
                if (p.en.isBlank()) errors.add("第 ${section.id} 篇第 ${i + 1} 段原文为空")
            }
            section.questions.forEach { q ->
                if (q.no.isBlank()) errors.add("第 ${section.id} 篇有题目缺少题号")
                else if (!allNos.add(q.no)) errors.add("题号 ${q.no} 重复")
                if (q.options.isNotEmpty()) {
                    if (q.answerFilled() && q.answer.letter != null && q.options.none { it.label == q.answer.letter }) {
                        errors.add("第 ${q.no} 题的答案字母 ${q.answer.letter} 不在选项里")
                    }
                }
            }
        }
        return errors
    }

    fun warnings(model: DraftModel): List<String> {
        val warnings = mutableListOf<String>()
        var noAnalysis = 0
        var noTranslation = 0
        var noAnswer = 0
        model.sections.forEach { section ->
            section.paragraphs.forEach { if (it.zh.isBlank()) noTranslation += 1 }
            section.questions.forEach {
                if (it.solutions.isEmpty()) noAnalysis += 1
                if (!it.answerFilled()) noAnswer += 1
            }
            section.blankCheck()?.let { check ->
                if (!check.startsWith("空位与题号一致")) warnings.add("第 ${section.id} 篇：$check")
            }
        }
        if (noTranslation > 0) warnings.add("有 $noTranslation 段还没有中文翻译")
        if (noAnalysis > 0) warnings.add("有 $noAnalysis 道题还没有解析")
        if (noAnswer > 0) warnings.add("有 $noAnswer 道题还没有答案（生成解析时会由 DeepSeek 自动补全）")
        return warnings
    }
}

/** 扫描 / 编辑流程之间传递草稿（进程内单例；另有 drafts 表做持久化） */
object DraftStore {
    var draft: DraftModel? = null

    // 注意：与 DraftModel.editingPaperId 重复，建议二选一保留。
    var editingPaperId: String? = null

    fun put(model: DraftModel) {
        draft = model
    }

    fun take(): DraftModel? = draft
}