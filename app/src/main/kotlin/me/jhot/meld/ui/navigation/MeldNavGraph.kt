package me.jhot.meld.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.jhot.meld.ui.globalSettings.GlobalSettingsScreen
import me.jhot.meld.ui.modeEditor.ModeEditorScreen
import me.jhot.meld.ui.modeList.ModeListScreen

@Composable
fun MeldNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "modeList") {
        composable("modeList") {
            ModeListScreen(navController = navController)
        }
        composable(
            route = "modeEditor?modeId={modeId}",
            arguments = listOf(navArgument("modeId") {
                type = NavType.LongType
                defaultValue = -1L
            }),
        ) { backStackEntry ->
            val modeId = backStackEntry.arguments?.getLong("modeId") ?: -1L
            ModeEditorScreen(
                modeId = if (modeId == -1L) null else modeId,
                navController = navController,
            )
        }
        composable("globalSettings") {
            GlobalSettingsScreen(navController = navController)
        }
    }
}
