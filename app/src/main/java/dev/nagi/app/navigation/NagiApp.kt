package dev.nagi.app.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.nagi.app.viewmodel.NotificationsViewModel
import dev.nagi.app.viewmodel.NotificationsState
import dev.nagi.app.ui.components.LocalOpenUser
import dev.nagi.app.ui.components.LocalNotifications
import dev.nagi.app.ui.components.LocalUserTags
import dev.nagi.app.ui.components.UserTagAccess
import dev.nagi.app.viewmodel.UserTagsState
import dev.nagi.app.viewmodel.UserTagsViewModel
import dev.nagi.app.ui.library.NotificationsScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.LocalAccountIdentity
import dev.nagi.app.ui.components.Notice
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
import dev.nagi.app.ui.topic.TopicsScreen
import dev.nagi.app.viewmodel.AccountIdentity
import dev.nagi.app.viewmodel.AccountIdentityViewModel
import dev.nagi.app.viewmodel.AccountViewModel
import dev.nagi.app.viewmodel.LibraryViewModel
import dev.nagi.sdk.Forum

@Composable
fun NagiApp(
    width: WindowWidthSizeClass,
    accountVm: AccountViewModel = hiltViewModel(),
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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, notificationsVm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) notificationsVm.setForeground(true)
            if (event == Lifecycle.Event.ON_STOP) notificationsVm.setForeground(false)
        }
        lifecycle.addObserver(observer)
        notificationsVm.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycle.removeObserver(observer); notificationsVm.setForeground(false) }
    }
    CompositionLocalProvider(LocalAccountIdentity provides identity, LocalNotifications provides notifications,
        LocalUserTags provides tagAccess) {
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
                val nav = rememberNavController()
                val expanded = width == WindowWidthSizeClass.Expanded
                val topicLinks = rememberTopicLinkHandler { nav.navigate("read/${Uri.encode(it)}") }
                val returnFromAccountPage: (androidx.navigation.NavBackStackEntry) -> Unit = { source ->
                    if (nav.currentBackStackEntry === source && nav.previousBackStackEntry != null) {
                        nav.popBackStack()
                    }
                }
                val select: (String) -> Unit = { target ->
                    nav.navigate(target) {
                        launchSingleTop = true
                    }
                }
                val navigateToForum: (Forum) -> Unit = { forum ->
                    nav.navigate(
                        "topics/${Uri.encode(forum.id.ifBlank { "0" })}?collection=${Uri.encode(forum.collectionId.orEmpty())}&name=${Uri.encode(forum.name)}",
                    )
                }
                Box(Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (account.busy) Modifier.clearAndSetSemantics { } else Modifier),
                        color = colors.groupedCanvas,
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                    ) {
                        CompositionLocalProvider(LocalUriHandler provides topicLinks, LocalOpenUser provides { uid ->
                            if (uid.matches(Regex("[1-9][0-9]*"))) select("user/${Uri.encode(uid)}/topics")
                        }) {
                            NavHost(nav, startDestination = "forums", modifier = Modifier.safeDrawingPadding().fillMaxSize()) {
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
                                        back = { nav.popBackStack() },
                                        onRead = { nav.navigate("read/${Uri.encode(it)}") },
                                        onSearch = {
                                            val args = backStack.arguments!!
                                            nav.navigate(
                                                "search?forumId=${Uri.encode(args.getString("id"))}&collectionId=${Uri.encode(args.getString("collection").orEmpty())}&forumName=${Uri.encode(args.getString("name").orEmpty())}",
                                            )
                                        },
                                        directoryForums = directory.items,
                                        favoriteForumKeys = favoriteForumKeys,
                                        onToggleForumFavorite = library::toggleForumFavorite,
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
                                ) {
                                    SearchScreen(
                                        library = library,
                                        onCancel = { nav.popBackStack() },
                                        onForum = navigateToForum,
                                        onRead = { nav.navigate("read/${Uri.encode(it)}") },
                                        onUserContent = { uid, kind -> select("user/${Uri.encode(uid)}/$kind") },
                                    )
                                }
                                composable("favorites") {
                                    FavoritesScreen(
                                        vm = library,
                                        back = { nav.popBackStack() },
                                        onSelect = select,
                                        onForum = navigateToForum,
                                        onRead = { nav.navigate("read/${Uri.encode(it)}") },
                                    )
                                }
                                composable("history") {
                                    HistoryScreen(library, back = { nav.popBackStack() }, read = { select("read/${Uri.encode(it)}") })
                                }
                                composable("settings") {
                                    SettingsScreen(library, preferences, back = { nav.popBackStack() }, onSelect = select)
                                }
                                composable("accounts") {
                                    AccountsScreen(account, accountVm, back = { nav.popBackStack() }, login = { select("login") })
                                }
                                composable("about") { AboutScreen(back = { nav.popBackStack() }) }
                                composable("profile") {
                                    if (account.userId == null) {
                                        Notice("请先登录 NGA") { select("login") }
                                    } else {
                                        OwnProfileScreen(
                                            identity = identity,
                                            back = { nav.popBackStack() },
                                            retry = identityVm::retry,
                                            content = { kind -> select("user/${Uri.encode(account.userId)}/$kind") },
                                        )
                                    }
                                }
                                composable("user/{uid}/{kind}") {
                                    UserContentScreen(back = { nav.popBackStack() }, read = { select("read/${Uri.encode(it)}") },
                                        sendMessage = { name ->
                                            if (account.userId == null) select("login")
                                            else select("compose-message/${Uri.encode(name)}")
                                        })
                                }
                                composable("login") { LoginScreen(back = { nav.popBackStack() }) }
                                composable("notifications") { entry ->
                                    NotificationsScreen(notifications, { returnFromAccountPage(entry) }, notificationsVm::refresh,
                                        { select("login") }, { item ->
                                            notificationsVm.markRead(item)
                                            if (item.topicId.matches(Regex("[1-9][0-9]*"))) select("read/${Uri.encode(item.topicId)}")
                                            else select("messages")
                                        }, markAllRead = notificationsVm::markAllRead)
                                }
                                composable("messages") { entry ->
                                    MessagesScreen(null, preferences, { returnFromAccountPage(entry) }, { nav.navigate("message/${Uri.encode(it)}") })
                                }
                                composable("compose-message/{recipient}") { entry ->
                                    NewMessageScreen(checkNotNull(entry.arguments!!.getString("recipient")), preferences, { nav.popBackStack() })
                                }
                                composable("message/{id}") { entry ->
                                    MessagesScreen(entry.arguments!!.getString("id"), preferences, { returnFromAccountPage(entry) }, {})
                                }
                                composable("read/{id}") { backStack ->
                                    ReaderScreen(backStack.arguments!!.getString("id")!!, preferences, back = { nav.popBackStack() })
                                }
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
