package com.mrrob.llmchat.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrrob.llmchat.ui.theme.LocalDensity
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType

/** Tactile, ripple-free press (iOS style). */
@Composable
fun Pressable(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
    )
}

@Composable
fun Modifier.noRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
    indication = null,
    onClick = onClick
)

/** Grouped white card with the #E9E9E5 hairline. */
@Composable
fun AsterCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(all = LocalDensity.current.cardPad),
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalScheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.border, shape)
            .then(if (onClick != null) Modifier.noRipple(onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

/** A hairline row divider inside cards, inset to label start. */
@Composable
fun CardDivider(insetStart: Boolean = true) {
    val c = LocalScheme.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = if (insetStart) 16.dp else 0.dp)
            .height(1.dp)
            .background(c.border)
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalScheme.current
    Text(
        text = text.uppercase(),
        style = LocalType.current.caption.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        ),
        color = c.textMuted,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

/**
 * Settings row: optional leading icon tile, label, optional right value,
 * and either a chevron or a trailing slot (switch/slider).
 */
@Composable
fun AsterRow(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    value: String? = null,
    showChevron: Boolean = false,
    destructive: Boolean = false,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.noRipple(onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                IconTile(icon = icon, size = 30.dp, tileRadius = 9.dp, iconSize = 15.dp)
                Spacer(Modifier.size(12.dp))
            }
            Text(
                text = label,
                style = t.rowTitle,
                color = if (destructive) c.danger else c.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (value != null) {
                Text(text = value, style = t.caption, color = c.textMuted)
                Spacer(Modifier.size(6.dp))
            }
            if (trailing != null) {
                trailing()
            } else if (showChevron) {
                Icon(IconsL.chevronRight, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
            }
        }
        if (description != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = t.caption,
                color = c.textMuted,
                modifier = Modifier.padding(start = if (icon != null) 42.dp else 0.dp)
            )
        }
    }
}

/** Small rounded icon tile (the #ECECFB / #F0F0ED squares from the design). */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tileRadius: Dp = 15.dp,
    iconSize: Dp = 20.dp,
    tint: Color? = null,
    background: Color? = null
) {
    val c = LocalScheme.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(tileRadius))
            .background(background ?: c.accentTint),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint ?: c.accent, modifier = Modifier.size(iconSize))
    }
}

/** The full-width 54dp primary / secondary buttons from the wizard + empty states. */
@Composable
fun AsterButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    loading: Boolean = false,
    icon: ImageVector? = null,
    radius: Dp = 18.dp,
    height: Dp = 54.dp,
    onClick: () -> Unit
) {
    val c = LocalScheme.current
    val shape = RoundedCornerShape(radius)
    val bg = when (variant) {
        ButtonVariant.PRIMARY -> if (enabled) c.accent else c.accent.copy(alpha = 0.45f)
        ButtonVariant.SECONDARY -> c.card
        ButtonVariant.NEUTRAL -> c.fill
    }
    val fg = when (variant) {
        ButtonVariant.PRIMARY -> Color.White
        else -> c.textPrimary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(bg)
            .then(
                if (variant != ButtonVariant.PRIMARY) Modifier.border(1.dp, c.border, shape) else Modifier
            )
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                enabled = enabled && !loading
            ) { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = fg)
            Spacer(Modifier.size(10.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(text = text, style = LocalType.current.button, color = fg)
    }
}

enum class ButtonVariant { PRIMARY, SECONDARY, NEUTRAL }

/** Pill chip (provider chips, filters). */
@Composable
fun AsterChip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val c = LocalScheme.current
    val shape = RoundedCornerShape(99.dp)
    val bg = if (selected) c.textPrimary else c.card
    val border = if (selected) c.textPrimary else c.border
    val fg = if (selected) c.bg else c.textSecondary
    Row(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.noRipple(onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = LocalType.current.desc.copy(fontWeight = FontWeight.SemiBold),
            color = fg
        )
    }
}

/** Set to a lambda by MainScaffold when navigation mode is "drawer". */
val LocalDrawerOpener = androidx.compose.runtime.compositionLocalOf<(() -> Unit)?> { null }

/** The uniform, non-scrolling top bar: large title + optional leading + actions. */
@Composable
fun ScreenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    val c = LocalScheme.current
    val opener = LocalDrawerOpener.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading == null && opener != null) {
            CircleIconButton(
                icon = IconsL.menu,
                onClick = opener,
                size = 40.dp,
                iconSize = 20.dp,
                bordered = false
            )
            Spacer(Modifier.width(10.dp))
        }
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            title,
            style = LocalType.current.largeTitle,
            color = c.textPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (actions != null) actions()
    }
    Spacer(Modifier.height(2.dp))
}

/** iOS switch matching the design: 46x27 pill, indigo ON track. */
@Composable
fun AsterSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = LocalScheme.current
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 27.dp)
            .clip(CircleShape)
            .background(if (checked) c.accent else c.fill)
            .border(if (checked) 0.dp else 1.dp, c.border, CircleShape)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
    ) {
        val target = if (checked) 19f else 0f
        val animated by animateFloatAsState(
            targetValue = target,
            animationSpec = androidx.compose.animation.core.spring<Float>(
                dampingRatio = 0.8f,
                stiffness = 380f
            ),
            label = "knob"
        )
        Box(
            modifier = Modifier
                .size(19.dp)
                .align(Alignment.CenterStart)
                .offset(x = (animated + 4).dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** Slider row: label + thin accent-filled track + right value, no drawn thumb. */
@Composable
fun AsterSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalScheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = LocalType.current.rowTitle, color = c.textPrimary)
        Spacer(Modifier.size(14.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .pointerInput(range) {
                    fun apply(offsetX: Float) {
                        val fraction = (offsetX / size.width).coerceIn(0f, 1f)
                        onValueChange(range.start + fraction * (range.endInclusive - range.start))
                    }
                    detectTapGestures { offset -> apply(offset.x) }
                }
                .pointerInput(range) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onValueChange(range.start + fraction * (range.endInclusive - range.start))
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.fill)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(c.accent)
                )
            }
        }
        Spacer(Modifier.size(14.dp))
        Text(
            valueText,
            style = LocalType.current.caption.copy(fontWeight = FontWeight.SemiBold),
            color = c.accent
        )
    }
}

/** Segmented picker inside a card: dark selected pill, options flex 1. */
@Composable
fun AsterSegmented(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    optionStyle: ((Int) -> TextStyle)? = null,
    onSelect: (Int) -> Unit
) {
    val c = LocalScheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 13.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(c.bg),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) c.textPrimary else Color.Transparent)
                    .noRipple { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = (optionStyle?.invoke(i) ?: LocalType.current.desc).copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) c.bg else c.textSecondary
                )
            }
        }
    }
}

/** Green/amber "Connected"/"Offline" status pill used on Home + sheets. */
@Composable
fun StatusPill(status: String, online: Boolean, modifier: Modifier = Modifier) {
    val c = LocalScheme.current
    val color = if (online) c.success else c.warning
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (online) c.successTint else c.warningTint)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(
            status,
            style = LocalType.current.caption.copy(fontWeight = FontWeight.SemiBold, color = color)
        )
    }
}

@Composable
fun StatusDot(online: Boolean, modifier: Modifier = Modifier, dotSize: Dp = 5.dp) {
    val c = LocalScheme.current
    Box(
        modifier
            .size(dotSize)
            .clip(CircleShape)
            .background(if (online) c.success else c.warning)
    )
}

/** Back-chevron header used on pushed detail screens (sub-settings). */
@Composable
fun AsterBackHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalScheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .noRipple(onBack)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(IconsL.chevronLeft, "Back", tint = c.textPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(2.dp))
        Text(title, style = LocalType.current.cardTitle, color = c.textPrimary)
    }
}

/** Icon button in a circle with a hairline border (search / add / avatar slots). */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 18.dp,
    background: Color? = null,
    tint: Color? = null,
    bordered: Boolean = true
) {
    val c = LocalScheme.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background ?: c.card)
            .then(if (bordered) Modifier.border(1.dp, c.border, CircleShape) else Modifier)
            .noRipple(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint ?: c.textSecondary, modifier = Modifier.size(iconSize))
    }
}

/** Generic tinted info strip: icon + text on a colored rounded background. */
@Composable
fun InfoStrip(
    text: String,
    icon: ImageVector,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(9.dp))
        Text(text, style = LocalType.current.caption.copy(color = tint), modifier = Modifier.weight(1f))
    }
}
