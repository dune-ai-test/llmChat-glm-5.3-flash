import io

p = 'app/src/main/java/com/mrrob/llmchat/ui/AsterNavigation.kt'
s = io.open(p, encoding='utf-8', newline='').read().replace('\r\n', '\n')

start = s.find('/** Root with the floating pill tab bar over the five sections. */')
end = s.find('/** Hosts the voice tab with its own view model instance. */')
assert start > 0 and end > start, (start, end)

new = '''/**
 * Root scaffold: five swipeable pages sharing one slide animation (pager),
 * presented through either the floating bottom bar or a side rail - chosen
 * in Appearance settings.
 */
@Composable
private fun MainScaffold(vm: AppViewModel, nav: NavHostController) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val c = LocalScheme.current
    val side = settings.navMode == "side"
    val pager = rememberPagerState(initialPage = tab.ordinal) { AsterTab.entries.size }

    // Settings selection drives the pager...
    LaunchedEffect(tab) {
        if (pager.targetPage != tab.ordinal) pager.animateScrollToPage(tab.ordinal)
    }
    // ...and swiping drives the selection back (on the settled page only).
    LaunchedEffect(pager) {
        androidx.compose.runtime.snapshotFlow { pager.settledPage }.collect { page ->
            val want = AsterTab.entries.getOrNull(page) ?: return@collect
            if (vm.tab.value != want) vm.selectTab(want)
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(c.bg)) {
        if (side) {
            AsterSideRail(selected = tab, onSelect = { vm.selectTab(it) })
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                val isVoice = page == AsterTab.VOICE.ordinal
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(bottom = if (isVoice || side) 0.dp else 84.dp)
                ) {
                    when (page) {
                        0 -> HomeScreen(
                            app = vm,
                            onOpenChat = { id -> vm.openConversation(id) },
                            onSeeAll = { nav.navigate("recent") },
                            onOpenSettings = { vm.selectTab(AsterTab.SETTINGS) },
                            onOpenSearch = { nav.navigate("search") },
                            onVoice = { vm.selectTab(AsterTab.VOICE) }
                        )
                        1 -> ChatsTab(
                            app = vm,
                            onOpenChat = { id -> vm.openConversation(id) },
                            onOpenSearch = { nav.navigate("search") },
                            onOpenArchived = { nav.navigate("archived") }
                        )
                        2 -> VoiceTabHost(vm) { vm.selectTab(AsterTab.HOME) }
                        3 -> ConnectionsTab(
                            app = vm,
                            onEdit = { conn -> vm.startEditConnection(conn) },
                            onAdd = { vm.startWizard() }
                        )
                        4 -> SettingsScreen(
                            app = vm,
                            onNavigate = { route -> nav.navigate(route) }
                        )
                    }
                }
            }

            if (!side) {
                FloatingTabBar(
                    selected = tab,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onSelect = { vm.selectTab(it) }
                )
            }
        }
    }
}

/** Material-style vertical rail: floating card of tab icons, left of content. */
@Composable
private fun AsterSideRail(
    selected: AsterTab,
    onSelect: (AsterTab) -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    val items = listOf(
        AsterTab.HOME to ("Home" to IconsL.home),
        AsterTab.CHATS to ("Chats" to IconsL.chatBubble),
        AsterTab.VOICE to ("Voice" to IconsL.mic),
        AsterTab.CONNECTIONS to ("APIs" to IconsL.server),
        AsterTab.SETTINGS to ("Settings" to IconsL.settings)
    )
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 10.dp, end = 2.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .width(84.dp)
                .fillMaxHeight()
                .shadow(12.dp, RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(c.card)
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.accentTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(IconsL.sparkles, null, tint = c.accent, modifier = Modifier.size(18.dp))
            }
            items.forEach { (item, pair) ->
                val isSel = item == selected
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSel) c.accentTint else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(item) }
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        pair.second, pair.first,
                        tint = if (isSel) c.accent else c.textMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        pair.first,
                        style = t.tiny.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Medium),
                        color = if (isSel) c.accent else c.textMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

'''
s = s[:start] + new + s[end:]

# imports for pager + fillMaxHeight + Color + shadow (already there) 
need = [
    'import androidx.compose.foundation.pager.HorizontalPager',
    'import androidx.compose.foundation.pager.rememberPagerState',
    'import androidx.compose.foundation.layout.fillMaxHeight',
    'import androidx.compose.ui.graphics.Color',
]
for imp in need:
    if imp not in s:
        s = s.replace('import androidx.compose.foundation.layout.fillMaxSize\n',
                      'import androidx.compose.foundation.layout.fillMaxSize\n' + imp + '\n', 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('main scaffold ok')
