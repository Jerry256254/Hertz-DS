package com.hertzds.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hertzds.data.db.MessageEntity
import com.hertzds.data.repo.MessageRole
import com.hertzds.data.repo.MessageStatus

/**
 * Renders one stored message. Tool traffic is compressed into chips; assistant
 * answers get a lightweight markdown treatment (bold, italics, code, fences).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(
    message: MessageEntity,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val isUser = message.role == MessageRole.USER

    when (message.role) {
        MessageRole.TOOL -> {
            val ok = message.status != MessageStatus.ERROR
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = message.toolName ?: "tool",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    Text(
                        text = if (ok) "· ok" else "· ${message.error?.take(60)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            if (message.status == MessageStatus.PENDING || message.status == MessageStatus.STREAMING) {
                Text(
                    "běží…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = modifier.padding(start = 12.dp, top = 2.dp),
                )
            } else if (!ok && message.error.isNullOrBlank() && message.content.isNotBlank()) {
                Text(
                    message.content.take(160),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        }

        else -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp,
                ),
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { clipboard.setText(AnnotatedString(message.content)) },
                    ),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (!isUser && !message.reasoning.isNullOrBlank()) {
                        ReasoningBlock(message.reasoning)
                        Spacer(Modifier.size(6.dp))
                    }

                    if (message.content.isNotBlank()) {
                        MarkdownText(
                            markdown = message.content,
                            streaming = message.status == MessageStatus.STREAMING,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    if (message.status == MessageStatus.STREAMING && message.content.isBlank()) {
                        TypingDots()
                    }

                    val footer = buildString {
                        if (message.model != null) append(message.model)
                        if (message.completionTokens > 0) {
                            append("  ·  ")
                            append(message.promptTokens + message.completionTokens)
                            append(" tok")
                        }
                        if (message.costUsd > 0.0) {
                            append("  ·  $")
                            append("%.5f".format(message.costUsd))
                            if (message.peakPricing) append(" ⚡peak")
                        }
                    }
                    if (footer.isNotBlank()) {
                        Text(
                            footer,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    when (message.status) {
                        MessageStatus.ERROR -> StatusLine("chyba: ${message.error ?: "neznámá"}", MaterialTheme.colorScheme.error)
                        MessageStatus.CANCELLED -> StatusLine("přerušeno", MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun ReasoningBlock(reasoning: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        onClick = { expanded = !expanded },
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "úvahy",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Text(
                    reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Streaming caret shown while tokens arrive. */
@Composable
fun TypingDots() {
    Text("▍", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
}

// ---- minimal markdown ---------------------------------------------------------

@Composable
fun MarkdownText(markdown: String, streaming: Boolean, color: androidx.compose.ui.graphics.Color) {
    val codeBackground = MaterialTheme.colorScheme.background
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val segments = splitFences(markdown)
        segments.forEachIndexed { index, segment ->
            if (segment.isCode) {
                Text(
                    segment.text.trimEnd('\n'),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(codeBackground.copy(alpha = 0.85f))
                        .padding(10.dp),
                )
            } else {
                Text(
                    inlineMd(segment.text),
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    color = color,
                    modifier = if (index == segments.lastIndex && streaming) {
                        Modifier
                    } else {
                        Modifier
                    },
                )
            }
        }
        if (streaming) TypingDots()
    }
}

private class Segment(val text: String, val isCode: Boolean)

private fun splitFences(text: String): List<Segment> {
    if (!text.contains("```")) return listOf(Segment(text, false))
    val segments = mutableListOf<Segment>()
    var rest = text
    while (true) {
        val start = rest.indexOf("```")
        if (start < 0) {
            if (rest.isNotEmpty()) segments += Segment(rest, false)
            break
        }
        if (start > 0) segments += Segment(rest.substring(0, start), false)
        val afterOpening = rest.indexOf('\n', start).let { if (it < 0) rest.length else it + 1 }
        val close = rest.indexOf("```", afterOpening)
        if (close < 0) {
            segments += Segment(rest.substring(afterOpening), true)
            break
        }
        segments += Segment(rest.substring(afterOpening, close), true)
        rest = rest.substring(close + 3)
    }
    return segments
}

/** **bold**, *italic*, `code` — enough markdown for chat answers. */
private fun inlineMd(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val bold = SpanStyle(fontWeight = FontWeight.SemiBold)
    val italic = SpanStyle(fontStyle = FontStyle.Italic)
    val code = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end < 0) { append(text.substring(i)); i = text.length } else {
                    withStyle(bold) { append(text.substring(i + 2, end)) }; i = end + 2
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end < 0) { append(text.substring(i)); i = text.length } else {
                    withStyle(code) { append(text.substring(i + 1, end)) }; i = end + 1
                }
            }
            text[i] == '*' && i + 1 < text.length && text[i + 1] != ' ' -> {
                val end = text.indexOf('*', i + 1)
                if (end < 0) { append(text.substring(i)); i = text.length } else {
                    withStyle(italic) { append(text.substring(i + 1, end)) }; i = end + 1
                }
            }
            else -> {
                val nextSpecial = generateSequence(i + 1) { it + 1 }
                    .takeWhile { it <= text.length }
                    .firstOrNull { it == text.length ||
                        text.startsWith("**", it) || text[it] == '`' ||
                        (text[it] == '*' && it + 1 < text.length && text[it + 1] != ' ') }
                    ?: text.length
                append(text.substring(i, nextSpecial))
                i = nextSpecial
            }
        }
    }
}
