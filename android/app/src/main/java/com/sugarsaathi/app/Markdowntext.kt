package com.sugarsaathi.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders a useful subset of Markdown without any external dependency:
 * headings, bold, italic, bullet and numbered lists, dividers, and
 * table rows reformatted as readable label/value lines.
 */
@Composable
fun MarkdownText(
    markdown: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    baseSize: Int = 15
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {

                is MdBlock.Divider -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = color.copy(alpha = 0.25f)
                )

                is MdBlock.Heading -> {
                    Spacer(Modifier.height(if (block.level <= 2) 6.dp else 2.dp))
                    Text(
                        text = inlineStyled(block.text),
                        fontSize = when (block.level) {
                            1 -> (baseSize + 5).sp
                            2 -> (baseSize + 3).sp
                            else -> (baseSize + 1).sp
                        },
                        fontWeight = FontWeight.Bold,
                        color = color,
                        lineHeight = (baseSize + 9).sp
                    )
                }

                is MdBlock.Bullet -> Row(
                    modifier = Modifier.padding(start = (block.indent * 12).dp)
                ) {
                    Text("•  ", fontSize = baseSize.sp, color = color)
                    Text(
                        text = inlineStyled(block.text),
                        fontSize = baseSize.sp,
                        lineHeight = (baseSize + 7).sp,
                        color = color
                    )
                }

                is MdBlock.Numbered -> Row(
                    modifier = Modifier.padding(start = (block.indent * 12).dp)
                ) {
                    Text(
                        "${block.number}.  ",
                        fontSize = baseSize.sp,
                        fontWeight = FontWeight.Medium,
                        color = color
                    )
                    Text(
                        text = inlineStyled(block.text),
                        fontSize = baseSize.sp,
                        lineHeight = (baseSize + 7).sp,
                        color = color
                    )
                }

                // A table row shown as "Label — value · value"
                is MdBlock.TableRow -> Row(
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                ) {
                    Text("▪  ", fontSize = (baseSize - 2).sp, color = color.copy(alpha = 0.7f))
                    Text(
                        text = buildAnnotatedString {
                            block.cells.forEachIndexed { i, cell ->
                                if (i == 0) {
                                    withStyleBold { append(cell) }
                                    if (block.cells.size > 1) append(" — ")
                                } else {
                                    append(cell)
                                    if (i < block.cells.size - 1) append(" · ")
                                }
                            }
                        },
                        fontSize = baseSize.sp,
                        lineHeight = (baseSize + 7).sp,
                        color = color
                    )
                }

                is MdBlock.Paragraph -> Text(
                    text = inlineStyled(block.text),
                    fontSize = baseSize.sp,
                    lineHeight = (baseSize + 7).sp,
                    color = color
                )
            }
        }
    }
}

// ── Block model ───────────────────────────────────────────

private sealed class MdBlock {
    data class Paragraph(val text: String) : MdBlock()
    data class Heading(val text: String, val level: Int) : MdBlock()
    data class Bullet(val text: String, val indent: Int) : MdBlock()
    data class Numbered(val text: String, val number: Int, val indent: Int) : MdBlock()
    data class TableRow(val cells: List<String>) : MdBlock()
    data object Divider : MdBlock()
}

// ── Parser ────────────────────────────────────────────────

private fun parseMarkdown(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()

    src.lines().forEach { rawLine ->
        val indent = (rawLine.length - rawLine.trimStart().length) / 2
        val line = rawLine.trim()

        when {
            line.isEmpty() -> { /* spacing handled by Column */ }

            // Divider: ---, ***, ___ (3 or more)
            line.length >= 3 && line.all { it == '-' || it == '*' || it == '_' } ->
                blocks += MdBlock.Divider

            // Heading: # to ######
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length.coerceAtMost(6)
                val text = line.drop(level).trim()
                if (text.isNotEmpty()) blocks += MdBlock.Heading(text, level)
            }

            // Table row: | a | b |
            line.startsWith("|") && line.count { it == '|' } >= 2 -> {
                val cells = line.trim('|').split("|").map { it.trim() }
                // Skip separator rows like |---|---|
                val isSeparator = cells.all { c ->
                    c.isNotEmpty() && c.all { it == '-' || it == ':' || it == ' ' }
                }
                if (!isSeparator && cells.any { it.isNotEmpty() }) {
                    blocks += MdBlock.TableRow(cells.filter { it.isNotEmpty() })
                }
            }

            // Bullet: -, *, • followed by a space
            (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")) ->
                blocks += MdBlock.Bullet(line.drop(2).trim(), indent)

            // Numbered: 1. text
            line.firstOrNull()?.isDigit() == true && line.contains(". ") -> {
                val dot = line.indexOf(". ")
                val num = line.substring(0, dot).toIntOrNull()

                blocks += if (num != null) {
                    MdBlock.Numbered(line.substring(dot + 2).trim(), num, indent)
                } else {
                    MdBlock.Paragraph(line)
                }
            }

            else -> blocks += MdBlock.Paragraph(line)
        }
    }

    return blocks
}

// ── Inline styling: **bold**, *italic*, `code` ────────────

private fun inlineStyled(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyleBold { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }

            text.startsWith("*", i) && !text.startsWith("**", i) -> {
                val end = text.indexOf("*", i + 1)
                if (end > i) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }

            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end > i) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Medium))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }

            else -> { append(text[i]); i++ }
        }
    }
}

private inline fun AnnotatedString.Builder.withStyleBold(
    block: () -> Unit
) {
    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
    block()
    pop()
}