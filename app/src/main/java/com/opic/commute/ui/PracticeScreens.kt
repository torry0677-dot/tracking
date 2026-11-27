package com.opic.commute.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.opic.commute.data.Activity
import com.opic.commute.data.Content
import com.opic.commute.data.Part
import com.opic.commute.data.QType
import com.opic.commute.data.SimilarQ
import com.opic.commute.data.Store
import com.opic.commute.data.Topic
import com.opic.commute.logic.Scoring
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

@Composable
fun rememberElapsed(running: Boolean): Int {
    var sec by remember { mutableIntStateOf(0) }
    LaunchedEffect(running) {
        if (running) {
            sec = 0
            while (true) {
                delay(1000)
                sec++
            }
        }
    }
    return sec
}

fun mmss(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

@Composable
fun SimilarResultCard(r: Scoring.SimilarResult, heard: String) {
    InfoCard(color = scoreColor(r.total).copy(alpha = 0.07f)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScoreCircle(r.total, "종합", size = 74)
            ScoreCircle(r.similarity, "유사도", size = 60)
            ScoreCircle(r.hitRate, "적중률", size = 60)
        }
        Muted("유사도: 내가 말한 내용이 스크립트와 얼마나 비슷한지 · 적중률: 핵심 표현 ${r.hits.size + r.misses.size}개 중 ${r.hits.size}개 사용 · 분량 ${r.wordCount}단어 (70단어 이상 권장)")
        if (r.hits.isNotEmpty()) Text("✓ 사용한 표현: " + r.hits.joinToString(", "), color = Green)
        if (r.misses.isNotEmpty()) Text("✗ 빠진 표현: " + r.misses.joinToString(", "), color = Red)
        Muted("내가 말한 내용")
        Text(heard.ifBlank { "(인식된 내용 없음)" })
    }
}

/** 5. 유사질문 적용 */
@Composable
fun SimilarScreen(topicId: Int) {
    val app = LocalApp.current
    val stt = app.stt
    val t = Content.topic(topicId)
    val ask = rememberMicPermission()
    var qi by remember { mutableIntStateOf(0) }
    var showScript by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf<String?>(null) }
    val elapsed = rememberElapsed(stt.listening)
    val q = t.similar[qi]

    ScreenFrame("유사질문 · ${t.title}") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            t.similar.indices.forEach { k ->
                FilterChip(selected = qi == k, onClick = { stt.cancel(); qi = k; heard = null }, label = { Text("변형 ${k + 1}") })
            }
        }
        InfoCard(color = Blue.copy(alpha = 0.07f)) {
            Tag(q.type.label, Green)
            Row(verticalAlignment = Alignment.Top) {
                Text("Q. ${q.en}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                SpeakButton(q.en, "sim-$qi")
            }
            Muted(q.ko)
        }
        InfoCard(color = Orange.copy(alpha = 0.08f)) {
            Text("💡 변형 포인트", fontWeight = FontWeight.Bold, color = Orange)
            Text(q.tip)
            Muted("원래 질문: ${t.questionKo}")
        }
        TextButton(onClick = { showScript = !showScript }) { Text(if (showScript) "원본 스크립트 숨기기" else "원본 스크립트 보기") }
        if (showScript) {
            InfoCard {
                t.sentences.forEachIndexed { i, s -> Text("${i + 1}. ${s.en}", fontSize = 14.sp) }
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MicButton(stt.listening) {
                if (stt.listening) stt.stop() else ask {
                    heard = null
                    showScript = false
                    stt.start(continuous = true) { text ->
                        heard = text
                        val r = Scoring.similar(text, t.fullScript, t.keyPhrases)
                        app.store.add(t.id, Activity.SIMILAR, r.total)
                    }
                }
            }
            Text(if (stt.listening) "● 녹음 중 ${mmss(elapsed)}  (다 말하면 멈춤)" else "버튼을 누르고 1~2분 동안 답해 보세요", color = if (stt.listening) Red else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (stt.listening) LiveTranscript(stt.liveText)
        ErrorText(stt.error)
        heard?.let { SimilarResultCard(Scoring.similar(it, t.fullScript, t.keyPhrases), it) }
        if (heard != null) {
            Button(onClick = { app.replace(Screen.Mastery(t.id)) }, modifier = Modifier.fillMaxWidth()) { Text("다음: 완벽암기 체크") }
        }
    }
}

/** 6. 완벽암기 체크 */
@Composable
fun MasteryScreen(topicId: Int) {
    val app = LocalApp.current
    val stt = app.stt
    val store = app.store
    val t = Content.topic(topicId)
    val ask = rememberMicPermission()
    var heard by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf(false) }
    val elapsed = rememberElapsed(stt.listening)
    val threshold = store.masteryThreshold

    ScreenFrame("완벽암기 · ${t.title}") {
        QuestionCard(t, showKo = false)
        InfoCard {
            Text("스크립트 ${t.sentences.size}문장을 처음부터 끝까지 말해 보세요.", fontWeight = FontWeight.Bold)
            Muted("순서대로 정확히 말한 단어 비율로 채점합니다. ${threshold}점 이상이면 마스터! (기준은 설정에서 변경)")
            if (store.mastered.containsKey(t.id)) Tag("이미 마스터한 주제 · ${store.mastered[t.id]}", Purple)
            TextButton(onClick = { hint = !hint }) { Text(if (hint) "기승전결 힌트 숨기기" else "기승전결 힌트 보기") }
            if (hint) {
                Part.entries.forEach { p ->
                    val first = t.sentences.firstOrNull { it.part == p }
                    if (first != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        PartBadge(p)
                        HSpace(8)
                        Text(first.ko, fontSize = 14.sp)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MicButton(stt.listening) {
                if (stt.listening) stt.stop() else ask {
                    heard = null
                    stt.start(continuous = true) { text ->
                        heard = text
                        val sc = Scoring.recall(t.fullScript, text)
                        store.add(t.id, Activity.MASTERY, sc)
                        if (sc >= threshold) store.setMastered(t.id)
                    }
                }
            }
            Text(if (stt.listening) "● 녹음 중 ${mmss(elapsed)}  (끝까지 말하면 멈춤)" else "버튼을 누르고 시작하세요", color = if (stt.listening) Red else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (stt.listening) LiveTranscript(stt.liveText)
        ErrorText(stt.error)
        heard?.let { h ->
            val sc = Scoring.recall(t.fullScript, h)
            val pass = sc >= threshold
            InfoCard(color = (if (pass) Purple else Orange).copy(alpha = 0.1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreCircle(sc, "완성도")
                    HSpace(16)
                    Column(Modifier.weight(1f)) {
                        Text(if (pass) "🏆 마스터! 이 주제를 완벽히 외웠어요." else "기준 ${threshold}점까지 ${threshold - sc}점 남았어요. 다시 도전해요!", fontWeight = FontWeight.Bold)
                        Muted("말한 단어 ${Scoring.tokens(h).size}개 / 스크립트 ${Scoring.tokens(t.fullScript).size}개")
                    }
                }
                Button(onClick = { heard = null }, modifier = Modifier.fillMaxWidth()) { Text(if (pass) "한 번 더 말하기" else "다시 도전") }
            }
            SectionTitle("문장별 결과")
            t.sentences.forEachIndexed { i, s ->
                val ssc = Scoring.recall(s.en, h)
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, color = scoreColor(ssc))
                        HSpace(8)
                        Column(Modifier.weight(1f)) { MarkedText(Scoring.wordMarks(s.en, h)) }
                        Text("$ssc", fontWeight = FontWeight.Bold, color = scoreColor(ssc))
                    }
                    if (ssc < 70) TextButton(onClick = { app.go(Screen.Follow(t.id, i)) }) { Text("이 문장 따라하기") }
                }
            }
        }
    }
}

// ---------------- 7. 토요일 시험 ----------------

data class ExamQ(val topic: Topic, val q: SimilarQ)

fun buildExam(set: Int, seed: Long): List<ExamQ> {
    val rnd = Random(seed)
    return if (set < 0) {
        Content.topics.map { t -> ExamQ(t, t.similar[rnd.nextInt(t.similar.size)]) }
    } else {
        Store.topicsOfSet(set).flatMap { t -> t.similar.shuffled(rnd).take(2).map { ExamQ(t, it) } }
    }.shuffled(rnd)
}

fun examFeedback(qs: List<ExamQ>, answers: List<String>, results: List<Scoring.SimilarResult>): List<String> {
    val tips = ArrayList<String>()
    val avgWords = results.map { it.wordCount }.average().takeIf { !it.isNaN() } ?: 0.0
    if (avgWords < 50) tips += "답변이 짧아요 (평균 ${avgWords.toInt()}단어). '승' 단계에 언제·어디서·누구와 같은 디테일을 2~3문장 더 붙여 70단어 이상을 목표로 하세요."
    else if (avgWords < 70) tips += "분량은 괜찮아요 (평균 ${avgWords.toInt()}단어). '전' 단계 에피소드를 한두 문장 더 넣으면 IH 이상을 노릴 수 있어요."
    else tips += "분량이 충분해요 (평균 ${avgWords.toInt()}단어). 이제 정확성과 연결어에 집중하세요."

    val used = answers.flatMap { Scoring.connectorsUsed(it) }.toSet()
    if (used.size < 5) tips += "연결어가 부족해요 (사용: ${used.joinToString().ifBlank { "없음" }}). First, Then, Also, To be honest, That's why 등을 의식적으로 넣어 기승전결을 드러내세요."
    else tips += "연결어를 다양하게 썼어요 (${used.size}종류). 좋습니다!"

    val expIdx = qs.indices.filter { qs[it].q.type == QType.EXPERIENCE }
    val noPast = expIdx.filter { !Scoring.usesPastTense(answers[it]) }
    if (noPast.isNotEmpty()) tips += "과거 경험 질문 ${expIdx.size}개 중 ${noPast.size}개에서 과거 시제가 부족했어요. went, was, had처럼 과거형으로 이야기하세요."

    val cmpIdx = qs.indices.filter { qs[it].q.type == QType.COMPARE }
    val noCompare = cmpIdx.filter { i ->
        val tk = " " + Scoring.tokens(answers[i]).joinToString(" ") + " "
        listOf(" in the past ", " these days ", " now ", " nowadays ", " used to ", " before ").none { tk.contains(it) }
    }
    if (noCompare.isNotEmpty()) tips += "비교·변화 질문에서 'In the past ~, but these days ~' 같은 대조 표현이 빠졌어요."

    val rpIdx = qs.indices.filter { qs[it].q.type == QType.ROLEPLAY }
    val weakRp = rpIdx.filter { i ->
        val tk = " " + Scoring.tokens(answers[i]).joinToString(" ") + " "
        listOf(" could you ", " do you ", " is there ", " how much ", " can i ", " are there ", " what time ", " does ").count { tk.contains(it) } < 3
    }
    if (weakRp.isNotEmpty()) tips += "롤플레이에서 질문 문장이 3개 미만이었어요. 'Could you tell me ~?', 'Is there ~?', 'How much ~?'로 질문을 3~4개 채우세요."

    val byTopic = qs.indices.groupBy { qs[it].topic }.mapValues { (_, idx) -> idx.map { results[it].total }.average() }
    val weak = byTopic.filter { it.value < 60 }.keys
    if (weak.isNotEmpty()) tips += "다음 주제는 복습이 필요해요: ${weak.joinToString { it.title }}. 스크립트 학습 → 완벽암기 체크를 다시 해 보세요."
    val strong = byTopic.filter { it.value >= 80 }.keys
    if (strong.isNotEmpty()) tips += "잘한 주제: ${strong.joinToString { it.title }} 👍"

    val missedHits = results.flatMap { it.misses }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key }
    if (missedHits.isNotEmpty()) tips += "자주 빠뜨린 핵심 표현: ${missedHits.joinToString(", ")}"
    return tips
}

@Composable
fun ExamScreen() {
    val app = LocalApp.current
    val stt = app.stt
    val store = app.store
    val today = LocalDate.now()
    val ask = rememberMicPermission()
    var set by remember { mutableIntStateOf(Store.weekSet(today)) }
    var started by remember { mutableStateOf(false) }
    var k by remember { mutableIntStateOf(0) }
    var showKo by remember { mutableStateOf(false) }
    val answers = remember { mutableStateListOf<String>() }
    var finished by remember { mutableStateOf(false) }
    val qs = remember(set) { buildExam(set, today.toEpochDay() + set) }
    val elapsed = rememberElapsed(stt.listening)
    var current by remember { mutableStateOf<String?>(null) }

    ScreenFrame("토요일 시험") {
        if (!started) {
            InfoCard(color = Orange.copy(alpha = 0.1f)) {
                Text("유사질문 10문제 실전 모의고사", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Muted("문제마다 질문을 듣고 1~2분 동안 답하세요. 채점은 모든 문제를 마친 뒤 한 번에 보여 줍니다. (실제 시험처럼 한글 해석은 기본으로 숨김)")
                if (today.dayOfWeek != DayOfWeek.SATURDAY) Text("오늘은 토요일이 아니라 연습 모드예요. 결과는 그대로 기록됩니다.", color = Orange)
            }
            Text("출제 범위", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = set == 0, onClick = { set = 0 }, label = { Text("A주차") })
                FilterChip(selected = set == 1, onClick = { set = 1 }, label = { Text("B주차") })
                FilterChip(selected = set == -1, onClick = { set = -1 }, label = { Text("전체 10주제") })
            }
            Muted("이번 주는 ${if (Store.weekSet(today) == 0) "A" else "B"}주차입니다: " + Store.topicsOfSet(Store.weekSet(today)).joinToString(", ") { it.title })
            Button(onClick = { ask { started = true; k = 0; answers.clear(); finished = false } }, modifier = Modifier.fillMaxWidth()) {
                Text("시험 시작 (${qs.size}문제)")
            }
            if (store.exams.isNotEmpty()) {
                SectionTitle("지난 시험")
                store.exams.takeLast(5).reversed().forEach { e ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${e.date} (${koDay(e.date)})")
                        Text("${e.score}점 · ${e.level}", fontWeight = FontWeight.Bold, color = scoreColor(e.score))
                    }
                }
            }
            return@ScreenFrame
        }

        if (finished) {
            val results = qs.indices.map { Scoring.similar(answers[it], qs[it].topic.fullScript, qs[it].topic.keyPhrases) }
            val total = results.sumOf { it.total } / results.size
            val level = Scoring.level(total)
            LaunchedEffect(Unit) {
                store.addExam(total, level.code, qs.size)
                qs.indices.forEach { store.add(qs[it].topic.id, Activity.EXAM, results[it].total) }
            }
            InfoCard(color = scoreColor(total).copy(alpha = 0.08f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreCircle(total, "총점", size = 96)
                    HSpace(16)
                    Column(Modifier.weight(1f)) {
                        Text("예상 등급", style = MaterialTheme.typography.labelLarge)
                        Text(level.code, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = scoreColor(total))
                        Text(level.title, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(level.desc)
                Muted("앱 자체 기준의 추정치예요. 실제 등급은 발음, 억양, 문법 정확도도 함께 평가됩니다.")
            }
            SectionTitle("개선점")
            InfoCard {
                examFeedback(qs, answers, results).forEach { Text("• $it", lineHeight = 22.sp) }
            }
            SectionTitle("문제별 점수")
            qs.forEachIndexed { i, q ->
                val r = results[i]
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${i + 1}. ${q.q.en}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Muted("${q.topic.title} · ${q.q.type.label} · 유사도 ${r.similarity} · 적중률 ${r.hitRate} · ${r.wordCount}단어")
                        }
                        Text("${r.total}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = scoreColor(r.total))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { started = false; finished = false }, modifier = Modifier.weight(1f)) { Text("처음으로") }
                Button(onClick = { app.replace(Screen.Stats) }, modifier = Modifier.weight(1f)) { Text("성취도 보기") }
            }
            return@ScreenFrame
        }

        val q = qs[k]
        LaunchedEffect(k) {
            current = null
            app.tts.speak(q.q.en, "exam-$k")
        }
        LinearProgressIndicator(progress = { (k + 1f) / qs.size }, modifier = Modifier.fillMaxWidth())
        Text("문제 ${k + 1} / ${qs.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        InfoCard(color = Blue.copy(alpha = 0.07f)) {
            Tag(q.q.type.label, Green)
            Row(verticalAlignment = Alignment.Top) {
                Text(q.q.en, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                SpeakButton(q.q.en, "exam-$k")
            }
            if (showKo) Muted(q.q.ko)
            TextButton(onClick = { showKo = !showKo }) { Text(if (showKo) "해석 숨기기" else "해석 보기") }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MicButton(stt.listening) {
                if (stt.listening) stt.stop() else {
                    app.tts.stop()
                    current = null
                    stt.start(continuous = true) { text -> current = text }
                }
            }
            Text(
                when {
                    stt.listening -> "● 답변 중 ${mmss(elapsed)}"
                    current != null -> "답변 완료 (${Scoring.tokens(current ?: "").size}단어)"
                    else -> "버튼을 누르고 답하세요"
                },
                color = if (stt.listening) Red else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (stt.listening) LiveTranscript(stt.liveText)
        current?.let { LiveTranscript(it, "(인식된 내용 없음)") }
        ErrorText(stt.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (current != null) OutlinedButton(onClick = { current = null }, modifier = Modifier.weight(1f)) { Text("다시 답하기") }
            Button(
                onClick = {
                    stt.cancel()
                    answers.add(current ?: "")
                    if (k == qs.size - 1) finished = true else k++
                },
                enabled = !stt.listening,
                modifier = Modifier.weight(1f),
            ) { Text(if (current == null) "건너뛰기" else if (k == qs.size - 1) "제출하고 결과 보기" else "다음 문제") }
        }
    }
}
