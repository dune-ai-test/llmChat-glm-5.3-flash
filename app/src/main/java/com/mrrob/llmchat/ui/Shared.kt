package com.mrrob.llmchat.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mrrob.llmchat.data.ConversationEntity
import com.mrrob.llmchat.ui.kit.IconTile
import com.mrrob.llmchat.ui.kit.IconsL
import com.mrrob.llmchat.ui.theme.LocalDensity
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Section labels used by grouped chat lists, matching the design. */
enum class ChatSection(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    WEEK("Previous 7 days"),
    OLDER("Older")
}

fun conversationSection(updatedAt: Long): ChatSection {
    fun midnightOf(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val days = ((midnightOf(System.currentTimeMillis()) - midnightOf(updatedAt)) / 86_400_000L).toInt()
    return when {
        days <= 0 -> ChatSection.TODAY
        days == 1 -> ChatSection.YESTERDAY
        days < 7 -> ChatSection.WEEK
        else -> ChatSection.OLDER
    }
}

fun groupedSections(conversations: List<ConversationEntity>): List<Pair<ChatSection, List<ConversationEntity>>> {
    val groups = LinkedHashMap<ChatSection, MutableList<ConversationEntity>>()
    conversations.forEach { c ->
        groups.getOrPut(conversationSection(c.updatedAt)) { mutableListOf() }.add(c)
    }
    return listOf(ChatSection.TODAY, ChatSection.YESTERDAY, ChatSection.WEEK, ChatSection.OLDER)
        .mapNotNull { section -> groups[section]?.let { section to it } }
}

/** Short timestamp for list rows: "9:41 AM", "Yesterday", "Mon", "Sep 2". */
fun shortTimestamp(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    return when {
        then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
            then.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
        else -> {
            val dayDiff = (now.timeInMillis - millis) / (24L * 3600 * 1000)
            when {
                dayDiff < 1 -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
                dayDiff < 2 -> "Yesterday"
                dayDiff < 7 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(millis))
                else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))
            }
        }
    }
}

/**
 * The shared conversation row: bubble tile, title + preview, time + model chip.
 * Used on Home, Recent activity, Chats tab and Search results.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListRow(
    conversation: ConversationEntity,
    showChip: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    highlight: String = "",
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick() },
                onLongClick = onLongClick
            )
            .padding(vertical = LocalDensity.current.listRowV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.card)
                .border(1.dp, c.border, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon ?: when {
                    conversation.kind == "JUDGE" -> IconsL.scale
                    conversation.voice -> IconsL.mic
                    else -> IconsL.chatSquare
                },
                contentDescription = null,
                tint = if (conversation.voice || conversation.kind == "JUDGE") c.accent else c.textSecondary,
                modifier = Modifier.size(17.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = highlightText(conversation.title, highlight),
                style = t.desc.copy(fontWeight = FontWeight.SemiBold, fontSize = t.desc.fontSize * 1.14f),
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (conversation.lastPreview.isNotBlank()) {
                Text(
                    text = highlightText(conversation.lastPreview, highlight),
                    style = t.micro.copy(fontSize = t.micro.fontSize * 1.18f),
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(shortTimestamp(conversation.updatedAt), style = t.caption, color = c.textMuted)
            if (showChip && conversation.model.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(c.fill)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        conversation.model,
                        style = t.tiny.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun highlightText(text: String, query: String): AnnotatedString {
    val c = LocalScheme.current
    return buildAnnotatedString {
        if (query.isBlank()) {
            append(text)
            return@buildAnnotatedString
        }
        val lower = text.lowercase()
        val needle = query.lowercase()
        var index = 0
        while (index < text.length) {
            val found = lower.indexOf(needle, index)
            if (found < 0) {
                append(text.substring(index))
                break
            }
            append(text.substring(index, found))
            withStyle(SpanStyle(background = c.accentTint, color = c.textPrimary, fontWeight = FontWeight.SemiBold)) {
                append(text.substring(found, found + needle.length))
            }
            index = found + needle.length
        }
    }
}

/** Time-of-day greeting for the home header. */
fun greeting(): String = greetingFor(System.currentTimeMillis())

/** Greeting for a specific moment - lets the UI re-evaluate as time passes. */
fun greetingFor(millis: Long): String {
    val hour = Calendar.getInstance().apply { timeInMillis = millis }
        .get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}
