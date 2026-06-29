package com.kzaller.shelf.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.kzaller.shelf.ui.screens.AddItemScreen
import com.kzaller.shelf.ui.screens.AddItemViewModel
import com.kzaller.shelf.ui.screens.DetailScreen
import com.kzaller.shelf.ui.screens.HomeScreen
import com.kzaller.shelf.ui.screens.SearchAllScreen
import com.kzaller.shelf.ui.screens.SearchAllViewModel
import com.kzaller.shelf.ui.screens.ShelfScreen
import com.kzaller.shelf.ui.screens.ShelfViewModel
import com.kzaller.shelf.ui.screens.StatsScreen
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
fun ShelfApp() {
    val context = LocalContext.current
    val repo = remember { ShelfRepository(context) }
    val prefs = remember { AppPreferences(context) }
    val nav = rememberNavController()

    MediaShelfTheme {
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) { entry ->
                HomeScreen(
                    onShelfTap = { nav.navigateIfResumed(entry, Routes.shelf(it)) },
                    onSearchAll = { nav.navigateIfResumed(entry, Routes.SEARCH_ALL) },
                    onStats = { nav.navigateIfResumed(entry, Routes.STATS) },
                )
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
                ShelfScreen(
                    kind = kind,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onAdd = { start -> nav.navigateIfResumed(entry, Routes.add(kind, start)) },
                    onItem = { nav.navigateIfResumed(entry, Routes.detail(kind, it)) },
                    onSwitchShelf = { target ->
                        // Swap shelves without stacking: pop back to home, then open the target.
                        if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            nav.popBackStack(Routes.HOME, inclusive = false)
                            nav.navigate(Routes.shelf(target))
                        }
                    },
                )
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
            ) { entry ->
                val kind = MediaKind.fromWire(entry.arguments?.getString("kind")!!)
                val id = entry.arguments?.getString("id")!!
                DetailScreen(
                    initialId = id,
                    kind = kind,
                    repo = repo,
                    prefs = prefs,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
