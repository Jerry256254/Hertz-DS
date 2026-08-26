package com.hertzds.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hertzds.data.db.MessageEntity
import com.hertzds.data.repo.MessageRole
import com.hertzds.data.repo.MessageStatus
import com.hertzds.ui.theme.LocalStrings
import com.hertzds.ui.theme.hertzSemantic
import kotlinx.coroutines.delay

@Composable
fun MessageRow(
    message: MessageEntity,
    isCallMode: Boolean = false,
    isReading: Boolean = false,
    onToggleReadAloud: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (message.role) {
        MessageRole.TOOL -> ToolRailEntry(message, modifier)
        MessageRole.USER -> UserBubble(message, modifier)
        else -> AssistantBlock(message, isCallMode, isReading, onToggleReadAloud, modifier)
    }
}

@Composable
private fun UserBubble(message: MessageEntity, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier.widthIn(max = 300.dp).border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (message.content.isNotBlank()) {
                    Text(message.content, style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFF2F2F2)))
                }
                if (message.status == MessageStatus.ERROR) {
                    Text("Send failed", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFFF6B6B)), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun AssistantBlock(
    message: MessageEntity,
    isCallMode: Boolean,
    isReading: Boolean,
    onToggleReadAloud: () -> Unit,
    modifier: Modifier,
) {
    val str = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val streaming = message.status == MessageStatus.STREAMING
    var justCopied by remember { mutableStateOf(false) }
    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1400)
            justCopied = false
        }
    }

    // No animateContentSize() here: this block's height changes on every streamed
    // token AND right when the message finishes (status flips to DONE, revealing
    // the action row below). Animating that transition let the LazyColumn item's
    // measured height lag one frame behind the real content, so the newly
    // revealed Copy/Read-aloud row rendered clipped out of view until some other
    // event forced a relayout — i.e. it looked like the buttons had vanished.
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SignalGlyph(streaming)
            Text("HERTZ", style = MaterialTheme.typography.labelMedium.copy(color = Color.White), modifier = Modifier.padding(start = 7.dp))
            message.model?.let {
                Text(" · ${modelShort(it)}", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6E6E6E)), modifier = Modifier.padding(start = 5.dp))
            }
        }

        if (!message.reasoning.isNullOrBlank() && !isCallMode) {
            ReasoningDisclosure(message.reasoning, Modifier.padding(top = 8.dp))
        }

        if (message.content.isNotBlank()) {
            MarkdownText(
                markdown = message.content,
                color = Color(0xFFF2F2F2),
                isCallMode = isCallMode,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else if (streaming) {
            ThinkingLine(Modifier.padding(top = 10.dp))
        }

        val meta = buildString {
            if (!isCallMode && message.completionTokens > 0) append("${message.promptTokens + message.completionTokens} tok")
            if (!isCallMode && message.costUsd > 0.0) {
                if (isNotEmpty()) append("  ·  ")
                append("$%.5f".format(message.costUsd))
                if (message.peakPricing) append(" peak")
            }
            when (message.status) {
                MessageStatus.ERROR -> append(if (isEmpty()) str.error else "  ·  ${str.error}")
                MessageStatus.CANCELLED -> append(if (isEmpty()) str.cancelled else "  ·  ${str.cancelled}")
                else -> Unit
            }
        }
        if (meta.isNotBlank()) {
            Text(meta, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6E6E6E)), modifier = Modifier.padding(top = 7.dp))
        }

        if (!isCallMode && !streaming && message.content.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                MessageActionButton(
                    icon = if (justCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    contentDescription = if (justCopied) str.copied else str.copy,
                    onClick = {
                        clipboard.setText(AnnotatedString(message.content))
                        justCopied = true
                    },
                )
                Spacer(Modifier.width(2.dp))
                MessageActionButton(
                    icon = if (isReading) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                    contentDescription = if (isReading) str.stopReading else str.readAloud,
                    onClick = onToggleReadAloud,
                )
            }
        }
    }
}

/** Icon-only — the tooltip/contentDescription carries the label, not visible text. */
@Composable
private fun MessageActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Color(0xFFA8A8A8), modifier = Modifier.size(15.dp))
    }
}

private fun modelShort(model: String): String = when {
    model.contains("pro") -> "pro"
    model.contains("vision") -> "vision"
    else -> "flash"
}

@Composable
fun SignalGlyph(pulsing: Boolean, size: Int = 12) {
    val transition = rememberInfiniteTransition(label = "glyph")
    val alpha by transition.animateFloat(
        initialValue = if (pulsing) 0.45f else 0.95f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha",
    )
    Box(Modifier.size(size.dp).alpha(alpha).background(Color.White, androidx.compose.foundation.shape.CircleShape))
}

@Composable
private fun ThinkingLine(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) { ThinkingDots() }
}

@Composable
fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(initialValue = 0.2f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(600, delayMillis = index * 160), RepeatMode.Reverse), label = "dot$index")
            Box(Modifier.size(5.dp).alpha(alpha).background(Color(0xFFA8A8A8), androidx.compose.foundation.shape.CircleShape))
        }
    }
}

@Composable
private fun ToolRailEntry(message: MessageEntity, modifier: Modifier) {
    val running = message.status == MessageStatus.PENDING || message.status == MessageStatus.STREAMING
    val failed = message.status == MessageStatus.ERROR
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(18.dp)) {
            Box(Modifier.width(1.dp).height(9.dp).background(Color(0xFF2A2A2A)))
            Box(Modifier.size(7.dp).background(when { running -> Color.White; failed -> Color(0xFFFF6B6B); else -> Color.White }, androidx.compose.foundation.shape.CircleShape))
            Box(Modifier.width(1.dp).height(9.dp).background(Color(0xFF2A2A2A)))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp).weight(1f)) {
            Text(message.toolName ?: "tool", style = MaterialTheme.typography.labelMedium.copy(color = if (failed) Color(0xFFFF6B6B) else Color(0xFFA8A8A8)))
            if (!message.error.isNullOrBlank()) {
                Text("  ${message.error.take(64)}", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFF6B6B).copy(alpha = 0.85f)), maxLines = 2)
            }
        }
    }
}

@Composable
private fun ReasoningDisclosure(reasoning: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val str = LocalStrings.current
    Column(modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 2.dp)) {
            Box(Modifier.size(4.dp).background(Color(0xFF6E6E6E), androidx.compose.foundation.shape.CircleShape))
            Text(str.insideHead, style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic, color = Color(0xFF6E6E6E)), modifier = Modifier.padding(start = 7.dp))
        }
        if (expanded) {
            Box(
                Modifier.padding(start = 1.dp, top = 4.dp).border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
            ) { Text(reasoning, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic, color = Color(0xFFA8A8A8))) }
        }
    }
}

// ── Markdown with full formatting — headings, lists, tables, dividers, underline ──

@Composable
fun MarkdownText(markdown: String, color: Color, isCallMode: Boolean = false, modifier: Modifier = Modifier) {
    if (isCallMode) {
        // In call mode: plain, no rich formatting, just readable paragraphs
        Text(markdown.trim(), style = MaterialTheme.typography.bodyLarge.copy(color = color, lineHeight = 24.sp), modifier = modifier)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        parseBlocks(markdown).forEach { block ->
            when (block) {
                is Block.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.displaySmall.copy(color = Color.White, fontSize = 22.sp, lineHeight = 28.sp)
                        2 -> MaterialTheme.typography.headlineSmall.copy(color = Color.White, fontSize = 19.sp, lineHeight = 26.sp)
                        3 -> MaterialTheme.typography.titleLarge.copy(color = Color.White, fontSize = 17.sp, lineHeight = 23.sp)
                        else -> MaterialTheme.typography.titleMedium.copy(color = Color.White, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                    Text(inlineMd(block.text), style = style)
                }
                is Block.Paragraph -> {
                    Text(inlineMd(block.text), style = MaterialTheme.typography.bodyLarge.copy(color = color, lineHeight = 24.sp))
                }
                is Block.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("•", color = Color.White, modifier = Modifier.padding(end = 8.dp).width(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text(inlineMd(item), style = MaterialTheme.typography.bodyLarge.copy(color = color, lineHeight = 24.sp), modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                is Block.NumberedList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEachIndexed { idx, item ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("${idx + 1}.", color = Color(0xFFA8A8A8), modifier = Modifier.padding(end = 8.dp).width(22.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp))
                                Text(inlineMd(item), style = MaterialTheme.typography.bodyLarge.copy(color = color, lineHeight = 24.sp), modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                is Block.Code -> {
                    Text(
                        block.text.trimEnd('\n'),
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFFF2F2F2),
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
                    )
                }
                is Block.Table -> {
                    MarkdownTable(block)
                }
                is Block.Divider -> {
                    HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

// Block model
private sealed class Block {
    data class Heading(val level: Int, val text: String) : Block()
    data class Paragraph(val text: String) : Block()
    data class BulletList(val items: List<String>) : Block()
    data class NumberedList(val items: List<String>) : Block()
    data class Code(val text: String) : Block()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : Block()
    data object Divider : Block()
}

private fun parseBlocks(markdown: String): List<Block> {
    // First, handle fenced code as segments to avoid parsing inside
    val codeSegments = splitFences(markdown)
    val result = mutableListOf<Block>()
    codeSegments.forEach { seg ->
        if (seg.isCode) {
            result.add(Block.Code(seg.text))
        } else {
            result.addAll(parseNonCodeBlocks(seg.text))
        }
    }
    return result
}

private fun parseNonCodeBlocks(text: String): List<Block> {
    val lines = text.lines()
    val blocks = mutableListOf<Block>()
    var i = 0
    val paraBuf = StringBuilder()
    fun flushPara() {
        if (paraBuf.isNotBlank()) {
            blocks.add(Block.Paragraph(paraBuf.toString().trim()))
            paraBuf.clear()
        }
    }
    val bulletBuf = mutableListOf<String>()
    val numberedBuf = mutableListOf<String>()
    fun flushLists() {
        if (bulletBuf.isNotEmpty()) { blocks.add(Block.BulletList(bulletBuf.toList())); bulletBuf.clear() }
        if (numberedBuf.isNotEmpty()) { blocks.add(Block.NumberedList(numberedBuf.toList())); numberedBuf.clear() }
    }

    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trim()

        // Table detection: header row with pipes + separator row
        if (isTableRow(trimmed) && i + 1 < lines.size && isTableSeparator(lines[i + 1].trim())) {
            flushPara(); flushLists()
            val headers = splitTableRow(trimmed)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && isTableRow(lines[i].trim())) {
                rows.add(splitTableRow(lines[i].trim()))
                i++
            }
            blocks.add(Block.Table(headers, rows))
            continue
        }

        when {
            trimmed.isEmpty() -> {
                flushPara(); flushLists()
            }
            isDivider(trimmed) -> {
                flushPara(); flushLists()
                blocks.add(Block.Divider)
            }
            trimmed.startsWith("#") -> {
                flushPara(); flushLists()
                val level = trimmed.takeWhile { it == '#' }.length.coerceAtMost(4)
                val headingText = trimmed.drop(level).trim().removePrefix(" ").trim()
                blocks.add(Block.Heading(level, headingText))
            }
            isBulletLine(trimmed) -> {
                flushPara()
                if (numberedBuf.isNotEmpty()) { blocks.add(Block.NumberedList(numberedBuf.toList())); numberedBuf.clear() }
                bulletBuf.add(trimmed.drop(1).trim().ifEmpty { trimmed.drop(2).trim() })
            }
            isNumberedLine(trimmed) -> {
                flushPara()
                if (bulletBuf.isNotEmpty()) { blocks.add(Block.BulletList(bulletBuf.toList())); bulletBuf.clear() }
                val dotIdx = trimmed.indexOf('.')
                numberedBuf.add(trimmed.substring(dotIdx + 1).trim())
            }
            else -> {
                flushLists()
                if (paraBuf.isNotEmpty()) paraBuf.append("\n")
                paraBuf.append(raw)
            }
        }
        i++
    }
    flushPara(); flushLists()
    return blocks
}

private fun isDivider(s: String): Boolean {
    if (s.length < 3) return false
    val stripped = s.replace(" ", "")
    return (stripped.all { it == '-' } || stripped.all { it == '*' } || stripped.all { it == '_' }) && stripped.length >= 3
}
private fun isBulletLine(s: String): Boolean = s.startsWith("- ") || s.startsWith("* ") || s.startsWith("• ") || s.startsWith("· ")
private fun isNumberedLine(s: String): Boolean {
    val idx = s.indexOf('.')
    if (idx <= 0 || idx > 3) return false
    val numPart = s.substring(0, idx)
    return numPart.all { it.isDigit() } && s.getOrNull(idx + 1) == ' '
}
private fun isTableRow(s: String): Boolean = s.contains("|") && s.count { it == '|' } >= 2
private fun isTableSeparator(s: String): Boolean {
    if (!s.contains("|") || !s.contains("-")) return false
    val cells = s.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    if (cells.isEmpty()) return false
    return cells.all { cell -> cell.matches(Regex("[:\\-]+")) && cell.contains("-") }
}
private fun splitTableRow(s: String): List<String> {
    var row = s.trim()
    if (row.startsWith("|")) row = row.drop(1)
    if (row.endsWith("|")) row = row.dropLast(1)
    return row.split("|").map { it.trim() }
}

@Composable
private fun MarkdownTable(block: Block.Table) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
    ) {
        // header
        Row(Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(vertical = 8.dp)) {
            block.headers.forEach { h ->
                Text(
                    inlineMd(h),
                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                )
            }
        }
        HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 1.dp)
        block.rows.forEachIndexed { idx, row ->
            Row(
                Modifier.fillMaxWidth()
                    .background(if (idx % 2 == 0) Color(0xFF0A0A0A) else Color(0xFF0A0A0A))
                    .padding(vertical = 8.dp)
            ) {
                row.forEachIndexed { colIdx, cell ->
                    val header = block.headers.getOrNull(colIdx) ?: ""
                    Text(
                        inlineMd(cell),
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFF2F2F2)),
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                    )
                }
                // pad missing cells
                repeat((block.headers.size - row.size).coerceAtLeast(0)) {
                    Spacer(Modifier.weight(1f))
                }
            }
            if (idx < block.rows.lastIndex) HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 1.dp)
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
        if (start < 0) { if (rest.isNotEmpty()) segments += Segment(rest, false); break }
        if (start > 0) segments += Segment(rest.substring(0, start), false)
        val afterOpening = rest.indexOf('\n', start).let { if (it < 0) rest.length else it + 1 }
        val close = rest.indexOf("```", afterOpening)
        if (close < 0) { segments += Segment(rest.substring(afterOpening), true); break }
        segments += Segment(rest.substring(afterOpening, close), true)
        rest = rest.substring(close + 3)
    }
    return segments
}

private fun inlineMd(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val bold = SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.White)
    val italic = SpanStyle(fontStyle = FontStyle.Italic)
    val underline = SpanStyle(textDecoration = TextDecoration.Underline)
    val code = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, background = Color(0xFF1E1E1E))
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end < 0) { append(text.substring(i)); i = text.length } else { withStyle(bold) { append(text.substring(i + 2, end)) }; i = end + 2 }
            }
            text.startsWith("__", i) -> {
                val end = text.indexOf("__", i + 2)
                if (end < 0) { append(text.substring(i)); i = text.length } else { withStyle(underline) { append(text.substring(i + 2, end)) }; i = end + 2 }
            }
            text.startsWith("<u>", i) -> {
                val end = text.indexOf("</u>", i + 3)
                if (end < 0) { append(text.substring(i)); i = text.length } else { withStyle(underline) { append(text.substring(i + 3, end)) }; i = end + 4 }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end < 0) { append(text.substring(i)); i = text.length } else { withStyle(code) { append(text.substring(i + 1, end)) }; i = end + 1 }
            }
            text[i] == '*' && i + 1 < text.length && text[i + 1] != ' ' -> {
                val end = text.indexOf('*', i + 1)
                if (end < 0) { append(text.substring(i)); i = text.length } else { withStyle(italic) { append(text.substring(i + 1, end)) }; i = end + 1 }
            }
            text.startsWith("_", i) && i + 1 < text.length && text[i + 1] != ' ' && text[i + 1] != '_' -> {
                val end = text.indexOf('_', i + 1)
                if (end < 0) { append(text.substring(i)); i = text.length } else { withStyle(italic) { append(text.substring(i + 1, end)) }; i = end + 1 }
            }
            else -> {
                val nextSpecial = generateSequence(i + 1) { it + 1 }.takeWhile { it <= text.length }.firstOrNull {
                    it == text.length || text.startsWith("**", it) || text.startsWith("__", it) || text.startsWith("<u>", it) || text[it] == '`' || (text[it] == '*' && it + 1 < text.length && text[it + 1] != ' ') || (text[it] == '_' && it + 1 < text.length && text[it + 1] != ' ' && text[it + 1] != '_')
                } ?: text.length
                append(text.substring(i, nextSpecial))
                i = nextSpecial
            }
        }
    }
}
