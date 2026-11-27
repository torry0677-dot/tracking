package com.opic.commute.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opic.commute.data.Activity
import com.opic.commute.data.Content
import com.opic.commute.data.Topic
import com.opic.commute.logic.Scoring
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

// ---------------- 1단계: 빈칸 채우기 ----------------

data class BlankQ(val display: String, val answer: String, val options: List<String>, val ko: String, val full: String)

private fun core(w: String) = w.trim { !it.isLetterOrDigit() && it != '\'' && it != '-' }.trim('\'', '-')

fun buildBlanks(t: Topic, rnd: Random): List<BlankQ> {
    val vocabWords = t.vocab.flatMap { it.word.lowercase().split(" ") }.filter { it.length >= 4 }.toSet()
    val pool = t.sentences.flatMap { s -> s.en.split(" ").map { core(it).lowercase() } }
        .filter { it.length >= 4 && it !in Scoring.stopwords && it.none(Char::isDigit) }
        .distinct()
    return t.sentences.map { s ->
        val words = s.en.split(" ")
        val cores = words.map { core(it) }
        val candidates = cores.indices.filter { i ->
            val c = cores[i].lowercase()
            c.length >= 4 && c !in Scoring.stopwords && c.none(Char::isDigit)
        }
        val preferred = candidates.filter { cores[it].lowercase().split("-").any { p -> p in vocabWords } || cores[it].lowercase() in vocabWords }
        val idx = when {
            preferred.isNotEmpty() -> preferred[rnd.nextInt(preferred.size)]
            candidates.isNotEmpty() -> candidates.sortedByDescending { cores[it].length }.take(3).let { it[rnd.nextInt(it.size)] }
            else -> cores.indices.maxByOrNull { cores[it].length } ?: 0
        }
        val answer = cores[idx].lowercase()
        val display = words.mapIndexed { j, w -> if (j == idx && cores[j].isNotEmpty()) w.replace(cores[j], "_______") else w }.joinToString(" ")
        val distractors = pool.filter { it != answer }
            .sortedBy { abs(it.length - answer.length) + rnd.nextInt(4) }
            .take(6).shuffled(rnd).take(3)
        BlankQ(display, answer, (distractors + answer).shuffled(rnd), s.ko, s.en)
    }
}

@Composable
fun BlankScreen(topicId: Int) {
    val app = LocalApp.current
    val t = Content.topic(topicId)
    var seed by remember { mutableIntStateOf(Random.nextInt()) }
    val qs = remember(seed) { buildBlanks(t, Random(seed)) }
    var i by remember(seed) { mutableIntStateOf(0) }
    var picked by remember(seed) { mutableStateOf<String?>(null) }
    var correct by remember(seed) { mutableIntStateOf(0) }
    var done by remember(seed) { mutableStateOf(false) }

    ScreenFrame("1단계 빈칸 · ${t.title}") {
        if (done) {
            val score = (100.0 * correct / qs.size).roundToInt()
            ResultPanel(score, "빈칸", onRetry = { seed = Random.nextInt() }, onNext = { app.replace(Screen.Order(t.id)) }, nextLabel = "2단계 순서")
            return@ScreenFrame
        }
        val q = qs[i]
        LinearProgressIndicator(progress = { (i + 1f) / qs.size }, modifier = Modifier.fillMaxWidth())
        Text("${i + 1} / ${qs.size}  ·  맞힘 $correct", style = MaterialTheme.typography.labelLarge)
        InfoCard {
            Text(if (picked == null) q.display else q.full, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold)
            Muted(q.ko)
        }
        q.options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { opt ->
                    val color = when {
                        picked == null -> Blue
                        opt == q.answer -> Green
                        opt == picked -> Red
                        else -> Color.LightGray
                    }
                    Button(
                        onClick = {
                            if (picked == null) {
                                picked = opt
                                if (opt == q.answer) correct++
                                app.tts.speak(q.full, "blank-$i")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = color),
                        modifier = Modifier.weight(1f),
                    ) { Text(opt, fontSize = 16.sp) }
                }
            }
        }
        if (picked != null) {
            Text(if (picked == q.answer) "정답이에요! 🎉" else "정답: ${q.answer}", color = if (picked == q.answer) Green else Red, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeakButton(q.full, "blank-$i")
                Muted("문장 다시 듣기")
            }
            Button(onClick = {
                if (i == qs.size - 1) {
                    done = true
                    app.store.add(t.id, Activity.BLANK, (100.0 * correct / qs.size).roundToInt())
                } else {
                    i++
                    picked = null
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (i == qs.size - 1) "결과 보기" else "다음") }
        }
    }
}

// ---------------- 2단계: 순서 맞추기 ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderScreen(topicId: Int) {
    val app = LocalApp.current
    val t = Content.topic(topicId)
    var tab by remember { mutableIntStateOf(0) }
    var seed by remember { mutableIntStateOf(Random.nextInt()) }

    ScreenFrame("2단계 순서 · ${t.title}") {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0; seed = Random.nextInt() }, text = { Text("문장 순서") })
            Tab(selected = tab == 1, onClick = { tab = 1; seed = Random.nextInt() }, text = { Text("단어 배열") })
        }
        if (tab == 0) {
            SentenceOrder(t, seed, onRetry = { seed = Random.nextInt() })
        } else {
            WordOrder(t, seed, onRetry = { seed = Random.nextInt() })
        }
    }
}

@Composable
private fun SentenceOrder(t: Topic, seed: Int, onRetry: () -> Unit) {
    val app = LocalApp.current
    val n = t.sentences.size
    val shuffled = remember(seed) { t.sentences.indices.shuffled(Random(seed)) }
    val placed = remember(seed) { mutableStateListOf<Int>() }
    var wrong by remember(seed) { mutableStateOf<Int?>(null) }
    var missedCurrent by remember(seed) { mutableStateOf(false) }
    var firstTry by remember(seed) { mutableIntStateOf(0) }
    var showKo by remember(seed) { mutableStateOf(true) }
    var saved by remember(seed) { mutableStateOf(false) }

    if (placed.size == n) {
        val score = (100.0 * firstTry / n).roundToInt()
        LaunchedEffect(seed) {
            if (!saved) {
                saved = true
                app.store.add(t.id, Activity.ORDER, score)
            }
        }
        ResultPanel(score, "문장 순서", onRetry = onRetry, onNext = { app.replace(Screen.Speak(t.id)) }, nextLabel = "3단계 말하기")
    } else {
        Muted("기승전결 흐름을 떠올리며 ${placed.size + 1}번째 문장을 누르세요. 한 번에 맞힌 문장 수로 채점합니다.")
        FilterChip(selected = showKo, onClick = { showKo = !showKo }, label = { Text("해석 보기") })
    }
    placed.forEachIndexed { k, idx ->
        val s = t.sentences[idx]
        Row(verticalAlignment = Alignment.CenterVertically) {
            PartBadge(s.part)
            HSpace(8)
            Text("${k + 1}. ${s.en}", color = Green, modifier = Modifier.weight(1f))
        }
    }
    if (placed.size < n) {
        shuffled.filter { it !in placed }.forEach { idx ->
            val s = t.sentences[idx]
            InfoCard(
                color = if (wrong == idx) Red.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                onClick = {
                    if (idx == placed.size) {
                        if (!missedCurrent) firstTry++
                        missedCurrent = false
                        wrong = null
                        placed.add(idx)
                        app.tts.speak(s.en, "ord-$idx")
                    } else {
                        wrong = idx
                        missedCurrent = true
                    }
                },
            ) {
                Text(s.en, fontSize = 16.sp)
                if (showKo) Muted(s.ko)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordOrder(t: Topic, seed: Int, onRetry: () -> Unit) {
    val app = LocalApp.current
    val rnd = remember(seed) { Random(seed) }
    val picks = remember(seed) { t.sentences.indices.shuffled(rnd).take(5).sorted() }
    var k by remember(seed) { mutableIntStateOf(0) }
    var goodSentences by remember(seed) { mutableIntStateOf(0) }
    var saved by remember(seed) { mutableStateOf(false) }

    if (k >= picks.size) {
        val score = (100.0 * goodSentences / picks.size).roundToInt()
        LaunchedEffect(seed) {
            if (!saved) {
                saved = true
                app.store.add(t.id, Activity.ORDER, score)
            }
        }
        ResultPanel(score, "단어 배열", onRetry = onRetry, onNext = { app.replace(Screen.Speak(t.id)) }, nextLabel = "3단계 말하기")
        return
    }
    val s = t.sentences[picks[k]]
    val words = remember(seed, k) { s.en.split(" ") }
    val order = remember(seed, k) { words.indices.shuffled(rnd) }
    val placed = remember(seed, k) { mutableStateListOf<Int>() }
    var mistakes by remember(seed, k) { mutableIntStateOf(0) }
    var wrong by remember(seed, k) { mutableStateOf<Int?>(null) }

    Text("${k + 1} / ${picks.size} 문장  ·  실수 1번까지 정답 처리", style = MaterialTheme.typography.labelLarge)
    InfoCard {
        Muted(s.ko)
        Text(
            if (placed.isEmpty()) "단어를 순서대로 누르세요" else placed.joinToString(" ") { words[it] },
            fontSize = 18.sp,
            lineHeight = 26.sp,
            color = if (placed.isEmpty()) Color.Gray else Green,
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (placed.size < words.size) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            order.filter { it !in placed }.forEach { wi ->
                AssistChip(
                    onClick = {
                        // 같은 단어가 여러 번 나오는 경우도 정답으로 인정
                        val expected = words[placed.size]
                        if (wi == placed.size || words[wi] == expected) {
                            placed.add(wi)
                            wrong = null
                        } else {
                            mistakes++
                            wrong = wi
                        }
                    },
                    label = { Text(words[wi], fontSize = 16.sp, color = if (wrong == wi) Red else Color.Unspecified) },
                )
            }
        }
        Muted("실수 $mistakes")
    } else {
        Text(if (mistakes <= 1) "잘했어요! ✓" else "실수 ${mistakes}번 — 한 번 더 들어 보세요.", color = if (mistakes <= 1) Green else Orange, fontWeight = FontWeight.Bold)
        LaunchedEffect(seed, k) { app.tts.speak(s.en, "wo-$k") }
        Button(onClick = {
            if (mistakes <= 1) goodSentences++
            k++
        }, modifier = Modifier.fillMaxWidth()) { Text(if (k == picks.size - 1) "결과 보기" else "다음 문장") }
    }
}

// ---------------- 3단계: 말하기 ----------------

@Composable
fun SpeakScreen(topicId: Int) {
    val app = LocalApp.current
    val stt = app.stt
    val t = Content.topic(topicId)
    val ask = rememberMicPermission()
    var i by remember { mutableIntStateOf(0) }
    val best = remember { mutableStateMapOf<Int, Int>() }
    var heard by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }

    ScreenFrame("3단계 말하기 · ${t.title}") {
        if (done) {
            val score = if (best.isEmpty()) 0 else best.values.sum() / t.sentences.size
            ResultPanel(score, "말하기", onRetry = {
                best.clear(); i = 0; heard = null; done = false; round++
            }, onNext = { app.replace(Screen.Similar(t.id)) }, nextLabel = "유사질문 적용")
            t.sentences.forEachIndexed { k, s ->
                ScoreBar("${k + 1}. ${s.en.take(32)}…", best[k])
            }
            return@ScreenFrame
        }
        val s = t.sentences[i]
        LinearProgressIndicator(progress = { (i + 1f) / t.sentences.size }, modifier = Modifier.fillMaxWidth())
        Text("${i + 1} / ${t.sentences.size}", style = MaterialTheme.typography.labelLarge)
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PartBadge(s.part)
                HSpace(8)
                Text(s.ko, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            if (hint) Text(s.en.split(" ").joinToString(" ") { w -> w.take(1) + "_".repeat((w.length - 1).coerceIn(0, 6)) }, color = Blue)
            TextButton(onClick = { hint = !hint }) { Text(if (hint) "힌트 숨기기" else "힌트: 첫 글자 보기") }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MicButton(stt.listening) {
                if (stt.listening) stt.stop() else ask {
                    heard = null
                    stt.start(continuous = false) { text ->
                        heard = text
                        val sc = Scoring.recall(s.en, text)
                        if (sc > (best[i] ?: -1)) best[i] = sc
                    }
                }
            }
            Muted(if (stt.listening) "듣고 있어요… 말을 마치면 자동으로 채점돼요" else "버튼을 누르고 영어로 말하세요")
        }
        if (stt.listening) LiveTranscript(stt.liveText)
        ErrorText(stt.error)
        heard?.let { h ->
            val sc = Scoring.recall(s.en, h)
            InfoCard(color = scoreColor(sc).copy(alpha = 0.08f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreCircle(sc, "이번", size = 60)
                    HSpace(12)
                    Column(Modifier.weight(1f)) {
                        Muted("내가 말한 문장")
                        Text(h.ifBlank { "(인식된 내용 없음)" })
                    }
                }
                Muted("정답 (빨간 밑줄 = 빠뜨린 단어)")
                MarkedText(Scoring.wordMarks(s.en, h))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { app.tts.speak(s.en, "sp-$i") }, modifier = Modifier.weight(1f)) { Text("정답 듣기") }
            Button(onClick = {
                stt.cancel()
                if (i == t.sentences.size - 1) {
                    done = true
                    app.store.add(t.id, Activity.SPEAK, best.values.sum() / t.sentences.size)
                } else {
                    i++
                    heard = null
                    hint = false
                }
            }, modifier = Modifier.weight(1f)) { Text(if (i == t.sentences.size - 1) "결과 보기" else "다음") }
        }
    }
}

// ---------------- 선택해서 따라하기 ----------------

@Composable
fun FollowScreen(topicId: Int, preselect: Int) {
    val app = LocalApp.current
    val tts = app.tts
    val stt = app.stt
    val t = Content.topic(topicId)
    val ask = rememberMicPermission()
    val scope = rememberCoroutineScope()
    val selected = remember {
        mutableStateListOf<Int>().apply { if (preselect >= 0) add(preselect) else addAll(t.sentences.indices) }
    }
    var repeatCount by remember { mutableIntStateOf(1) }
    var job by remember { mutableStateOf<Job?>(null) }
    var current by remember { mutableIntStateOf(-1) }
    var phase by remember { mutableStateOf("") }
    var lastHeard by remember { mutableStateOf("") }
    val scores = remember { mutableStateListOf<Int>() }
    var finished by remember { mutableStateOf(false) }

    fun startSession() {
        scores.clear()
        finished = false
        job = scope.launch {
            val list = selected.sorted()
            for (idx in list) {
                repeat(repeatCount) {
                    current = idx
                    lastHeard = ""
                    phase = "🔊 잘 들어 보세요"
                    tts.speakAwait(t.sentences[idx].en, "fol-$idx-$it")
                    delay(250)
                    phase = "🎤 따라 말하세요"
                    val h = stt.listenAwait()
                    lastHeard = h
                    val sc = Scoring.recall(t.sentences[idx].en, h)
                    scores.add(sc)
                    phase = "점수 $sc"
                    delay(1800)
                }
            }
            current = -1
            job = null
            finished = true
            if (scores.isNotEmpty()) app.store.add(t.id, Activity.FOLLOW, scores.sum() / scores.size)
        }
    }

    ScreenFrame("따라하기 · ${t.title}") {
        if (job != null && current >= 0) {
            val s = t.sentences[current]
            InfoCard(color = partColor(s.part).copy(alpha = 0.1f)) {
                Text(phase, fontWeight = FontWeight.Bold, color = Blue)
                Text(s.en, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                Muted(s.ko)
                if (stt.listening) LiveTranscript(stt.liveText, "말하는 중…")
                if (lastHeard.isNotBlank()) MarkedText(Scoring.wordMarks(s.en, lastHeard))
            }
            ErrorText(stt.error)
            Button(onClick = {
                job?.cancel(); job = null; current = -1; tts.stop(); stt.cancel()
            }, colors = ButtonDefaults.buttonColors(containerColor = Red), modifier = Modifier.fillMaxWidth()) { Text("그만하기") }
            Muted("진행 ${scores.size} / ${selected.size * repeatCount}")
            return@ScreenFrame
        }
        if (finished && scores.isNotEmpty()) {
            val avg = scores.sum() / scores.size
            ResultPanel(avg, "따라하기 평균", onRetry = { startSession() }, onNext = null)
        }
        Muted("연습할 문장을 고르고 시작하세요. 원어민 음성 → 따라 말하기 → 점수가 자동으로 반복됩니다.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("반복", fontWeight = FontWeight.Bold)
            listOf(1, 2, 3).forEach { r ->
                FilterChip(selected = repeatCount == r, onClick = { repeatCount = r }, label = { Text("${r}회") })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { selected.clear(); selected.addAll(t.sentences.indices) }) { Text("전체 선택") }
            TextButton(onClick = { selected.clear() }) { Text("선택 해제") }
        }
        t.sentences.forEachIndexed { idx, s ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Checkbox(checked = idx in selected, onCheckedChange = { c -> if (c) selected.add(idx) else selected.remove(idx) })
                Column(Modifier.weight(1f)) {
                    Text("${idx + 1}. ${s.en}", fontSize = 15.sp)
                    Muted(s.ko)
                }
                SpeakButton(s.en, "fsel-$idx")
            }
        }
        Button(
            onClick = { ask { startSession() } },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("선택한 ${selected.size}문장 따라하기 시작") }
    }
}
