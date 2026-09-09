package com.mrrob.llmchat.data

/**
 * A pragmatic Markdown block parser for chat rendering. Supports the
 * constructs the spec calls for: headings, bold/italic, lists, ordered
 * lists, blockquotes, inline code, fenced code blocks, horizontal rules
 * and tables (rendered as aligned text rows).
 */

sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class Bullets(val items: List<String>) : MdBlock()
    data class Ordered(val items: List<String>) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Code(val language: String, val code: String) : MdBlock()
    data class Table(val rows: List<List<String>>, val headerRow: Boolean) : MdBlock()
    object Rule : MdBlock()
}

/** Inline span kinds for markdown text inside a block. */
sealed class MdSpan {
    data class Plain(val text: String) : MdSpan()
    data class Bold(val text: String) : MdSpan()
    data class Italic(val text: String) : MdSpan()
    data class CodeSpan(val text: String) : MdSpan()
    data class Link(val text: String, val url: String) : MdSpan()
}

object Markdown {

    fun parseBlocks(source: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val lines = source.replace("\r\n", "\n").split("\n")
        var i = 0
        val para = StringBuilder()

        fun flushPara() {
            if (para.isNotBlank()) blocks.add(MdBlock.Paragraph(para.toString().trim()))
            para.setLength(0)
        }

        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> flushPara()

                line.startsWith("```") -> {
                    flushPara()
                    val lang = line.removePrefix("```").trim()
                    val body = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].startsWith("```")) {
                        body.appendLine(lines[i])
                        i++
                    }
                    blocks.add(MdBlock.Code(lang.ifBlank { "text" }, body.toString().trimEnd()))
                }

                line.startsWith("---") || line.startsWith("***") || line.startsWith("___") -> {
                    flushPara()
                    blocks.add(MdBlock.Rule)
                }

                Regex("^#{1,6}\\s").matches(line) -> {
                    flushPara()
                    val level = line.takeWhile { it == '#' }.length
                    blocks.add(MdBlock.Heading(level, line.drop(level).trim()))
                }

                line.trimStart().startsWith(">") -> {
                    flushPara()
                    val quote = StringBuilder()
                    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                        quote.appendLine(lines[i].trimStart().removePrefix(">").trim())
                        i++
                    }
                    blocks.add(MdBlock.Quote(quote.toString().trim()))
                    continue
                }

                Regex("^\\s*[-*•]\\s+").matches(line) -> {
                    flushPara()
                    val items = mutableListOf<String>()
                    while (i < lines.size && Regex("^\\s*[-*•]\\s+").matches(lines[i])) {
                        items.add(Regex("^\\s*[-*•]\\s+").replace(lines[i], ""))
                        i++
                    }
                    blocks.add(MdBlock.Bullets(items))
                    continue
                }

                Regex("^\\s*\\d+[.)]\\s+").matches(line) -> {
                    flushPara()
                    val items = mutableListOf<String>()
                    while (i < lines.size && Regex("^\\s*\\d+[.)]\\s+").matches(lines[i])) {
                        items.add(Regex("^\\s*\\d+[.)]\\s+").replace(lines[i], ""))
                        i++
                    }
                    blocks.add(MdBlock.Ordered(items))
                    continue
                }

                line.contains("|") && i + 1 < lines.size && lines[i + 1].contains("---") -> {
                    flushPara()
                    val rows = mutableListOf<List<String>>()
                    var hadHeader = false
                    while (i < lines.size && lines[i].contains("|")) {
                        val raw = lines[i]
                        if (raw.replace(Regex("[|\\s-]"), "").isEmpty()) {
                            hadHeader = true
                            i++
                            continue
                        }
                        rows.add(raw.trim().trim('|').split("|").map { it.trim() })
                        i++
                    }
                    blocks.add(MdBlock.Table(rows, hadHeader))
                    continue
                }

                else -> {
                    if (para.isNotEmpty()) para.append('\n')
                    para.append(line)
                }
            }
            i++
        }
        flushPara()
        return blocks
    }

    /** Inline parser for double-asterisk bold, single-asterisk italic, backtick
     * code spans and [text](url) links. */
    fun parseInline(text: String): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        val buffer = StringBuilder()
        var i = 0

        fun flush() {
            if (buffer.isNotEmpty()) {
                spans.add(MdSpan.Plain(buffer.toString()))
                buffer.setLength(0)
            }
        }

        while (i < text.length) {
            val rest = text.substring(i)
            when {
                rest.startsWith("**") && rest.length > 4 -> {
                    val end = rest.indexOf("**", 2)
                    if (end > 0) {
                        flush()
                        spans.add(MdSpan.Bold(rest.substring(2, end)))
                        i += end + 2
                    } else { buffer.append('*'); i++ }
                }

                rest.startsWith("__") && rest.length > 4 -> {
                    val end = rest.indexOf("__", 2)
                    if (end > 0) {
                        flush()
                        spans.add(MdSpan.Bold(rest.substring(2, end)))
                        i += end + 2
                    } else { buffer.append('_'); i++ }
                }

                (rest.startsWith("*") || rest.startsWith("_")) && rest.length > 3 -> {
                    val close = rest.indexOf(rest[0], 1)
                    if (close > 1) {
                        flush()
                        spans.add(MdSpan.Italic(rest.substring(1, close)))
                        i += close + 1
                    } else { buffer.append(rest[0]); i++ }
                }

                rest.startsWith("`") && rest.length > 2 -> {
                    val end = rest.indexOf('`', 1)
                    if (end > 1) {
                        flush()
                        spans.add(MdSpan.CodeSpan(rest.substring(1, end)))
                        i += end + 1
                    } else { buffer.append('`'); i++ }
                }

                rest.startsWith("[") -> {
                    val closeBracket = rest.indexOf(']')
                    if (closeBracket > 0 && rest.length > closeBracket + 2 && rest[closeBracket + 1] == '(') {
                        val closeParen = rest.indexOf(')', closeBracket + 2)
                        if (closeParen > closeBracket) {
                            flush()
                            spans.add(MdSpan.Link(rest.substring(1, closeBracket), rest.substring(closeBracket + 2, closeParen)))
                            i += closeParen + 1
                        } else { buffer.append('['); i++ }
                    } else { buffer.append('['); i++ }
                }

                else -> { buffer.append(text[i]); i++ }
            }
        }
        flush()
        return spans
    }

    /** Strip markdown for copy / speech / plain display. */
    fun toPlainText(text: String): String =
        parseBlocks(text).joinToString("\n\n") { block ->
            when (block) {
                is MdBlock.Heading -> stripInline(block.text)
                is MdBlock.Paragraph -> stripInline(block.text)
                is MdBlock.Bullets -> block.items.joinToString("\n") { "• " + stripInline(it) }
                is MdBlock.Ordered -> block.items.mapIndexed { idx, item -> "${idx + 1}. " + stripInline(item) }.joinToString("\n")
                is MdBlock.Quote -> stripInline(block.text)
                is MdBlock.Code -> block.code
                is MdBlock.Table -> block.rows.joinToString("\n") { row -> row.joinToString("  |  ") }
                MdBlock.Rule -> ""
            }
        }

    /** Remove inline markup: bold/italic stars and underscores, code backticks, links. */
    fun stripInline(s: String): String =
        LINK_PATTERN.replace(s) { it.groupValues[1] }
            .replace(Regex("\\*\\*|__|\\*|_"), "")
            .replace("`", "")

    private val LINK_PATTERN = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
}
