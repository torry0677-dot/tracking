package com.woosungtracking.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.woosungtracking.tracking.TrackingRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WoosungTheme { AppNav() } }
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val tracking by TrackingRepository.state.collectAsStateWithLifecycle()
    val start = remember { if (TrackingRepository.state.value.recording) "record" else "home" }

    // 기록이 끝나면 상세 화면으로 이동
    LaunchedEffect(tracking.finishedHikeId) {
        val id = tracking.finishedHikeId ?: return@LaunchedEffect
        TrackingRepository.consumeFinished()
        nav.navigate("hike/$id") {
            popUpTo(nav.graph.id) { inclusive = true }
        }
    }

    NavHost(navController = nav, startDestination = start) {
        composable("home") {
            HomeScreen(
                onStartRecording = { nav.navigate("record") },
                onOpenRecording = { nav.navigate("record") },
                onOpenHike = { id -> nav.navigate("hike/$id") },
            )
        }
        composable("record") {
            RecordScreen(onBack = {
                if (!nav.popBackStack()) nav.navigate("home") { popUpTo(nav.graph.id) { inclusive = true } }
            })
        }
        composable(
            "hike/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            HikeDetailScreen(hikeId = id, onBack = {
                if (!nav.popBackStack()) nav.navigate("home") { popUpTo(nav.graph.id) { inclusive = true } }
            })
        }
    }
}
