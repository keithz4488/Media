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
import com.kzaller.shelf.ui.screens.ShelfScreen
import com.kzaller.shelf.ui.screens.ShelfViewModel
import com.kzaller.shelf.ui.theme.MediaShelfTheme

object Routes {
    const val HOME = "home"
    const val SHELF = "shelf/{kind}"
    fun shelf(k: MediaKind) = "shelf/${k.wire}"
    const val ADD = "add/{kind}"
    fun add(k: MediaKind) = "add/${k.wire}"
    const val DETAIL = "item/{kind}/{id}"
    fun detail(k: MediaKind, id: String) = "item/${k.wire}/$id"
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
                HomeScreen(onShelfTap = { nav.navigateIfResumed(entry, Routes.shelf(it)) })
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
                    onAdd = { nav.navigateIfResumed(entry, Routes.add(kind)) },
                    onItem = { nav.navigateIfResumed(entry, Routes.detail(kind, it)) },
                )
            }
            composable(
                route = Routes.ADD,
                arguments = listOf(navArgument("kind") { type = NavType.StringType }),
            ) { entry ->
                val kind = MediaKind.fromWire(entry.arguments?.getString("kind")!!)
                val vm: AddItemViewModel = viewModel(factory = AddItemViewModel.factory(repo, kind))
                AddItemScreen(
                    kind = kind,
                    vm = vm,
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
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
