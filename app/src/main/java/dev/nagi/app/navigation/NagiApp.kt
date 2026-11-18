package dev.nagi.app.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.nagi.app.viewmodel.AppUpdateViewModel
import dev.nagi.app.viewmodel.NotificationsViewModel
import dev.nagi.app.viewmodel.NotificationsState
import dev.nagi.app.ui.components.LocalAppUpdate
import dev.nagi.app.ui.components.LocalOpenLogin
import dev.nagi.app.ui.components.LocalOpenUser
import dev.nagi.app.ui.components.LocalNotifications
import dev.nagi.app.ui.components.LocalUserTags
import dev.nagi.app.ui.components.UserTagAccess
import dev.nagi.app.viewmodel.UserTagsState
import dev.nagi.app.viewmodel.UserTagsViewModel
import dev.nagi.app.ui.library.NotificationsScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.LocalAccountIdentity
import dev.nagi.app.ui.components.LocalNagiBlurEnabled
import dev.nagi.app.ui.components.LocalNagiHaze
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.hardwareBlurAllowed
import dev.nagi.app.ui.forum.ForumScreen
import dev.nagi.app.ui.library.AboutScreen
import dev.nagi.app.ui.library.AccountsScreen
import dev.nagi.app.ui.library.FavoritesScreen
import dev.nagi.app.ui.library.HistoryScreen
import dev.nagi.app.ui.library.OwnProfileScreen
import dev.nagi.app.ui.library.SettingsScreen
import dev.nagi.app.ui.library.UserContentScreen
import dev.nagi.app.ui.login.LoginScreen
import dev.nagi.app.ui.post.NewMessageScreen
import dev.nagi.app.ui.post.MessagesScreen
import dev.nagi.app.ui.post.ReaderScreen
import dev.nagi.app.ui.search.SearchScreen
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.ui.topic.TopicsScreen
import dev.nagi.app.viewmodel.AccountIdentity
import dev.nagi.app.viewmodel.AccountIdentityViewModel
import dev.nagi.app.viewmodel.AccountViewModel
import dev.nagi.app.viewmodel.LibraryViewModel
import dev.nagi.sdk.ExtractedExternalLink
import dev.nagi.sdk.Forum
import dev.nagi.sdk.NgaExternalLinks
import dev.nagi.sdk.NgaExternalTarget

@Composable
fun NagiApp(
    width: WindowWidthSizeClass,
    accountVm: AccountViewModel = hiltViewModel(),
    deepLinkUri: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    clipboardLink: ExtractedExternalLink? = null,
    onClipboardLinkHandled: (ExtractedExternalLink) -> Unit = {},
) {
    val preferences by accountVm.preferences.collectAsStateWithLifecycle()
    val account by accountVm.account.collectAsStateWithLifecycle()
    val identityVm: AccountIdentityViewModel = hiltViewModel()
    val loadedIdentity by identityVm.identity.collectAsStateWithLifecycle()
    val identity = loadedIdentity.takeIf { it.revision == account.revision && it.userId == account.userId }
        ?: AccountIdentity(account.revision, account.userId, loading = account.userId != null)
    val notificationsVm: NotificationsViewModel = hiltViewModel()
    val loadedNotifications by notificationsVm.state.collectAsStateWithLifecycle()
    val notifications = loadedNotifications.takeIf { !account.busy && it.revision == account.revision && it.userId == account.userId }
        ?: NotificationsState(account.revision, account.userId)
    val tagsVm: UserTagsViewModel = hiltViewModel()
    val loadedTags by tagsVm.state.collectAsStateWithLifecycle()
    val tags = loadedTags.takeIf { !account.busy && it.revision == account.revision && it.userId == account.userId }
        ?: UserTagsState(account.revision, account.userId)
    val tagAccess = UserTagAccess(tags) { userId, text, done -> tagsVm.save(tags, userId, text, done) }
    val updateVm: AppUpdateViewModel = hiltViewModel()
    val appUpdate by updateVm.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, notificationsVm, updateVm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                notificationsVm.setForeground(true)
                updateVm.checkIfNeeded()
            }
            if (event == Lifecycle.Event.ON_STOP) notificationsVm.setForeground(false)
        }
        lifecycle.addObserver(observer)
        notificationsVm.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycle.removeObserver(observer); notificationsVm.setForeground(false) }
    }
    CompositionLocalProvider(LocalAccountIdentity provides identity, LocalNotifications provides notifications,
        LocalUserTags provides tagAccess, LocalAppUpdate provides appUpdate) {
        NagiTheme(preferences.theme) {
            val colors = NagiThemeTokens.colors
            if (!account.ready) {
                Surface(Modifier.fillMaxSize(), color = colors.groupedCanvas, shadowElevation = 0.dp, tonalElevation = 0.dp) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        if (account.error != null) Notice(account.error!!, accountVm::initialize)
                        else LoadingLine()
                    }
                }
                return@NagiTheme
            }
            BackHandler(account.busy) { }
            AccountReadingScope(account.revision) {
                val library: LibraryViewModel = hiltViewModel()
                val directory by library.forums.collectAsStateWithLifecycle()
                val forumFavorites by library.forumFavorites.collectAsStateWithLifecycle()
                val favoriteForumKeys = remember(forumFavorites) { forumFavorites.map { it.key }.toSet() }
                val cloudForumFavorites by library.cloudFavoriteForums.collectAsStateWithLifecycle()
                val cloudFavoriteForumKeys = remember(cloudForumFavorites.items) { cloudForumFavorites.items.map { it.key }.toSet() }
                val cloudForumBusyKeys by library.cloudForumBusyKeys.collectAsStateWithLifecycle()
                val cloudForumUncertainKeys by library.cloudForumUncertainKeys.collectAsStateWithLifecycle()
                val cloudForumNotice by library.cloudForumNotice.collectAsStateWithLifecycle()
                val appContext = LocalContext.current
                LaunchedEffect(library) {
                    library.cloudForumFeedback.collect { message ->
                        android.widget.Toast.makeText(appContext, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                val nav = rememberNavController()
                val expanded = width == WindowWidthSizeClass.Expanded
                val popBackFrom: (androidx.navigation.NavBackStackEntry) -> Unit = { source ->
                    if (nav.currentBackStackEntry === source && nav.previousBackStackEntry != null) {
                        nav.popBackStack()
                    }
                }
                val select: (String) -> Unit = { target ->
                    nav.navigate(target) {
                        launchSingleTop = true
                    }
                }
                val openTopic: (String) -> Unit = { topicId ->
                    val current = nav.currentBackStackEntry
                    nav.navigate("read/${Uri.encode(topicId)}") {
                        launchSingleTop = current?.destination?.route == "read/{id}" &&
                            current.arguments?.getString("id") == topicId
                    }
                }
                val topicLinks = rememberTopicLinkHandler(openTopic)
                val navigateToForum: (Forum) -> Unit = { forum ->
                    nav.navigate(
                        "topics/${Uri.encode(forum.id.ifBlank { "0" })}?collection=${Uri.encode(forum.collectionId.orEmpty())}&name=${Uri.encode(forum.name)}",
                    )
                }
                val showClipboardPrompt = clipboardLink != null && !account.busy && deepLinkUri == null
                val useBackdropBlur = hardwareBlurAllowed() && showClipboardPrompt
                val rootHazeState = rememberHazeState(blurEnabled = useBackdropBlur)
                SideEffect { rootHazeState.blurEnabled = useBackdropBlur }
                Box(Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (useBackdropBlur) Modifier.hazeSource(state = rootHazeState) else Modifier)
                            .then(if (account.busy || showClipboardPrompt) Modifier.clearAndSetSemantics { } else Modifier),
                        color = colors.groupedCanvas,
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                    ) {
                        CompositionLocalProvider(
                            LocalUriHandler provides topicLinks,
                            LocalOpenUser provides { uid ->
                                if (uid.matches(Regex("[1-9][0-9]*"))) select("user/${Uri.encode(uid)}/topics")
                            },
                            LocalOpenLogin provides { select("login") },
                        ) {
                            NavHost(
                                nav,
                                startDestination = "forums",
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                    .fillMaxSize(),
                            ) {
                                composable("forums") {
                                    ForumScreen(library, select, navigateToForum)
                                }
                                composable(
                                    route = "topics/{id}?collection={collection}&name={name}",
                                    arguments = listOf(
                                        navArgument("id") { type = NavType.StringType },
                                        navArgument("collection") { defaultValue = "" },
                                        navArgument("name") { defaultValue = "" },
                                    ),
                                ) { backStack ->
                                    TopicsScreen(
                                        forumId = backStack.arguments!!.getString("id")!!,
                                        collection = backStack.arguments!!.getString("collection")?.ifBlank { null },
                                        title = backStack.arguments!!.getString("name").orEmpty(),
                                        expanded = expanded,
                                        preferences = preferences,
                                        back = { popBackFrom(backStack) },
                                        onRead = openTopic,
                                        onSearch = {
                                            val args = backStack.arguments!!
                                            nav.navigate(
                                                "search?forumId=${Uri.encode(args.getString("id"))}&collectionId=${Uri.encode(args.getString("collection").orEmpty())}&forumName=${Uri.encode(args.getString("name").orEmpty())}",
                                            )
                                        },
                                        directoryForums = directory.items,
                                        favoriteForumKeys = favoriteForumKeys,
                                        onToggleForumFavorite = library::toggleForumFavorite,
                                        isSignedIn = library.isSignedIn,
                                        cloudFavoriteForumKeys = cloudFavoriteForumKeys,
                                        cloudFavoriteBusyKeys = cloudForumBusyKeys,
                                        cloudFavoriteUncertainKeys = cloudForumUncertainKeys,
                                        cloudFavoriteNotice = cloudForumNotice,
                                        onToggleCloudForumFavorite = library::toggleCloudForumFavorite,
                                        onRefreshCloudFavorites = library::loadCloudFavoriteForums,
                                        onSwitchForum = { targetForum ->
                                            backStack.arguments?.apply {
                                                putString("id", targetForum.id.ifBlank { "0" })
                                                putString("collection", targetForum.collectionId.orEmpty())
                                                putString("name", targetForum.name)
                                            }
                                        },
                                    )
                                }
                                composable(
                                    route = "search?forumId={forumId}&collectionId={collectionId}&forumName={forumName}",
                                    arguments = listOf(
                                        navArgument("forumId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                        navArgument("collectionId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                        navArgument("forumName") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    ),
                                ) { entry ->
                                    SearchScreen(
                                        library = library,
                                        onCancel = { popBackFrom(entry) },
                                        onForum = navigateToForum,
                                        onRead = openTopic,
                                        onUserContent = { uid, kind -> select("user/${Uri.encode(uid)}/$kind") },
                                    )
                                }
                                composable("favorites") { entry ->
                                    FavoritesScreen(
                                        vm = library,
                                        back = { popBackFrom(entry) },
                                        onSelect = select,
                                        onForum = navigateToForum,
                                        onRead = openTopic,
                                    )
                                }
                                composable("history") { entry ->
                                    HistoryScreen(library, back = { popBackFrom(entry) }, read = openTopic)
                                }
                                composable("settings") { entry ->
                                    SettingsScreen(library, preferences, back = { popBackFrom(entry) }, onSelect = select)
                                }
                                composable("accounts") { entry ->
                                    AccountsScreen(account, accountVm, back = { popBackFrom(entry) }, login = { select("login") })
                                }
                                composable("about") { entry ->
                                    AboutScreen(
                                        onCheckUpdate = updateVm::checkNow,
                                        onResetUpdateStatus = updateVm::resetManualStatus,
                                        back = { popBackFrom(entry) },
                                    )
                                }
                                composable("profile") { entry ->
                                    if (account.userId == null) {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)),
                                        ) {
                                            Notice("请先登录 NGA") { select("login") }
                                        }
                                    } else {
                                        OwnProfileScreen(
                                            identity = identity,
                                            back = { popBackFrom(entry) },
                                            retry = identityVm::retry,
                                            content = { kind -> select("user/${Uri.encode(account.userId)}/$kind") },
                                        )
                                    }
                                }
                                composable("user/{uid}/{kind}") { entry ->
                                    UserContentScreen(back = { popBackFrom(entry) }, read = openTopic,
                                        sendMessage = { name ->
                                            if (account.userId == null) select("login")
                                            else select("compose-message/${Uri.encode(name)}")
                                        })
                                }
                                composable("login") { entry -> LoginScreen(back = { popBackFrom(entry) }) }
                                composable("notifications") { entry ->
                                    NotificationsScreen(notifications, { popBackFrom(entry) }, notificationsVm::refresh,
                                        { select("login") }, { item ->
                                            notificationsVm.markRead(item)
                                            if (item.topicId.matches(Regex("[1-9][0-9]*"))) openTopic(item.topicId)
                                            else select("messages")
                                        }, markAllRead = notificationsVm::markAllRead)
                                }
                                composable("messages") { entry ->
                                    MessagesScreen(null, preferences, { popBackFrom(entry) }, { nav.navigate("message/${Uri.encode(it)}") })
                                }
                                composable("compose-message/{recipient}") { entry ->
                                    NewMessageScreen(checkNotNull(entry.arguments!!.getString("recipient")), preferences, { popBackFrom(entry) })
                                }
                                composable("message/{id}") { entry ->
                                    MessagesScreen(entry.arguments!!.getString("id"), preferences, { popBackFrom(entry) }, {})
                                }
                                composable("read/{id}") { backStack ->
                                    val topicId = backStack.arguments!!.getString("id")!!
                                    val pendingPost by backStack.savedStateHandle.getStateFlow<String?>("postReference", null).collectAsStateWithLifecycle()
                                    val pendingPage by backStack.savedStateHandle.getStateFlow<Int?>("initialPage", null).collectAsStateWithLifecycle()
                                    val reference = pendingPost?.split(',')?.let {
                                        dev.nagi.sdk.PostReference(topicId, it[0], it[1].toInt())
                                    }
                                    ReaderScreen(
                                        topicId, preferences, back = { popBackFrom(backStack) },
                                        initialReference = reference,
                                        referenceConsumed = { backStack.savedStateHandle["postReference"] = null },
                                        initialPage = pendingPage,
                                        initialPageConsumed = { backStack.savedStateHandle["initialPage"] = null },
                                        onOpenReference = { target ->
                                            openTopic(target.topicId)
                                            nav.currentBackStackEntry?.savedStateHandle?.set("postReference", "${target.postId},${target.page}")
                                        },
                                    )
                                }
                            }
                        }
                    }
                    val navigateToExternalTarget: (NgaExternalTarget) -> Unit = { target ->
                        when (target) {
                            is NgaExternalTarget.Topic -> {
                                openTopic(target.topicId)
                                if (target.postId != null) {
                                    nav.currentBackStackEntry?.savedStateHandle?.set("postReference", "${target.postId},${target.page}")
                                } else if (target.page > 1) {
                                    nav.currentBackStackEntry?.savedStateHandle?.set("initialPage", target.page)
                                }
                            }
                            is NgaExternalTarget.Forum -> {
                                val matched = directory.items.firstOrNull {
                                    it.id == target.forumId && it.collectionId.orEmpty() == target.collectionId.orEmpty()
                                }
                                val fallbackName = if (target.collectionId != null) "合集 ${target.collectionId}" else "版块 ${target.forumId}"
                                navigateToForum(
                                    matched ?: Forum(
                                        id = target.forumId,
                                        name = fallbackName,
                                        category = "",
                                        group = "",
                                        collectionId = target.collectionId,
                                    ),
                                )
                            }
                            is NgaExternalTarget.User -> select("user/${Uri.encode(target.userId)}/topics")
                        }
                    }
                    LaunchedEffect(deepLinkUri, account.busy) {
                        val raw = deepLinkUri ?: return@LaunchedEffect
                        if (account.busy) return@LaunchedEffect
                        NgaExternalLinks.parse(raw)?.let { target ->
                            ClipboardLinkTracker.markHandled(raw)
                            navigateToExternalTarget(target)
                        }
                        onDeepLinkConsumed()
                    }
                    if (showClipboardPrompt) {
                        val activeClipboardLink = clipboardLink!!
                        BackHandler { onClipboardLinkHandled(activeClipboardLink) }
                        CompositionLocalProvider(
                            LocalNagiHaze provides rootHazeState,
                            LocalNagiBlurEnabled provides useBackdropBlur,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = if (colors.isDark) 0.50f else 0.30f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onClipboardLinkHandled(activeClipboardLink) },
                                    )
                                    .safeDrawingPadding()
                                    .padding(horizontal = Space.xl),
                                contentAlignment = Alignment.Center,
                            ) {
                                ClipboardLinkPromptDialog(
                                    link = activeClipboardLink,
                                    onOpen = {
                                        onClipboardLinkHandled(activeClipboardLink)
                                        navigateToExternalTarget(activeClipboardLink.target)
                                    },
                                    onIgnore = { onClipboardLinkHandled(activeClipboardLink) },
                                )
                            }
                        }
                    }
                    if (account.busy) {
                        Surface(
                            onClick = {},
                            modifier = Modifier.fillMaxSize(),
                            color = colors.groupedCanvas,
                            shadowElevation = 0.dp,
                            tonalElevation = 0.dp,
                        ) {
                            Box(Modifier.safeDrawingPadding()) { Notice("正在处理账号…") }
                        }
                    }
                }
            }
        }
    }
}
