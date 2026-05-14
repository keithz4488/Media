package com.kzaller.shelf.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kzaller.shelf.data.MediaKind
import com.kzaller.shelf.data.ShelfRepository
import com.kzaller.shelf.ui.screens.AddItemScreen
import com.kzaller.shelf.ui.screens.DetailScreen
import com.kzaller.shelf.ui.screens.HomeScreen
import com.kzaller.shelf.ui.screens.ShelfScreen
import com.kzaller.shelf.ui.screens.AddItemViewModel
import com.kzaller.shelf.ui.screens.DetailViewModel
import com.kzaller.shelf.ui.screens.ShelfViewModel
import com.kzaller.shelf.ui.theme.MediaShelfTheme

object Routes {
    const val HOME = "home"
    const val SHELF = "shelf/{kind}"
    fun shelf(k: MediaKind) = "shelf/${k.wire}"
    const val ADD = "add/{kind}"
    fun add(k: MediaKind) = "add/${k.wire}"
    const val DETAIL = "item/{id}"
    fun detail(id: String) = "item/$id"
}

@Composable
fun ShelfApp() {
    val context = LocalContext.current
    val repo = remember { ShelfRepository(context) }
    val nav = rememberNavController()

    MediaShelfTheme {
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(onShelfTap = { nav.navigate(Routes.shelf(it)) })
            }
            composable(
                route = Routes.SHELF,
                arguments = listOf(navArgument("kind") { type = NavType.StringType }),
            ) { backStack ->
                val kind = MediaKind.fromWire(backStack.arguments?.getString("kind")!!)
                val vm: ShelfViewModel = viewModel(factory = ShelfViewModel.factory(repo, kind))
                ShelfScreen(
                    kind = kind,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onAdd = { nav.navigate(Routes.add(kind)) },
                    onItem = { nav.navigate(Routes.detail(it)) },
                )
            }
            composable(
                route = Routes.ADD,
                arguments = listOf(navArgument("kind") { type = NavType.StringType }),
            ) { backStack ->
                val kind = MediaKind.fromWire(backStack.arguments?.getString("kind")!!)
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
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { backStack ->
                val id = backStack.arguments?.getString("id")!!
                val vm: DetailViewModel = viewModel(factory = DetailViewModel.factory(repo, id))
                DetailScreen(vm = vm, onBack = { nav.popBackStack() })
            }
        }
    }
}
