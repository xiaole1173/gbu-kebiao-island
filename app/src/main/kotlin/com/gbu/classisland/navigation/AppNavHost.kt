// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.gbu.classisland.R
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gbu.classisland.ui.screens.SettingsScreen
import com.gbu.classisland.ui.screens.TodayScreen
import com.gbu.classisland.ui.screens.WeekScreen

/** 顶层路由。 */
sealed class Screen(val route: String, val label: String) {
    data object Week : Screen("week", "周课表")
    data object Today : Screen("today", "今日")
    data object Settings : Screen("settings", "设置")
}

/** 页面切换动画时长（短淡入淡出：避免新旧页面长时间同时渲染导致的切页卡顿）。 */
private val pageAnim = tween<Float>(140)

@Composable
fun AppNavHost(initialRoute: String = Screen.Week.route) {
    val navController = rememberNavController()
    val items = listOf(Screen.Week, Screen.Today, Screen.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val iconRes = when (screen) {
                                is Screen.Week -> R.drawable.lucide_calendar
                                is Screen.Today -> R.drawable.lucide_clock
                                is Screen.Settings -> R.drawable.lucide_settings
                            }
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        // 页面切换：淡入淡出 + 轻微横向滑动过渡
        NavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(pageAnim) },
            exitTransition = { fadeOut(pageAnim) },
            popEnterTransition = { fadeIn(pageAnim) },
            popExitTransition = { fadeOut(pageAnim) }
        ) {
            composable(Screen.Week.route) { WeekScreen() }
            composable(Screen.Today.route) { TodayScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
