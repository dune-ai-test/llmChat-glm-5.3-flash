package com.mrrob.llmchat.ui.kit

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mrrob.llmchat.data.MdBlock
import com.mrrob.llmchat.data.MdSpan.Bold
import com.mrrob.llmchat.data.MdSpan.CodeSpan
import com.mrrob.llmchat.data.MdSpan.Italic
import com.mrrob.llmchat.data.MdSpan.Link
import com.mrrob.llmchat.data.MdSpan.Plain
import com.mrrob.llmchat.data.Markdown
import com.mrrob.llmchat.ui.theme.AsterColors
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType
import com.mrrob.llmchat.ui.theme.MonoFamily

/** Renders assistant/user markdown exactly like the design's chat messages. */
@Composable
fun MarkdownText(
    source: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    showCodeLineNumbers: Boolean = false
) {
    val c = LocalScheme.current
    val t = LocalType.current
    val blocks = remember(source) { Markdown.parseBlocks(source) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = inlineAnnotated(block.text, c),
                    style = t.body.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = t.cardTitle.fontSize * (if (block.level <= 2) 1.15f else 1.05f)
                    ),
                    color = c.textPrimary
                )

                is MdBlock.Paragraph -> Text(
                    text = inlineAnnotated(block.text, c),
                    style = t.body,
                    color = c.textPrimary
                )

                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    block.items.forEach { item ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier
                                    .padding(top = 7.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(c.textMuted)
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(inlineAnnotated(item, c), style = t.bodyTight, color = c.textPrimary, modifier = Modifier.weight(1f))
                        }
                    }
                }

                is MdBlock.Ordered -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    block.items.forEachIndexed { idx, item ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("${idx + 1}.", style = t.bodyTight, color = c.textMuted)
                            Spacer(Modifier.width(9.dp))
                            Text(inlineAnnotated(item, c), style = t.bodyTight, color = c.textPrimary, modifier = Modifier.weight(1f))
                        }
                    }
                }

                is MdBlock.Quote -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.fill)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(inlineAnnotated(block.text, c), style = t.bodyTight, color = c.textSecondary)
                }

                is MdBlock.Code -> CodeBlock(
                    language = block.language,
                    code = block.code,
                    showLineNumbers = showCodeLineNumbers
                )

                is MdBlock.Table -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    block.rows.forEachIndexed { rowIdx, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { cell ->
                                Text(
                                    text = cell,
                                    style = t.desc.copy(
                                        fontWeight = if (rowIdx == 0 && block.headerRow) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (rowIdx == 0 && block.headerRow) c.textPrimary else c.textSecondary
                                )
                            }
                        }
                    }
                }

                MdBlock.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(c.border)
                )
            }
        }
        if (streaming) StreamingCaret()
    }
}

@Composable
private fun StreamingCaret() {
    val c = LocalScheme.current
    val transition = rememberInfiniteTransition(label = "caret")
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "blink"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).height(16.dp).alpha(blink).background(c.accent))
    }
}

/** Inline spans → AnnotatedString. */
@Composable
private fun inlineAnnotated(
    text: String,
    c: com.mrrob.llmchat.ui.theme.AsterScheme
): AnnotatedString =
    buildAnnotatedString {
        Markdown.parseInline(text).forEach { span ->
            when (span) {
                is Plain -> append(span.text)
                is Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
                is Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) }
                is CodeSpan -> withStyle(
                    SpanStyle(
                        fontFamily = MonoFamily,
                        background = c.fill,
                        color = c.textPrimary
                    )
                ) { append(span.text) }
                is Link -> withStyle(SpanStyle(color = c.accent)) { append(span.text) }
            }
        }
    }

/** One-dark code block: header with language + copy, mono body with line-level coloring. */
@Composable
fun CodeBlock(
    language: String,
    code: String,
    modifier: Modifier = Modifier,
    showLineNumbers: Boolean = false
) {
    val c = LocalScheme.current
    val clipboard = LocalClipboardManager.current
    val t = LocalType.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (c.dark) AsterColors.CodeBg.copy(alpha = 0.9f) else c.codeBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                language.replaceFirstChar { it.uppercase() },
                style = t.monoSmall,
                color = c.textMuted,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { clipboard.setText(AnnotatedString(code)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(IconsL.copy, "Copy code", tint = c.textMuted, modifier = Modifier.size(14.dp))
            }
        }
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            code.split("\n").forEachIndexed { idx, line ->
                Row {
                    if (showLineNumbers) {
                        Text(
                            "${idx + 1}".padStart(2),
                            style = t.mono,
                            color = AsterColors.CodeComment.copy(alpha = 0.6f),
                            modifier = Modifier.width(26.dp)
                        )
                    }
                    Text(line.ifEmpty { " " }, style = t.mono, color = codeLineColor(line))
                }
            }
        }
    }
}

/** Whole-line syntax coloring matching the design export (One Dark-ish). */
@Composable
private fun codeLineColor(line: String) = com.mrrob.llmchat.ui.theme.LocalScheme.current.let { c ->
    val trimmed = line.trimStart()
    when {
        trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.startsWith("--") || trimmed.startsWith("\"\"\"") || trimmed.startsWith("'''") ->
            if (trimmed.startsWith("#!")) AsterColors.CodeComment else AsterColors.CodeComment
        trimmed.startsWith("def ") || trimmed.startsWith("fun ") || trimmed.startsWith("class ") ||
            trimmed.startsWith("if ") || trimmed.startsWith("for ") || trimmed.startsWith("while ") ||
            trimmed.startsWith("return ") || trimmed.startsWith("suspend ") || trimmed.startsWith("import ") ||
            trimmed.startsWith("val ") || trimmed.startsWith("var ") || trimmed.startsWith("yield ") ||
            trimmed.startsWith("raise ") || trimmed.startsWith("throw ") || trimmed.startsWith("try") ||
            trimmed.startsWith("catch") || trimmed.startsWith("repeat") -> AsterColors.CodePurple
        trimmed.startsWith("await") || trimmed.startsWith("throw") -> AsterColors.CodeRed
        else -> c.codeFg
    }
}
