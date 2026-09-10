package com.mrrob.llmchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mrrob.llmchat.AppViewModel
import com.mrrob.llmchat.AsterApp
import com.mrrob.llmchat.AsterTab
import com.mrrob.llmchat.ChatViewModel
import com.mrrob.llmchat.VoiceViewModel
import com.mrrob.llmchat.ui.kit.IconsL
import com.mrrob.llmchat.ui.theme.AsterTheme
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType
import kotlinx.coroutines.launch

@Composable
fun AsterRoot(app: AsterApp) {
    val settings by app.container.settingsStore.settings.collectAsStateWithLifecycle()
    AsterTheme(
        themeMode = settings.themeMode,
        fontScaleValue = settings.fontScale,
        compactDensity = settings.chatDensity == "compact",
        accentTheme = settings.accentTheme
    ) {
        val vm: AppViewModel = viewModel(factory = AppViewModel.factory(app))
        AsterNavigation(vm)
    }
}

@Composable
private fun AsterNavigation(vm: AppViewModel) {
    val nav: NavHostController = rememberNavController()
    val navigate by vm.navigate.collectAsStateWithLifecycle()
    val openChat by vm.openConversationId.collectAsStateWithLifecycle()
    val c = LocalScheme.current
    var crashDismissed by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(false)
    }

    val crash = vm.lastCrash
    if (crash != null && !crashDismissed) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { crashDismissed = true },
            containerColor = c.card,
            icon = { Icon(IconsL.warning, null, tint = c.danger) },
            title = { Text("The app crashed on the previous run", color = c.textPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text(crash, style = LocalType.current.mono, color = c.textSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearCrashLog()
                        crashDismissed = true
                    }
                ) { Text("Clear", color = c.danger) }
            },
            dismissButton = {
                TextButton(onClick = { crashDismissed = true }) { Text("Dismiss", color = c.accent) }
            }
        )
    }

    LaunchedEffect(navigate) {
        navigate?.let {
            nav.navigate(it)
            vm.consumeNavigation()
        }
    }
    LaunchedEffect(openChat) {
        openChat?.let { id ->
            nav.navigate("chat/$id")
            vm.consumeOpenConversation()
        }
    }

    val start = if (vm.settings.value.onboardingDone) "main" else "splash"
    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = {
            androidx.compose.animation.slideInHorizontally(tween(260)) { full -> full / 4 } +
                androidx.compose.animation.fadeIn(tween(260))
        },
        exitTransition = {
            androidx.compose.animation.fadeOut(tween(140)) +
                androidx.compose.animation.slideOutHorizontally(tween(260)) { full -> -full / 5 }
        },
        popEnterTransition = {
            androidx.compose.animation.slideInHorizontally(tween(260)) { full -> -full / 5 } +
                androidx.compose.animation.fadeIn(tween(260))
        },
        popExitTransition = {
            androidx.compose.animation.fadeOut(tween(140)) +
                androidx.compose.animation.slideOutHorizontally(tween(260)) { full -> full / 4 }
        }
    ) {
        composable("splash") {
            SplashScreen(onFinished = {
                nav.navigate(if (vm.settings.value.onboardingDone) "main" else "onboarding") {
                    popUpTo("splash") { inclusive = true }
                }
            })
        }
        composable("onboarding") {
            OnboardingScreen(
                onGetStarted = {
                    vm.updateSettings { it.copy(onboardingDone = true) }
                    vm.startWizard()
                },
                onSkip = {
                    vm.updateSettings { it.copy(onboardingDone = true) }
                    nav.navigate("main") { popUpTo("onboarding") { inclusive = true } }
                }
            )
        }
        composable("main") {
            MainScaffold(vm, nav)
        }
        composable(
            route = "chat/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("conversationId").orEmpty()
            val chatVm: ChatViewModel = viewModel(
                key = "chat-$id",
                factory = ChatVmFactory(vm, id)
            )
            ChatScreen(
                vm = chatVm,
                favorites = vm.favoriteModels(),
                onToggleFavorite = vm::toggleFavoriteModel,
                onBack = { nav.popBackStack() },
                onVoice = {
                    nav.popBackStack("main", inclusive = false)
                    vm.selectTab(AsterTab.VOICE)
                },
                onOpenConnections = {
                    nav.popBackStack("main", inclusive = false)
                    vm.selectTab(AsterTab.CONNECTIONS)
                }
            )
        }
        composable("archived") {
            ArchivedScreen(
                app = vm,
                onBack = { nav.popBackStack() },
                onOpenChat = { id -> vm.openConversation(id) }
            )
        }
        composable("search") {
            SearchScreen(
                app = vm,
                onBack = { nav.popBackStack() },
                onOpenChat = { id -> vm.openConversation(id) }
            )
        }
        composable("recent") {
            RecentActivityScreen(
                app = vm,
                onBack = { nav.popBackStack() },
                onOpenChat = { id -> vm.openConversation(id) },
                onOpenSearch = { nav.navigate("search") }
            )
        }
        composable("wizard/start") {
            WizardIntro(
                app = vm,
                onBegin = { nav.navigate("wizard/provider") },
                onBack = { nav.popBackStack() }
            )
        }
        composable("wizard/provider") {
            WizardProvider(
                app = vm,
                onContinue = { nav.navigate("wizard/config") },
                onBack = { nav.popBackStack("wizard/start", false) }
            )
        }
        composable("wizard/config") {
            val configScope = androidx.compose.runtime.rememberCoroutineScope()
            WizardConfig(
                app = vm,
                onTest = { nav.navigate("wizard/test") },
                onContinue = {
                    configScope.launch {
                        vm.wizardSaveAndFinish()
                        nav.navigate("main") {
                            popUpTo("main") { inclusive = true }
                        }
                    }
                },
                onBack = { nav.popBackStack() }
            )
        }
        composable("wizard/test") {
            WizardTest(
                app = vm,
                onDone = {
                    nav.navigate("main") {
                        popUpTo("main") { inclusive = true }
                    }
                },
                onEditBack = { nav.popBackStack() },
                onCancel = { nav.popBackStack() }
            )
        }
        composable("settings/chat") { ChatSettingsScreen(vm) { nav.popBackStack() } }
        composable("settings/voice") { VoiceSettingsScreen(vm) { nav.popBackStack() } }
        composable("settings/appearance") { AppearanceScreen(vm) { nav.popBackStack() } }
        composable("settings/advanced") { AdvancedApiScreen(vm) { nav.popBackStack() } }
    }
}

/**
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

/** Floating pill-shaped bottom navigation bar (the default tab surface). */
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

/** Hosts the voice tab with its own view model instance. */
@Composable
private fun VoiceTabHost(vm: AppViewModel, onExit: () -> Unit) {
    val voiceVm: VoiceViewModel = viewModel(factory = VoiceVmFactory(vm))
    VoiceScreen(
        app = vm,
        vm = voiceVm,
        onExit = onExit,
        onOpenSettings = { vm.selectTab(AsterTab.SETTINGS) }
    )
}

class ChatVmFactory(
    private val app: AppViewModel,
    private val conversationId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(app, conversationId) as T
}

class VoiceVmFactory(private val app: AppViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        VoiceViewModel(app) as T
}
