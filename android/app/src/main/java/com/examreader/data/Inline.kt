package com.examreader.data

/**
 * 行内标记解析（Kotlin 版，与 framework/src/inline.mjs 行为保持一致）：
 *   - 旧版嵌套空位 {{ {{56|}} |it}} 先归一成 {{56|it}}
 *   - {{题号}} / {{题号|提示}} -> CSeg(t="blank")
 *   - <span class="ans"> / <span class="q"> / <b> / <i> / <br> -> 对应 segments
 *   - HTML 实体解码
 */
object Inline {
    private val NESTED_BLANK = Regex("""\{\{\s*\{\{\s*(\d+)\s*\|[^}]*\}\}\s*\|([^}]*)\}\}""")
    private val BLANK_AT_START = Regex("""^\{\{(\d+)(?:\|([^}]*))?\}\}""")
    private val TAG_AT_START = Regex("""^<\s*(/?)\s*([a-zA-Z][a-zA-Z0-9]*)((?:"[^"]*"|'[^']*'|[^>"'])*)>""")
    private val CLASS_ATTR = Regex("""class\s*=\s*["']([^"']*)["']""")
    private val ENTITY = Regex("""&(#\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);""")
    private val WHITESPACE = Regex("""\s+""")

    private val STYLE_TAGS = mapOf("b" to "b", "strong" to "b", "i" to "i", "em" to "i")
    private val SPAN_CLASS = mapOf("ans" to "ans", "q" to "q", "blk" to "blk")
    private val ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " "
    )

    fun normalizeBlanks(text: String): String =
        NESTED_BLANK.replace(text) { m -> "{{" + m.groupValues[1] + "|" + m.groupValues[2].trim() + "}}" }

    fun decodeEntities(text: String): String = ENTITY.replace(text) { m ->
        val code = m.groupValues[1]
        if (code.startsWith("#")) {
            val cp = if (code.startsWith("#x") || code.startsWith("#X")) {
                code.substring(2).toIntOrNull(16)
            } else {
                code.substring(1).toIntOrNull()
            }
            if (cp != null && cp in 0..0x10FFFF) String(Character.toChars(cp)) else m.value
        } else {
            ENTITIES[code.lowercase()] ?: m.value
        }
    }

    fun parse(raw: String?): List<CSeg> {
        if (raw.isNullOrEmpty()) return emptyList()
        val src = normalizeBlanks(raw)
        val segs = mutableListOf<CSeg>()
        val stack = ArrayList<String>()
        val buf = StringBuilder()

        fun styleNow(): String = stack.lastOrNull() ?: "text"
        fun flush() {
            if (buf.isEmpty()) return
            segs.add(CSeg(t = styleNow(), v = decodeEntities(buf.toString())))
            buf.clear()
        }
        fun popStyle(style: String) {
            for (i in stack.indices.reversed()) {
                if (stack[i] == style) {
                    stack.removeAt(i)
                    return
                }
            }
        }

        var i = 0
        while (i < src.length) {
            val rest = src.substring(i)

            val blank = BLANK_AT_START.find(rest)
            if (blank != null) {
                flush()
                val no = blank.groupValues[1]
                val hint = blank.groupValues[2].trim()
                segs.add(if (hint.isEmpty()) CSeg(t = "blank", no = no) else CSeg(t = "blank", no = no, hint = hint))
                i += blank.value.length
                continue
            }

            val tag = TAG_AT_START.find(rest)
            if (tag != null) {
                val closing = tag.groupValues[1] == "/"
                val name = tag.groupValues[2].lowercase()
                val attrs = tag.groupValues[3]
                flush()
                when {
                    name == "br" -> segs.add(CSeg(t = "br", v = ""))
                    STYLE_TAGS.containsKey(name) -> {
                        val style = STYLE_TAGS.getValue(name)
                        if (closing) popStyle(style) else stack.add(style)
                    }

                    name == "span" -> {
                        val cls = CLASS_ATTR.find(attrs)?.groupValues?.get(1) ?: ""
                        val token = cls.split(WHITESPACE).mapNotNull { SPAN_CLASS[it] }.firstOrNull()
                        if (token != null) {
                            if (closing) popStyle(token) else stack.add(token)
                        }
                    }
                }
                i += tag.value.length
                continue
            }

            buf.append(src[i])
            i += 1
        }
        flush()

        // 去掉首尾纯空白文本段
        while (segs.isNotEmpty() && segs.first().t == "text" && segs.first().v.isNullOrBlank()) segs.removeAt(0)
        while (segs.isNotEmpty() && segs.last().t == "text" && segs.last().v.isNullOrBlank()) segs.removeAt(segs.size - 1)
        if (segs.isNotEmpty() && segs.first().t == "text") {
            segs[0] = segs[0].copy(v = segs[0].v?.trimStart())
        }
        if (segs.isNotEmpty() && segs.last().t == "text") {
            val trimmed = segs.last().v?.trimEnd()
            if (trimmed.isNullOrEmpty()) segs.removeAt(segs.size - 1)
            else segs[segs.size - 1] = segs.last().copy(v = trimmed)
        }
        return segs
    }

    /** segments -> 可编辑的原始文本（保存后再编译，形成闭环） */
    fun toRaw(segs: List<CSeg>): String = buildString {
        segs.forEach { s ->
            when (s.t) {
                "blank" -> {
                    append("{{")
                    append(s.no ?: "")
                    if (!s.hint.isNullOrEmpty()) {
                        append('|')
                        append(s.hint)
                    }
                    append("}}")
                }

                "br" -> append("<br>")
                "ans" -> append("<span class=\"ans\">").append(s.v ?: "").append("</span>")
                "q" -> append("<span class=\"q\">").append(s.v ?: "").append("</span>")
                "blk" -> append("<span class=\"blk\">").append(s.v ?: "").append("</span>")
                "b" -> append("<b>").append(s.v ?: "").append("</b>")
                "i" -> append("<i>").append(s.v ?: "").append("</i>")
                else -> append(s.v ?: "")
            }
        }
    }

    /**
     * 词卷短文：segments[i] 结尾是第 i 个答案词，编译时摘掉换成空位。
     * 与 framework/src/compile.mjs 的 compileQuizPassage 对齐。
     */
    fun compileQuizPassage(segments: List<String>, answers: List<String>): List<CSeg> {
        val out = mutableListOf<CSeg>()
        segments.forEachIndexed { index, raw ->
            var text = raw
            val answer = answers.getOrNull(index)
            if (!answer.isNullOrBlank()) {
                val body = answer.trim().trimEnd('_', ' ')
                if (body.isNotEmpty()) {
                    val re = Regex("""\s*${Regex.escape(body)}\s*$""", RegexOption.IGNORE_CASE)
                    if (re.containsMatchIn(text)) text = re.replace(text, "")
                }
                out.addAll(parse(text))
                out.add(CSeg(t = "blank", no = (index + 1).toString()))
            } else {
                out.addAll(parse(text))
            }
        }
        return out
    }

    fun plainText(segs: List<CSeg>): String = buildString {
        segs.forEach { s ->
            when (s.t) {
                "blank" -> append(" ____ ")
                "br" -> append('\n')
                else -> append(s.v ?: "")
            }
        }
    }
}
