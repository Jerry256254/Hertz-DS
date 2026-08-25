package com.hertzds.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hertzds.data.db.MessageEntity
import com.hertzds.data.repo.MessageRole
import com.hertzds.data.repo.MessageStatus
import com.hertzds.ui.theme.hertzSemantic

// ─────────────────────────────────────────────────────────────────────────────
// Message language:
//   user      → compact capsule on the right, signal veil fill + hairline
//   assistant → open typography on canvas (no box), glyph header, data footer
//   tool      → timeline rail: hairline spine, node dot, monospace caption
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MessageRow(message: MessageEntity, modifier: Modifier = Modifier) {
    when (message.role) {
        MessageRole.TOOL -> ToolRailEntry(message, modifier)
        MessageRole.USER -> UserBubble(message, modifier)
        else -> AssistantBlock(message, modifier)
    }
}

// ---- user --------------------------------------------------------------------

@Composable
private fun UserBubble(message: MessageEntity, modifier: Modifier) {
    val sem = hertzSemantic()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .border(1.dp, sem.signalVeil, RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                if (message.content.isNotBlank()) {
                    Text(
                        message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                if (message.status == MessageStatus.ERROR) {
                    Text(
                        "odeslání selhalo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

// ---- assistant -----------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantBlock(message: MessageEntity, modifier: Modifier) {
    val clipboard = LocalClipboardManager.current
    val streaming = message.status == MessageStatus.STREAMING

    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize()
            .combinedClickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = {
                    if (message.content.isNotBlank()) clipboard.setText(AnnotatedString(message.content))
                },
            ),
    ) {
        // header: glyph + identity
        Row(verticalAlignment = Alignment.CenterVertically) {
            SignalGlyph(streaming)
            Text(
                "HERTZ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 7.dp),
            )
            message.model?.let {
                Text(
                    "· ${modelShort(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
        }

        if (!message.reasoning.isNullOrBlank()) {
            ReasoningDisclosure(message.reasoning, Modifier.padding(top = 8.dp))
        }

        if (message.content.isNotBlank()) {
            MarkdownText(
                markdown = message.content,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else if (streaming) {
            ThinkingLine(Modifier.padding(top = 10.dp))
        }

        val meta = buildString {
            if (message.completionTokens > 0) append("${message.promptTokens + message.completionTokens} tok")
            if (message.costUsd > 0.0) {
                if (isNotEmpty()) append("  ·  ")
                append("$%.5f".format(message.costUsd))
                if (message.peakPricing) append(" peak")
            }
            when (message.status) {
                MessageStatus.ERROR -> append(if (isEmpty()) "chyba" else "  ·  chyba")
                MessageStatus.CANCELLED -> append(if (isEmpty()) "přerušeno" else "  ·  přerušeno")
                else -> Unit
            }
        }
        if (meta.isNotBlank()) {
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = hertzSemantic().faintText,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
    }
}

private fun modelShort(model: String): String = when {
    model.contains("pro") -> "pro"
    model.contains("vision") -> "vision"
    else -> "flash"
}

/** Small waveform mark used as assistant identity. */
@Composable
fun SignalGlyph(pulsing: Boolean, size: Int = 12) {
    val transition = rememberInfiniteTransition(label = "glyph")
    val alpha by transition.animateFloat(
        initialValue = if (pulsing) 0.45f else 0.95f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        Modifier
            .size(size.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
    )
}

@Composable
private fun ThinkingLine(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ThinkingDots()
    }
}

@Composable
fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(600, delayMillis = index * 160),
                    RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .size(5.dp)
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
            )
        }
    }
}

// ---- tool timeline ---------------------------------------------------------------

@Composable
private fun ToolRailEntry(message: MessageEntity, modifier: Modifier) {
    val sem = hertzSemantic()
    val running = message.status == MessageStatus.PENDING || message.status == MessageStatus.STREAMING
    val failed = message.status == MessageStatus.ERROR

    Row(modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        // rail: spine above + node + spine below
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(18.dp)) {
            Box(
                Modifier
                    .width(1.dp)
                    .height(9.dp)
                    .background(sem.hairline),
            )
            Box(
                Modifier
                    .size(7.dp)
                    .background(
                        when {
                            running -> MaterialTheme.colorScheme.primary
                            failed -> MaterialTheme.colorScheme.error
                            else -> sem.positive
                        },
                        CircleShape,
                    ),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(9.dp)
                    .background(sem.hairline),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        ) {
            Text(
                message.toolName ?: "tool",
                style = MaterialTheme.typography.labelMedium,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!message.error.isNullOrBlank()) {
                Text(
                    "  ${message.error.take(64)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    maxLines = 2,
                )
            }
        }
    }
}

// ---- reasoning -------------------------------------------------------------------

@Composable
private fun ReasoningDisclosure(reasoning: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sem = hertzSemantic()
    Column(modifier.animateContentSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
        ) {
            Box(
                Modifier
                    .size(4.dp)
                    .background(sem.faintText, CircleShape),
            )
            Text(
                "uvnitř hlavy",
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
        if (expanded) {
            Box(
                Modifier
                    .padding(start = 1.dp, top = 4.dp)
                    .border(1.dp, sem.hairline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lightweight markdown: fences, bold, italics, inline code
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MarkdownText(markdown: String, color: Color, modifier: Modifier = Modifier) {
    val sem = hertzSemantic()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        splitFences(markdown).forEach { segment ->
            if (segment.isCode) {
                Text(
                    segment.text.trimEnd('\n'),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .border(1.dp, sem.hairline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                )
            } else if (segment.text.isNotBlank()) {
                Text(
                    inlineMd(segment.text.trim()),
                    fontSize = 15.5.sp,
                    lineHeight = 23.sp,
                    color = color,
                )
            }
        }
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

private fun inlineMd(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val bold = SpanStyle(fontWeight = FontWeight.SemiBold)
    val italic = SpanStyle(fontStyle = FontStyle.Italic)
    val code = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
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
