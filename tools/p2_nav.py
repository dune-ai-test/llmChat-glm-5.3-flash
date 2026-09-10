import io

p = 'app/src/main/java/com/mrrob/llmchat/ui/AsterNavigation.kt'
s = io.open(p, encoding='utf-8', newline='').read().replace('\r\n', '\n')

def rep(o, n):
    global s
    assert s.count(o) == 1, (o[:60], s.count(o))
    s = s.replace(o, n)

# ---- AsterRoot: system-bar icons follow app theme ----
rep('''@Composable
fun AsterRoot(app: AsterApp) {
    val settings by app.container.settingsStore.settings.collectAsStateWithLifecycle()
    AsterTheme(''',
'''@Composable
fun AsterRoot(app: AsterApp) {
    val settings by app.container.settingsStore.settings.collectAsStateWithLifecycle()

    // Make the status/navigation bar icons follow the app's chosen mode.
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val appDark = when (settings.themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val view = androidx.compose.ui.platform.LocalView.current
    val window = (view.context as? android.app.Activity)?.window
    androidx.compose.runtime.SideEffect {
        window?.let { w ->
            val controller = androidx.core.view.WindowCompat.getInsetsController(w, view)
            controller.isAppearanceLightStatusBars = !appDark
            controller.isAppearanceLightNavigationBars = !appDark
        }
    }

    AsterTheme(''')

# ---- MainScaffold: state vars ----
rep('''    val side = settings.navMode == "side"
    val pager = rememberPagerState(initialPage = tab.ordinal) { AsterTab.entries.size }''',
'''    val drawerMode = settings.navMode == "side"
    val pager = rememberPagerState(initialPage = tab.ordinal) { AsterTab.entries.size }
    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val uiScope = rememberCoroutineScope()''')

# ---- wrap in ModalNavigationDrawer ----
rep('''    Row(modifier = Modifier.fillMaxSize().background(c.bg)) {
        if (side) {
            AsterSideRail(selected = tab, onSelect = { vm.selectTab(it) })
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            HorizontalPager(''',
'''    val drawerOpener: (() -> Unit)? =
        if (drawerMode) { { uiScope.launch { drawerState.open() } } } else null
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.mrrob.llmchat.ui.kit.LocalDrawerOpener provides drawerOpener
        ) {
            androidx.compose.material3.ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    if (drawerMode) {
                        AsterDrawer(
                            selected = tab,
                            onSelect = { target ->
                                vm.selectTab(target)
                                uiScope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            ) {
            HorizontalPager(''')

# ---- page box padding: voice needs clearance too ----
rep('''                val isVoice = page == AsterTab.VOICE.ordinal
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(bottom = if (isVoice || side) 0.dp else 84.dp)
                ) {''',
'''                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(bottom = if (drawerMode) 12.dp else 84.dp)
                ) {''')

# ---- tail: FloatingTabBar condition + close extra scopes + drawer composable ----
rep('''            if (!side) {
                FloatingTabBar(
                    selected = tab,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onSelect = { vm.selectTab(it) }
                )
            }
        }
    }
}''',
'''            if (!drawerMode) {
                FloatingTabBar(
                    selected = tab,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onSelect = { vm.selectTab(it) }
                )
            }
            }  // ModalNavigationDrawer content
        }  // provider
    }
}

/** The navigation drawer shown when Appearance > Navigation = "Sidebar". */
@Composable
private fun AsterDrawer(
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
    androidx.compose.material3.ModalDrawerSheet(modifier = Modifier.width(288.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 20.dp)
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(c.accentTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(IconsL.sparkles, null, tint = c.accent, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("Aster", style = t.cardTitle, color = c.textPrimary)
                    Text("Your AI workspace", style = t.tiny, color = c.textMuted)
                }
            }
            items.forEach { (item, pair) ->
                val isSel = item == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSel) c.accentTint else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(item) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        pair.second, pair.first,
                        tint = if (isSel) c.accent else c.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        pair.first,
                        style = t.rowTitle.copy(fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (isSel) c.accent else c.textPrimary
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Version 1.2.0",
                style = t.tiny,
                color = c.textMuted,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
            )
        }
    }
}''')

# ---- remove the permanent rail ----
st = s.find('/** Material-style vertical rail: floating card of tab icons, left of content. */')
en = s.find('/** Hosts the voice tab with its own view model instance. */')
assert 0 < st < en, (st, en)
s = s[:st] + s[en:]

# ---- imports ----
for imp in ['import androidx.compose.runtime.rememberCoroutineScope', 'import kotlinx.coroutines.launch']:
    if imp not in s:
        s = s.replace('import androidx.compose.ui.Modifier\n', imp + '\nimport androidx.compose.ui.Modifier\n', 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('nav patched')
