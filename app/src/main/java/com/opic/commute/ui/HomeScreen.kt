package com.opic.commute.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opic.commute.data.Store
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

fun koDay(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val store = app.store
    val today = LocalDate.now()
    val streak = store.currentStreak()
    val todayTopic = Store.todayTopic(today)
    val dueReviews = store.reviewPlan().filter { !it.next.isAfter(today) }

    ScreenFrame(
        title = "OPIc 출퇴근 학습",
        showBack = false,
        actions = {
            IconButton(onClick = { app.go(Screen.Settings) }) { Icon(Icons.Filled.Settings, contentDescription = "설정") }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${today.monthValue}월 ${today.dayOfMonth}일 (${koDay(today)})", style = MaterialTheme.typography.titleMedium)
            Tag(if (Store.weekSet(today) == 0) "A주차" else "B주차")
            Tag("🔥 ${streak}일 연속", Orange)
        }

        // 오늘 할 일
        when {
            todayTopic != null -> InfoCard(color = Blue.copy(alpha = 0.08f), onClick = { app.go(Screen.Hub(todayTopic.id)) }) {
                Text("오늘의 주제 (${koDay(today)})", color = Blue, fontWeight = FontWeight.Bold)
                Text(todayTopic.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(todayTopic.category)
                    Tag(todayTopic.type.label, Green)
                    if (store.mastered.containsKey(todayTopic.id)) Tag("마스터 ✓", Purple)
                }
                Muted(todayTopic.questionKo)
                Button(onClick = { app.go(Screen.Hub(todayTopic.id)) }, modifier = Modifier.fillMaxWidth()) { Text("오늘 학습 시작") }
            }
            today.dayOfWeek == DayOfWeek.SATURDAY -> InfoCard(color = Orange.copy(alpha = 0.10f), onClick = { app.go(Screen.Exam) }) {
                Text("토요일 시험", color = Orange, fontWeight = FontWeight.Bold)
                Text("이번 주 5개 주제 · 유사질문 10문제", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Muted("월~금에 배운 스크립트를 변형 질문에 적용해 보세요. 끝나면 예상 등급과 개선점을 알려 드려요.")
                Button(onClick = { app.go(Screen.Exam) }, modifier = Modifier.fillMaxWidth()) { Text("시험 시작") }
            }
            else -> InfoCard(color = Purple.copy(alpha = 0.08f)) {
                Text("일요일은 복습의 날", color = Purple, fontWeight = FontWeight.Bold)
                Muted("아래 복습 목록이나 마스터하지 못한 주제를 다시 해 보세요. 다음 주는 ${if (Store.weekSet(today.plusDays(1)) == 0) "A" else "B"}주차 주제로 진행돼요.")
            }
        }

        if (dueReviews.isNotEmpty()) {
            InfoCard {
                Text("오늘 복습할 주제 ${dueReviews.size}개", fontWeight = FontWeight.Bold)
                dueReviews.take(4).forEach { r ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("• ${r.topic.title}", modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = { app.go(Screen.Hub(r.topic.id)) }) { Text("복습") }
                    }
                }
            }
        }

        SectionTitle("학습 메뉴")
        MenuItem(1, "오리엔테이션", "OPIc 시험 구성, 주제 분야와 질문 유형 안내") { app.go(Screen.Orientation) }
        MenuItem(2, "답변 전략", "기승전결 순서로 답하는 방법과 유형별 템플릿") { app.go(Screen.Strategy) }
        MenuItem(3, "스크립트 학습", "영어 문장, 단어·발음, 한글 해석, 문장별 음성") { app.go(Screen.Picker(Mode.STUDY)) }
        MenuItem(4, "암기테스트", "빈칸 → 문장 순서 → 말하기, 선택해서 따라하기") { app.go(Screen.Picker(Mode.MEMORY)) }
        MenuItem(5, "유사질문 적용", "변형 질문에 말하고 유사도·적중률 확인") { app.go(Screen.Picker(Mode.SIMILAR)) }
        MenuItem(6, "완벽암기 체크", "처음부터 끝까지 말하고 기준 넘으면 마스터") { app.go(Screen.Picker(Mode.MASTERY)) }
        MenuItem(7, "토요일 시험", "월~금 주제 유사질문 10문제, 등급과 개선점") { app.go(Screen.Exam) }
        MenuItem(8, "성취도", "일·주제·유형별 점수, 연속 학습일, 복습 일정") { app.go(Screen.Stats) }
    }
}

@Composable
private fun MenuItem(no: Int, title: String, desc: String, onClick: () -> Unit) {
    InfoCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(Blue, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("$no", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            HSpace(12)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Muted(desc)
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val store = app.store
    val context = androidx.compose.ui.platform.LocalContext.current
    ScreenFrame("설정") {
        InfoCard {
            Text("음성 속도: ${"%.1f".format(store.ttsRate)}배", fontWeight = FontWeight.Bold)
            androidx.compose.material3.Slider(
                value = store.ttsRate,
                onValueChange = { store.updateSettings(rate = (it * 10).toInt() / 10f); app.tts.rate = store.ttsRate },
                valueRange = 0.5f..1.3f,
                steps = 7,
            )
            androidx.compose.material3.OutlinedButton(onClick = { app.tts.speak("Hello, this is your OPIc commute coach. Let's practice together.", "sample") }) {
                Text("샘플 듣기")
            }
        }
        InfoCard {
            Text("전체 듣기 문장 사이 쉬는 시간: ${store.pauseSec}초", fontWeight = FontWeight.Bold)
            androidx.compose.material3.Slider(
                value = store.pauseSec.toFloat(),
                onValueChange = { store.updateSettings(pause = it.toInt()) },
                valueRange = 0f..5f,
                steps = 4,
            )
            Muted("출퇴근 중 듣기만 할 때 사이에 따라 말할 시간을 줍니다.")
        }
        InfoCard {
            Text("완벽암기 마스터 기준: ${store.masteryThreshold}점", fontWeight = FontWeight.Bold)
            androidx.compose.material3.Slider(
                value = store.masteryThreshold.toFloat(),
                onValueChange = { store.updateSettings(threshold = (it / 5).toInt() * 5) },
                valueRange = 60f..95f,
                steps = 6,
            )
        }
        InfoCard {
            Text("음성이 안 나오거나 한국어 억양일 때", fontWeight = FontWeight.Bold)
            Muted("설정 > 일반 > 텍스트 음성 변환(TTS)에서 기본 엔진의 영어(미국) 음성을 내려받아 주세요. 음성 인식은 Google 음성 인식이 가장 정확합니다.")
            if (app.tts.missingVoice) Text("영어 음성 데이터가 설치되어 있지 않은 것 같아요.", color = Red)
            androidx.compose.material3.OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(android.content.Intent("com.android.settings.TTS_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("TTS 설정 열기") }
        }
        Muted("학습 기록은 이 휴대폰 안에만 저장됩니다.", Modifier.padding(4.dp))
    }
}
