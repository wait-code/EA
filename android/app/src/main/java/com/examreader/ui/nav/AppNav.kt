package com.examreader.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.examreader.ui.CatalogScreen
import com.examreader.ui.CollectionScreen
import com.examreader.ui.CreditsScreen
import com.examreader.ui.EditorScreen
import com.examreader.ui.GlossaryScreen
import com.examreader.ui.LibraryScreen
import com.examreader.ui.QuizScreen
import com.examreader.ui.ScanBanner
import com.examreader.ui.ScanScreen
import com.examreader.ui.SectionScreen
import com.examreader.ui.SentencesScreen
import com.examreader.ui.SettingsScreen
import com.examreader.ui.WordbookScreen

object Routes {
    const val LIBRARY = "library"
    const val PAPER = "paper/{paperId}"
    const val SECTION = "section/{paperId}/{sectionId}"
    const val QUIZ = "quiz/{paperId}"
    const val WORDBOOK = "wordbook/{paperId}"
    const val SENTENCES = "sentences/{paperId}"
    const val SETTINGS = "settings/{paperId}"
    const val SCAN = "scan"
    const val EDITOR = "editor/{paperId}"
    const val COLLECTION = "collection"
    const val GLOSSARY = "glossary"
    const val CREDITS = "credits"

    fun paper(paperId: String) = "paper/$paperId"
    fun section(paperId: String, sectionId: String) = "section/$paperId/$sectionId"
    fun quiz(paperId: String) = "quiz/$paperId"
    fun wordbook(paperId: String) = "wordbook/$paperId"
    fun sentences(paperId: String) = "sentences/$paperId"
    fun settings(paperId: String) = "settings/$paperId"
    fun scan() = SCAN
    fun editor(paperId: String) = "editor/$paperId"
    fun collection() = COLLECTION
    fun glossary() = GLOSSARY
    fun credits() = CREDITS
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = Routes.LIBRARY) {
            composable(Routes.LIBRARY) {
                LibraryScreen(nav)
            }
            composable(Routes.COLLECTION) {
                CollectionScreen(nav)
            }
            composable(Routes.GLOSSARY) {
                GlossaryScreen(nav)
            }
            composable(Routes.CREDITS) {
                CreditsScreen(nav)
            }
            composable(
                route = Routes.PAPER,
                arguments = listOf(navArgument("paperId") { type = NavType.StringType })
            ) { entry ->
                CatalogScreen(nav, entry.arguments?.getString("paperId") ?: "")
            }
            composable(
                route = Routes.SECTION,
                arguments = listOf(
                    navArgument("paperId") { type = NavType.StringType },
                    navArgument("sectionId") { type = NavType.StringType }
                )
            ) { entry ->
                SectionScreen(
                    nav = nav,
                    paperId = entry.arguments?.getString("paperId") ?: "",
                    sectionId = entry.arguments?.getString("sectionId") ?: ""
                )
            }
            composable(
                route = Routes.QUIZ,
                arguments = listOf(navArgument("paperId") { type = NavType.StringType })
            ) { entry ->
                QuizScreen(nav, entry.arguments?.getString("paperId") ?: "")
            }
            composable(
                route = Routes.WORDBOOK,
                arguments = listOf(navArgument("paperId") { type = NavType.StringType })
            ) { entry ->
                WordbookScreen(nav, entry.arguments?.getString("paperId") ?: "")
            }
            composable(
                route = Routes.SENTENCES,
                arguments = listOf(navArgument("paperId") { type = NavType.StringType })
            ) { entry ->
                SentencesScreen(nav, entry.arguments?.getString("paperId") ?: "")
            }
            composable(
                route = Routes.SETTINGS,
                arguments = listOf(navArgument("paperId") { type = NavType.StringType })
            ) { entry ->
                SettingsScreen(nav, entry.arguments?.getString("paperId") ?: "")
            }
            composable(Routes.SCAN) {
                ScanScreen(nav)
            }
            composable(
                route = Routes.EDITOR,
                arguments = listOf(navArgument("paperId") { type = NavType.StringType })
            ) { entry ->
                EditorScreen(nav, entry.arguments?.getString("paperId") ?: "")
            }
        }

        // 后台扫描悬浮胶囊：可拖动，浮在所有页面之上
        ScanBanner(nav = nav)
    }
}
