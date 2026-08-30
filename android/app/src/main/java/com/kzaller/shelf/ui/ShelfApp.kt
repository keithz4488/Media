package com.kzaller.shelf.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.data.preferences.AppPreferences
import com.kzaller.shelf.ui.screens.AuthState
import com.kzaller.shelf.ui.screens.AuthViewModel
import com.kzaller.shelf.ui.screens.SignInScreen
import kotlinx.coroutines.flow.first
import com.kzaller.shelf.ui.components.AchievementUnlockBanner
import com.kzaller.shelf.ui.screens.AccountScreen
import com.kzaller.shelf.ui.screens.AchievementsScreen
import com.kzaller.shelf.ui.screens.ShelfScanScreen
import com.kzaller.shelf.ui.screens.ShelfScanViewModel
import com.kzaller.shelf.ui.screens.AchievementsViewModel
import com.kzaller.shelf.ui.screens.AddItemScreen
import com.kzaller.shelf.ui.screens.AddItemViewModel
import com.kzaller.shelf.ui.screens.DetailScreen
import com.kzaller.shelf.ui.screens.HomeScreen
import com.kzaller.shelf.ui.screens.ImportScreen
import com.kzaller.shelf.ui.screens.ImportViewModel
import com.kzaller.shelf.ui.screens.SearchAllScreen
import com.kzaller.shelf.ui.screens.SearchAllViewModel
import com.kzaller.shelf.ui.screens.ShelfScreen
import com.kzaller.shelf.ui.screens.ShelfViewModel
import com.kzaller.shelf.ui.screens.StatsScreen
import com.kzaller.shelf.ui.screens.SteamImportScreen
import com.kzaller.shelf.ui.screens.SteamImportViewModel
import com.kzaller.shelf.ui.screens.StatsViewModel
import com.kzaller.shelf.ui.theme.MediaShelfTheme

object Routes {
    const val HOME = "home"
    const val SHELF = "shelf/{kind}"
    fun shelf(k: MediaKind) = "shelf/${k.wire}"
    const val ADD = "add/{kind}?start={start}"
    fun add(k: MediaKind, start: String = "choose") = "add/${k.wire}?start=$start"
    const val DETAIL = "item/{kind}/{id}"
    fun detail(k: MediaKind, id: String) = "item/${k.wire}/$id"
    const val SEARCH_ALL = "search"
    const val STATS = "stats"
    const val ACHIEVEMENTS = "achievements"
    const val IMPORT = "import"
    const val STEAM_IMPORT = "import/steam"
    const val SHELF_SCAN = "scan/shelf"
    const val ACCOUNT = "account"
}

/** Guard against navigations issued during a back-transition: when the user has just
 *  tapped the back arrow, the previous screen is still visible (and clickable) for
 *  the duration of the animation. Any click on it should be a no-op, not a real navigation. */
private fun NavController.navigateIfResumed(from: NavBackStackEntry?, route: String) {
    if (from?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
        navigate(route)
    }
}

@Composable
private fun AuthSplash() {
    androidx.compose.material3.MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xFF160D06)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.CircularProgressIndicator(color = androidx.compose.ui.graphics.Color(0xFFE5C07B))
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ShelfApp() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }

    // Gate the whole app behind Google sign-in: the shelf is per-account now.
    val authVm: AuthViewModel = viewModel(factory = AuthViewModel.factory(context, prefs))
    val authState by authVm.state.collectAsState()
    if (authState !is AuthState.SignedIn) {
        if (authState is AuthState.Loading) AuthSplash() else SignInScreen(authVm)
        return
    }

    val repo = remember { ShelfRepository(context) }
    val nav = rememberNavController()

    // One app-wide achievements watcher so unlock banners can appear on ANY screen, not just home.
    val achievementsVm: AchievementsViewModel =
        viewModel(factory = AchievementsViewModel.factory(repo, prefs))
    val unlockQueue by achievementsVm.queue.collectAsState()
    val currentUnlock = unlockQueue.firstOrNull()
    LaunchedEffect(currentUnlock) {
        if (currentUnlock != null) {
            kotlinx.coroutines.delay(3600)
            achievementsVm.consume()
        }
    }

    // Build every shelf's items now, while the user is still on the home screen, so tapping a
    // shelf only has to lay covers out rather than query and convert a couple of thousand rows.
    // The sort, view mode and column count come from DataStore, which is equally asynchronous
    // and equally worth having settled before a shelf tries to lay itself out.
    LaunchedEffect(Unit) {
        repo.warmShelves()
        prefs.warmDisplayPrefs()
    }

    // If Steam is already connected, make sure the backend has the credentials so its daily
    // auto-sync cron can add newly-purchased games (the app otherwise only sends them on connect).
    LaunchedEffect(Unit) {
        val key = prefs.observeSteamKey().first()
        val id = prefs.observeSteamId().first()
        if (key.isNotBlank() && id.isNotBlank()) {
            repo.saveSteamConfig(key, id)
        }
    }

    // Tell the backend which Plex account is this user's own, read from their own server. The
    // webhook fires for everyone with access to the library and the account name is the only
    // thing that separates them, so without this other people's viewing lands on this shelf.
    // Done here rather than only on connect so an existing setup fixes itself on next launch.
    LaunchedEffect(Unit) {
        val plexUrl = prefs.observePlexUrl().first()
        val plexToken = prefs.observePlexToken().first()
        repo.registerPlexAccount(plexUrl, plexToken)
    }

    MediaShelfTheme {
        Box(modifier = Modifier.fillMaxSize()) {
        SharedTransitionLayout {
        val flyingCover = remember { mutableStateOf<String?>(null) }
        val shelfOrder = remember { mutableStateOf<List<String>>(emptyList()) }
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalFlyingCoverId provides flyingCover,
            LocalShelfOrder provides shelfOrder,
        ) {
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) { entry ->
                HomeScreen(
                    onShelfTap = { nav.navigateIfResumed(entry, Routes.shelf(it)) },
                    onSearchAll = { nav.navigateIfResumed(entry, Routes.SEARCH_ALL) },
                    onStats = { nav.navigateIfResumed(entry, Routes.STATS) },
                    onAchievements = { nav.navigateIfResumed(entry, Routes.ACHIEVEMENTS) },
                    onImport = { nav.navigateIfResumed(entry, Routes.IMPORT) },
                    onImportSteam = { nav.navigateIfResumed(entry, Routes.STEAM_IMPORT) },
                    onScanShelf = { nav.navigateIfResumed(entry, Routes.SHELF_SCAN) },
                    onAccount = { nav.navigateIfResumed(entry, Routes.ACCOUNT) },
                    onOpenItem = { k, id -> nav.navigateIfResumed(entry, Routes.detail(k, id)) },
                )
            }
            composable(Routes.SHELF_SCAN) {
                val vm: ShelfScanViewModel = viewModel(factory = ShelfScanViewModel.factory(repo))
                ShelfScanScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onDone = { nav.popBackStack() },
                )
            }
            composable(Routes.ACCOUNT) {
                AccountScreen(
                    email = (authState as? AuthState.SignedIn)?.email ?: "",
                    onSignOut = { authVm.signOut() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.IMPORT) {
                val vm: ImportViewModel = viewModel(factory = ImportViewModel.factory(repo, prefs))
                ImportScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onDone = { nav.popBackStack() },
                )
            }
            composable(Routes.STEAM_IMPORT) {
                val vm: SteamImportViewModel = viewModel(factory = SteamImportViewModel.factory(repo, prefs))
                SteamImportScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onDone = { nav.popBackStack() },
                )
            }
            composable(Routes.ACHIEVEMENTS) {
                val vm: AchievementsViewModel =
                    viewModel(factory = AchievementsViewModel.factory(repo, prefs))
                AchievementsScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.STATS) { entry ->
                val vm: StatsViewModel = viewModel(factory = StatsViewModel.factory(repo))
                StatsScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onShelfTap = { nav.navigateIfResumed(entry, Routes.shelf(it)) },
                )
            }
            composable(Routes.SEARCH_ALL) { entry ->
                val vm: SearchAllViewModel = viewModel(factory = SearchAllViewModel.factory(repo))
                SearchAllScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onItem = { k, id -> nav.navigateIfResumed(entry, Routes.detail(k, id)) },
                )
            }
            composable(
                route = Routes.SHELF,
                arguments = listOf(navArgument("kind") { type = NavType.StringType }),
            ) { entry ->
                val kind = MediaKind.fromWire(entry.arguments?.getString("kind")!!)
                val vm: ShelfViewModel = viewModel(factory = ShelfViewModel.factory(repo, prefs, kind))
                val animScope = this
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides animScope) {
                // Once the shelf has settled back into view the trip is over. Without this the
                // id lingers, and the same cover would turn again on the way out to anywhere else.
                LaunchedEffect(animScope.transition.currentState, animScope.transition.isRunning) {
                    if (animScope.transition.currentState == EnterExitState.Visible &&
                        !animScope.transition.isRunning
                    ) {
                        flyingCover.value = null
                    }
                }
                ShelfScreen(
                    kind = kind,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onAdd = { start -> nav.navigateIfResumed(entry, Routes.add(kind, start)) },
                    onItem = { id ->
                        flyingCover.value = id
                        nav.navigateIfResumed(entry, Routes.detail(kind, id))
                    },
                    onSwitchShelf = { target ->
                        // Swap shelves without stacking: pop back to home, then open the target.
                        if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            nav.popBackStack(Routes.HOME, inclusive = false)
                            nav.navigate(Routes.shelf(target))
                        }
                    },
                )
                }
            }
            composable(
                route = Routes.ADD,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("start") { type = NavType.StringType; defaultValue = "choose" },
                ),
            ) { entry ->
                val kind = MediaKind.fromWire(entry.arguments?.getString("kind")!!)
                val start = entry.arguments?.getString("start") ?: "choose"
                val vm: AddItemViewModel = viewModel(factory = AddItemViewModel.factory(repo, kind))
                AddItemScreen(
                    kind = kind,
                    vm = vm,
                    startMode = start,
                    onClose = { nav.popBackStack() },
                    onAdded = { nav.popBackStack() },
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType },
                ),
                // The page fades in around the cover while the cover itself flies into place;
                // the flight is the motion, so nothing else should move.
                enterTransition = { fadeIn(tween(COVER_FLIGHT_MS)) },
                popExitTransition = { fadeOut(tween(COVER_FLIGHT_MS)) },
            ) { entry ->
                val kind = MediaKind.fromWire(entry.arguments?.getString("kind")!!)
                val id = entry.arguments?.getString("id")!!
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                    DetailScreen(
                        initialId = id,
                        kind = kind,
                        repo = repo,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
        }
        }
        // Global unlock banner overlays every screen.
        AchievementUnlockBanner(
            achievement = currentUnlock,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        }
    }
}
