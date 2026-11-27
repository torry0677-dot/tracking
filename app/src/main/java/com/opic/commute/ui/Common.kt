package com.opic.commute.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.opic.commute.data.Part

val Blue = Color(0xFF1E4FD8)
val Green = Color(0xFF1B8A5A)
val Red = Color(0xFFD64545)
val Orange = Color(0xFFE58A1F)
val Purple = Color(0xFF7A4FD8)

@Composable
fun OpicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            secondary = Green,
            background = Color(0xFFF5F7FB),
            surface = Color.White,
            surfaceVariant = Color(0xFFEDF1F8),
        ),
        content = content,
    )
}

fun partColor(p: Part): Color = when (p) {
    Part.GI -> Blue
    Part.SEUNG -> Green
    Part.JEON -> Orange
    Part.GYEOL -> Purple
}

fun scoreColor(score: Int): Color = when {
    score >= 80 -> Green
    score >= 60 -> Blue
    score >= 40 -> Orange
    else -> Red
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenFrame(
    title: String,
    showBack: Boolean = true,
    actions: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val app = LocalApp.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = { app.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                        }
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = bottomBar,
    ) { pad ->
        val mod = Modifier
            .fillMaxSize()
            .padding(pad)
            .let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
            .padding(horizontal = 16.dp, vertical = 8.dp)
        Column(mod, verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = color)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, content = inner)
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, content = inner)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
fun Tag(text: String, color: Color = Blue) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable
fun PartBadge(part: Part) {
    Box(
        Modifier
            .size(28.dp)
            .background(partColor(part), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(part.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** 문장 듣기 버튼: 재생 중이면 정지 */
@Composable
fun SpeakButton(text: String, id: String, slow: Boolean = false) {
    val tts = LocalApp.current.tts
    val playing = tts.speakingId == id
    IconButton(onClick = { if (playing) tts.stop() else tts.speak(text, id, slow) }) {
        Icon(
            if (playing) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = if (playing) "정지" else "듣기",
            tint = if (slow) Orange else Blue,
        )
    }
}

@Composable
fun MicButton(listening: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val level = LocalApp.current.stt.level
    val ring = if (listening) (8 + level.coerceIn(0f, 10f) * 1.5f).dp else 0.dp
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
        if (listening) {
            Box(
                Modifier
                    .size(72.dp + ring)
                    .background(Red.copy(alpha = 0.18f), CircleShape),
            )
        }
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (listening) Red else Blue),
        ) {
            Icon(if (listening) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = if (listening) "멈춤" else "말하기", modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
fun ScoreCircle(score: Int, label: String, size: Int = 84) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size.dp)
                .border(6.dp, scoreColor(score), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$score", fontSize = (size / 3).sp, fontWeight = FontWeight.Bold, color = scoreColor(score))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ScoreBar(label: String, score: Int?, modifier: Modifier = Modifier, suffix: String = "") {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(if (score == null) "-" else "$score$suffix", fontWeight = FontWeight.Bold, color = if (score == null) Color.Gray else scoreColor(score))
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ((score ?: 0) / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = if (score == null) Color.LightGray else scoreColor(score),
            trackColor = Color(0xFFE3E8F0),
        )
    }
}

/** 단어별로 맞음(초록)/빠짐(빨강 취소선) 표시 */
@Composable
fun MarkedText(marks: List<Pair<String, Boolean>>) {
    val s = buildAnnotatedString {
        marks.forEachIndexed { i, (w, ok) ->
            if (ok) {
                withStyle(SpanStyle(color = Green, fontWeight = FontWeight.SemiBold)) { append(w) }
            } else {
                withStyle(SpanStyle(color = Red, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)) { append(w) }
            }
            if (i < marks.size - 1) append(" ")
        }
    }
    Text(s, fontSize = 17.sp, lineHeight = 24.sp)
}

/** 마이크 권한 요청 후 실행 */
@Composable
fun rememberMicPermission(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pending?.invoke() else denied = true
        pending = null
    }
    if (denied) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { denied = false },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { denied = false }) { Text("확인") } },
            title = { Text("마이크 권한이 필요해요") },
            text = { Text("말하기 연습은 마이크로 음성을 인식합니다. 휴대폰 설정 > 애플리케이션 > OPIc 출퇴근 > 권한에서 마이크를 허용해 주세요.") },
        )
    }
    return { action ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pending = action
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

@Composable
fun LiveTranscript(text: String, placeholder: String = "마이크 버튼을 누르고 영어로 말해 보세요.") {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text.ifBlank { placeholder },
            modifier = Modifier.padding(14.dp),
            color = if (text.isBlank()) Color.Gray else Color.Black,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
fun ErrorText(msg: String?) {
    if (msg != null) Text(msg, color = Red, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun HSpace(w: Int) = Spacer(Modifier.width(w.dp))

val CardPadding = PaddingValues(16.dp)
