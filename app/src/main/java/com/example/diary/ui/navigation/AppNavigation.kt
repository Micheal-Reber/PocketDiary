package com.example.diary.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diary.data.backup.BackupRepository
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.preferences.ThemePreferences
import com.example.diary.data.repository.CountdownRepository
import com.example.diary.data.repository.DiaryRepository
import com.example.diary.data.repository.HabitRepository
import com.example.diary.ui.countdown.CountdownDetailScreen
import com.example.diary.ui.countdown.CountdownEditScreen
import com.example.diary.ui.countdown.CountdownListScreen
import com.example.diary.ui.diary.DiaryListScreen
import com.example.diary.ui.editor.DiaryEditorScreen
import com.example.diary.ui.habits.HabitsScreen
import com.example.diary.ui.habits.HabitsViewModel
import com.example.diary.ui.habits.HabitsViewModelFactory
import com.example.diary.ui.habits.StatisticsScreen
import com.example.diary.ui.settings.SettingsScreen

sealed class Screen(val route: String, val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    data object Diary : Screen("diary", "日记", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook)
    data object Calendar : Screen("calendar", "日历", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth)
    data object Countdown : Screen("countdown", "倒数日", Icons.Filled.HourglassTop, Icons.Outlined.HourglassTop)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
}

val bottomNavItems = listOf(Screen.Diary, Screen.Calendar, Screen.Countdown, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    themePreferences: ThemePreferences,
    database: AppDatabase
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val diaryRepository = remember { DiaryRepository(database.diaryDao()) }
    val habitRepository = remember { HabitRepository(database.habitDao()) }
    val countdownRepository = remember { CountdownRepository(database.countdownDao()) }
    val backupRepository = remember { BackupRepository(
        context = context,
        diaryRepository = diaryRepository,
        habitRepository = habitRepository,
        countdownRepository = countdownRepository,
        themePreferences = themePreferences,
        database = database,
    ) }
    // Shared between the calendar tab and the statistics screen so both see the
    // same stats state (selected year/month, loaded chart data) without refetch.
    val habitsViewModel: HabitsViewModel = viewModel(factory = HabitsViewModelFactory(habitRepository))
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        // 里层各 Tab 的 Scaffold/TopAppBar 已各自消化 statusBars，
        // 外层不再重复垫状态栏，只留底栏高度，避免双重 top inset 导致顶部黑空隙
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // Subtle fade-through between tabs; the editor slides up gently.
        NavHost(
            navController = navController,
            startDestination = Screen.Diary.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(150)) }
        ) {
            composable(Screen.Diary.route) {
                DiaryListScreen(
                    diaryRepository = diaryRepository,
                    themePreferences = themePreferences,
                    onWriteDiary = { date ->
                        val route = if (date != null) "editor?date=$date" else "editor"
                        navController.navigate(route)
                    },
                    onEditDiary = { date -> navController.navigate("editor?date=$date") }
                )
            }
            composable(Screen.Calendar.route) {
                HabitsScreen(
                    habitRepository = habitRepository,
                    onOpenStatistics = { navController.navigate("statistics") },
                    viewModel = habitsViewModel
                )
            }
            composable("statistics") {
                StatisticsScreen(
                    habitsViewModel = habitsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Countdown.route) {
                CountdownListScreen(
                    repository = countdownRepository,
                    onOpenDetail = { id -> navController.navigate("countdown_detail/$id") },
                    onCreate = { navController.navigate("countdown_edit") }
                )
            }
            composable(
                route = "countdown_edit?id={id}",
                arguments = listOf(navArgument("id") {
                    type = NavType.LongType; defaultValue = 0L
                }),
                enterTransition = { slideInVertically(tween(220)) { it / 6 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutVertically(tween(200)) { it / 6 } + fadeOut(tween(180)) }
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: 0L
                CountdownEditScreen(
                    existingId = id.takeIf { it > 0L },
                    repository = countdownRepository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "countdown_edit",
                enterTransition = { slideInVertically(tween(220)) { it / 6 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutVertically(tween(200)) { it / 6 } + fadeOut(tween(180)) }
            ) {
                CountdownEditScreen(
                    existingId = null,
                    repository = countdownRepository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "countdown_detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                enterTransition = { slideInVertically(tween(220)) { it / 6 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutVertically(tween(200)) { it / 6 } + fadeOut(tween(180)) }
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: 0L
                CountdownDetailScreen(
                    eventId = id,
                    repository = countdownRepository,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("countdown_edit?id=$id") },
                    onCreate = { navController.navigate("countdown_edit") }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    themePreferences = themePreferences,
                    backupRepository = backupRepository,
                )
            }
            composable(
                route = "editor?date={date}",
                arguments = listOf(navArgument("date") { type = NavType.StringType; defaultValue = "" }),
                enterTransition = { slideInVertically(tween(220)) { it / 6 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutVertically(tween(200)) { it / 6 } + fadeOut(tween(180)) }
            ) { backStackEntry ->
                val dateStr = backStackEntry.arguments?.getString("date")?.ifEmpty { null }
                DiaryEditorScreen(
                    initialDate = dateStr,
                    diaryRepository = diaryRepository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "editor",
                enterTransition = { slideInVertically(tween(220)) { it / 6 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutVertically(tween(200)) { it / 6 } + fadeOut(tween(180)) }
            ) {
                DiaryEditorScreen(
                    initialDate = null,
                    diaryRepository = diaryRepository,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
