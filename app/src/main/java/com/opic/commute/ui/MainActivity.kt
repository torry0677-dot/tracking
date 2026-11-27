package com.opic.commute.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.opic.commute.data.Store
import com.opic.commute.logic.Stt
import com.opic.commute.logic.Tts

/** 화면 목록 */
sealed interface Screen {
    data object Home : Screen
    data object Orientation : Screen
    data object Strategy : Screen
    data class Picker(val mode: Mode) : Screen
    data class Hub(val topicId: Int) : Screen
    data class Study(val topicId: Int) : Screen
    data class Memory(val topicId: Int) : Screen
    data class Blank(val topicId: Int) : Screen
    data class Order(val topicId: Int) : Screen
    data class Speak(val topicId: Int) : Screen
    data class Follow(val topicId: Int, val preselect: Int = -1) : Screen
    data class Similar(val topicId: Int) : Screen
    data class Mastery(val topicId: Int) : Screen
    data object Exam : Screen
    data object Stats : Screen
    data object Settings : Screen
}

enum class Mode(val label: String) {
    STUDY("스크립트 학습"),
    MEMORY("암기테스트"),
    SIMILAR("유사질문 적용"),
    MASTERY("완벽암기 체크"),
}

class AppState(context: Context) {
    val store = Store(context)
    val tts = Tts(context).also { it.rate = store.ttsRate }
    val stt = Stt(context)
    val stack = mutableStateListOf<Screen>(Screen.Home)

    val current: Screen get() = stack.last()

    private fun quiet() {
        tts.stop()
        stt.cancel()
    }

    fun go(s: Screen) {
        quiet()
        stack.add(s)
    }

    /** 현재 화면을 바꿔 끼웁니다 (뒤로가기 시 이전 화면으로) */
    fun replace(s: Screen) {
        quiet()
        stack[stack.size - 1] = s
    }

    fun back() {
        quiet()
        if (stack.size > 1) stack.removeAt(stack.size - 1)
    }

    fun home() {
        quiet()
        while (stack.size > 1) stack.removeAt(stack.size - 1)
    }
}

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState 없음") }

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app = AppState(applicationContext)
        setContent {
            OpicTheme {
                CompositionLocalProvider(LocalApp provides app) {
                    Root(app)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 화면을 끄면 음성 인식은 멈춥니다 (TTS 듣기는 계속).
        app.stt.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        app.tts.shutdown()
        app.stt.cancel()
    }
}

@Composable
private fun Root(app: AppState) {
    BackHandler(enabled = app.stack.size > 1) { app.back() }
    AnimatedContent(targetState = app.current, label = "screen") { screen ->
        when (screen) {
            Screen.Home -> HomeScreen()
            Screen.Orientation -> OrientationScreen()
            Screen.Strategy -> StrategyScreen()
            is Screen.Picker -> PickerScreen(screen.mode)
            is Screen.Hub -> HubScreen(screen.topicId)
            is Screen.Study -> StudyScreen(screen.topicId)
            is Screen.Memory -> MemoryHubScreen(screen.topicId)
            is Screen.Blank -> BlankScreen(screen.topicId)
            is Screen.Order -> OrderScreen(screen.topicId)
            is Screen.Speak -> SpeakScreen(screen.topicId)
            is Screen.Follow -> FollowScreen(screen.topicId, screen.preselect)
            is Screen.Similar -> SimilarScreen(screen.topicId)
            is Screen.Mastery -> MasteryScreen(screen.topicId)
            Screen.Exam -> ExamScreen()
            Screen.Stats -> StatsScreen()
            Screen.Settings -> SettingsScreen()
        }
    }
}
