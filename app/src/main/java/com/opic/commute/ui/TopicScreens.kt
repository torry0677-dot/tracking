package com.opic.commute.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opic.commute.data.Activity
import com.opic.commute.data.Content
import com.opic.commute.data.Store
import com.opic.commute.data.Topic
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun QuestionCard(topic: Topic, showKo: Boolean = true, en: String = topic.question, ko: String = topic.questionKo) {
    InfoCard(color = Blue.copy(alpha = 0.07f)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(topic.category)
            Tag(topic.type.label, Green)
        }
        Row(verticalAlignment = Alignment.Top) {
            Text("Q. $en", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            SpeakButton(en, "q-$en")
        }
        if (showKo) Muted(ko)
    }
}

@Composable
fun PickerScreen(mode: Mode) {
    val app = LocalApp.current
    val store = app.store
    val today = Store.todayTopic()
    ScreenFrame(mode.label) {
        Muted("주제를 고르세요. 오늘 배정된 주제에는 '오늘' 표시가 있어요.")
        listOf(0, 1).forEach { set ->
            SectionTitle(if (set == 0) "A주차 주제" else "B주차 주제")
            Store.topicsOfSet(set).forEachIndexed { i, t ->
                InfoCard(onClick = {
                    app.go(
                        when (mode) {
                            Mode.STUDY -> Screen.Study(t.id)
                            Mode.MEMORY -> Screen.Memory(t.id)
                            Mode.SIMILAR -> Screen.Similar(t.id)
                            Mode.MASTERY -> Screen.Mastery(t.id)
                        },
                    )
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${"월화수목금"[i]}  ${t.title}", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (today?.id == t.id) Tag("오늘", Orange)
                        if (store.mastered.containsKey(t.id)) Tag("마스터", Purple)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tag(t.category)
                        Tag(t.type.label, Green)
                    }
                }
            }
        }
    }
}

@Composable
fun HubScreen(topicId: Int) {
    val app = LocalApp.current
    val store = app.store
    val t = Content.topic(topicId)
    ScreenFrame(t.title) {
        QuestionCard(t)
        Muted("아래 순서대로 진행하면 하루 학습이 끝나요.")
        HubStep("3", "스크립트 학습", "문장 ${t.sentences.size}개 · 단어 ${t.vocab.size}개", store.best(t.id, Activity.STUDY)) { app.go(Screen.Study(t.id)) }
        HubStep(
            "4", "암기테스트", "빈칸 ${score(store.best(t.id, Activity.BLANK))} · 순서 ${score(store.best(t.id, Activity.ORDER))} · 말하기 ${score(store.best(t.id, Activity.SPEAK))}",
            listOfNotNull(store.best(t.id, Activity.BLANK), store.best(t.id, Activity.ORDER), store.best(t.id, Activity.SPEAK)).maxOrNull(),
        ) { app.go(Screen.Memory(t.id)) }
        HubStep("5", "유사질문 적용", "변형 질문 ${t.similar.size}개", store.best(t.id, Activity.SIMILAR)) { app.go(Screen.Similar(t.id)) }
        HubStep(
            "6", "완벽암기 체크",
            if (store.mastered.containsKey(t.id)) "마스터 완료 (${store.mastered[t.id]})" else "기준 ${store.masteryThreshold}점 이상이면 마스터",
            store.best(t.id, Activity.MASTERY),
        ) { app.go(Screen.Mastery(t.id)) }
    }
}

private fun score(s: Int?) = s?.toString() ?: "-"

@Composable
private fun HubStep(no: String, title: String, desc: String, best: Int?, onClick: () -> Unit) {
    InfoCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$no. $title", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Muted(desc)
            }
            if (best != null) ScoreCircle(best, "최고", size = 52)
        }
    }
}

/** 3. 스크립트 학습 */
@Composable
fun StudyScreen(topicId: Int) {
    val app = LocalApp.current
    val tts = app.tts
    val t = Content.topic(topicId)
    val scope = rememberCoroutineScope()
    var showKo by remember { mutableStateOf(true) }
    var showEn by remember { mutableStateOf(true) }
    var repeatAll by remember { mutableStateOf(false) }
    var playIndex by remember { mutableIntStateOf(-1) }
    var playJob by remember { mutableStateOf<Job?>(null) }

    fun stopAll() {
        playJob?.cancel()
        playJob = null
        playIndex = -1
        tts.stop()
    }

    fun playAll() {
        stopAll()
        playJob = scope.launch {
            do {
                for (i in t.sentences.indices) {
                    playIndex = i
                    tts.speakAwait(t.sentences[i].en, "all-$i")
                    delay(app.store.pauseSec * 1000L + 200)
                }
            } while (repeatAll)
            playIndex = -1
            playJob = null
        }
    }

    ScreenFrame(
        title = "스크립트 · ${t.title}",
        bottomBar = {
            NavigationBar {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { if (playJob != null) stopAll() else playAll() }, modifier = Modifier.weight(1f)) {
                        Icon(if (playJob != null) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
                        Text(if (playJob != null) " 멈춤" else " 전체 듣기")
                    }
                    FilterChip(
                        selected = repeatAll,
                        onClick = { repeatAll = !repeatAll },
                        label = { Text("반복") },
                        leadingIcon = { Icon(Icons.Filled.Repeat, null) },
                    )
                }
            }
        },
    ) {
        QuestionCard(t)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = showEn, onClick = { showEn = !showEn }, label = { Text("영어 보기") })
            FilterChip(selected = showKo, onClick = { showKo = !showKo }, label = { Text("해석 보기") })
        }
        Muted("🔊 보통 속도 · 🔊(주황) 느리게. 영어를 끄면 한글만 보고 떠올리는 연습이 됩니다.")
        t.sentences.forEachIndexed { i, s ->
            val active = playIndex == i
            InfoCard(color = if (active) partColor(s.part).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface) {
                Row(verticalAlignment = Alignment.Top) {
                    PartBadge(s.part)
                    HSpace(10)
                    Column(Modifier.weight(1f)) {
                        if (showEn) Text("${i + 1}. ${s.en}", fontSize = 17.sp, lineHeight = 24.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                        else Text("${i + 1}. " + s.en.split(" ").joinToString(" ") { w -> w.take(1) + "_".repeat((w.length - 1).coerceIn(0, 6)) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (showKo) Muted(s.ko)
                    }
                    Column {
                        SpeakButton(s.en, "s-$i")
                        SpeakButton(s.en, "slow-$i", slow = true)
                    }
                }
            }
        }
        SectionTitle("핵심 단어와 발음")
        InfoCard {
            t.vocab.forEachIndexed { i, v ->
                if (i > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(v.word, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${v.ipa}  [${v.koPron}]", color = Blue, fontSize = 14.sp)
                        Muted(v.meaning)
                    }
                    SpeakButton(v.word, "v-$i")
                    SpeakButton(v.word, "vs-$i", slow = true)
                }
            }
        }
        SectionTitle("핵심 표현 (유사질문 적중률 기준)")
        Muted(t.keyPhrases.joinToString("  ·  "))
        Button(onClick = {
            app.store.add(t.id, Activity.STUDY, 100)
            app.replace(Screen.Memory(t.id))
        }, modifier = Modifier.fillMaxWidth()) { Text("학습 완료 → 암기테스트") }
    }
}

/** 4. 암기테스트 목록 */
@Composable
fun MemoryHubScreen(topicId: Int) {
    val app = LocalApp.current
    val store = app.store
    val t = Content.topic(topicId)
    val blank = store.best(t.id, Activity.BLANK)
    val order = store.best(t.id, Activity.ORDER)
    val speak = store.best(t.id, Activity.SPEAK)
    ScreenFrame("암기테스트 · ${t.title}") {
        Muted("단계마다 난이도가 올라갑니다. 이전 단계를 60점 이상 받으면 다음 단계가 열려요. (잠겨 있어도 도전은 가능)")
        LevelCard("1단계", "빈칸 채우기", "문장 속 핵심 단어를 4지선다로 고르기", blank, locked = false) { app.go(Screen.Blank(t.id)) }
        LevelCard("2단계", "문장 순서 맞추기", "섞인 문장을 기승전결 순서대로, 단어 배열까지", order, locked = (blank ?: 0) < 60) { app.go(Screen.Order(t.id)) }
        LevelCard("3단계", "말하기", "한글 해석만 보고 영어 문장을 말하기", speak, locked = (order ?: 0) < 60) { app.go(Screen.Speak(t.id)) }
        SectionTitle("자유 연습")
        LevelCard("선택", "선택해서 따라하기", "원하는 문장만 골라 듣고 → 따라 말하고 → 점수 확인", store.best(t.id, Activity.FOLLOW), locked = false) { app.go(Screen.Follow(t.id)) }
    }
}

@Composable
private fun LevelCard(level: String, title: String, desc: String, best: Int?, locked: Boolean, onClick: () -> Unit) {
    InfoCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(level, if (locked) MaterialTheme.colorScheme.outline else Blue)
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (locked) Icon(Icons.Filled.Lock, contentDescription = "잠김", tint = MaterialTheme.colorScheme.outline)
                }
                Muted(desc)
            }
            if (best != null) ScoreCircle(best, "최고", size = 52)
        }
    }
}

@Composable
fun ResultPanel(score: Int, title: String, onRetry: () -> Unit, onNext: (() -> Unit)?, nextLabel: String = "다음 단계") {
    InfoCard(color = scoreColor(score).copy(alpha = 0.08f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreCircle(score, title)
            HSpace(16)
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        score >= 90 -> "완벽해요! 다음 단계로 가세요."
                        score >= 60 -> "좋아요. 다음 단계가 열렸어요."
                        else -> "조금 더 연습해 봐요. 다시 하면 금방 올라가요."
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("다시 하기") }
            if (onNext != null) Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text(nextLabel) }
        }
    }
}
