import io

p = 'app/src/main/java/com/mrrob/llmchat/ui/AsterNavigation.kt'
s = io.open(p, encoding='utf-8', newline='').read().replace('\r\n', '\n')
anchor = '/** Hosts the voice tab with its own view model instance. */'
assert s.count(anchor) == 1

block = '''/** Floating pill-shaped bottom navigation bar (the default tab surface). */
@Composable
private fun FloatingTabBar(
    selected: AsterTab,
    modifier: Modifier = Modifier,
    onSelect: (AsterTab) -> Unit
) {
    val c = LocalScheme.current
    val tabs = listOf(
        AsterTab.HOME to ("Home" to IconsL.home),
        AsterTab.CHATS to ("Chats" to IconsL.chatBubble),
        AsterTab.VOICE to ("Voice" to IconsL.mic),
        AsterTab.CONNECTIONS to ("APIs" to IconsL.server),
        AsterTab.SETTINGS to ("Settings" to IconsL.settings)
    )
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(c.card.copy(alpha = 0.94f))
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            tabs.forEach { (item, pair) ->
                TabItem(
                    label = pair.first,
                    icon = pair.second,
                    selected = item == selected,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(item) }
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) c.accentTint else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 9.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            icon, label,
            tint = if (selected) c.accent else c.textMuted,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            style = t.tiny.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
            color = if (selected) c.accent else c.textMuted,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip
        )
    }
}

'''
s = s.replace(anchor, block + anchor, 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('tab bar restored')
