package com.nexttimeemail.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexttimeemail.ui.history.HistoryScreen
import com.nexttimeemail.ui.meeting.MeetingScreen
import com.nexttimeemail.ui.roster.RosterScreen

object Routes {
    const val ROSTER = "roster"
    const val MEETING = "meeting"
    const val HISTORY = "history"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.ROSTER) {
        composable(Routes.ROSTER) {
            RosterScreen(
                onStartMeeting = { navController.navigate(Routes.MEETING) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
            )
        }
        composable(Routes.MEETING) {
            MeetingScreen(onFinished = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
    }
}
