package com.opic.commute.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opic.commute.data.Activity
import com.opic.commute.data.Content
import com.opic.commute.data.QType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun StatsScreen() {
    val app = LocalApp.current
    val store = app.store
    val records = store.records.toList()
    val today = LocalDate.now()

    ScreenFrame("8. 성취도") {
        // 요약
        InfoCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("🔥 ${store.currentStreak()}일", "연속 학습")
                Stat("${store.bestStreak()}일", "최장 연속")
                Stat("${store.studyDates().size}일", "총 학습일")
                Stat("${store.mastered.size}/${Content.topics.size}", "마스터")
            }
        }
        if (records.isEmpty()) {
            Muted("아직 학습 기록이 없어요. 오늘의 주제부터 시작해 보세요!")
            return@ScreenFrame
        }

        // 일별
        SectionTitle("일별 점수 (최근 14일)")
        InfoCard {
            val days = (13 downTo 0).map { today.minusDays(it.toLong()) }
            val byDay = records.groupBy { it.date }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { d ->
                    val recs = byDay[d].orEmpty()
                    val avg = if (recs.isEmpty()) 0 else recs.sumOf { it.score } / recs.size
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (recs.isNotEmpty()) Text("$avg", fontSize = 9.sp, color = scoreColor(avg))
                        Box(
                            Modifier
                                .width(14.dp)
                                .height((avg.coerceAtLeast(if (recs.isEmpty()) 0 else 4) * 0.9).dp)
                                .background(if (recs.isEmpty()) Color.Transparent else scoreColor(avg), RoundedCornerShape(3.dp)),
                        )
                        Text("${d.dayOfMonth}", fontSize = 10.sp, color = if (d == today) Blue else Color.Gray, fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            val todayRecs = byDay[today].orEmpty()
            Muted("오늘: ${todayRecs.size}회 학습" + if (todayRecs.isNotEmpty()) " · 평균 ${todayRecs.sumOf { it.score } / todayRecs.size}점" else "")
        }

        // 주제별
        SectionTitle("주제별 점수")
        InfoCard {
            Content.topics.forEach { t ->
                val recs = records.filter { it.topicId == t.id && it.activity != Activity.STUDY }
                val avg = if (recs.isEmpty()) null else recs.sumOf { it.score } / recs.size
                ScoreBar(t.title + if (store.mastered.containsKey(t.id)) "  🏆" else "", avg)
            }
            Muted("스크립트 학습(완료 체크)은 점수 평균에서 제외됩니다.")
        }

        // 질문 유형별
        SectionTitle("질문 유형별 점수")
        InfoCard {
            QType.entries.forEach { qt ->
                val ids = Content.topics.filter { it.type == qt }.map { it.id }.toSet()
                val recs = records.filter { it.topicId in ids && it.activity != Activity.STUDY }
                if (ids.isNotEmpty()) ScoreBar(qt.label, if (recs.isEmpty()) null else recs.sumOf { it.score } / recs.size)
            }
        }

        // 학습 유형별
        SectionTitle("학습 유형별 점수")
        InfoCard {
            Activity.entries.filter { it != Activity.STUDY }.forEach { a ->
                val recs = records.filter { it.activity == a }
                ScoreBar("${a.label} (${recs.size}회)", if (recs.isEmpty()) null else recs.sumOf { it.score } / recs.size)
            }
        }

        // 시험
        if (store.exams.isNotEmpty()) {
            SectionTitle("토요일 시험 기록")
            InfoCard {
                store.exams.reversed().take(8).forEach { e ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${e.date} (${koDay(e.date)})")
                        Text("${e.score}점 · ${e.level}", fontWeight = FontWeight.Bold, color = scoreColor(e.score))
                    }
                }
            }
        }

        // 복습
        SectionTitle("복습 일정")
        Muted("배운 날로부터 1 → 3 → 7 → 14 → 30일 간격으로 복습하면 오래 기억돼요.")
        InfoCard {
            store.reviewPlan().forEach { r ->
                val dd = ChronoUnit.DAYS.between(today, r.next)
                val label = when {
                    dd < 0 -> "${-dd}일 지남"
                    dd == 0L -> "오늘"
                    else -> "D-$dd"
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.topic.title, fontWeight = FontWeight.SemiBold)
                        Muted("마지막 학습 ${r.lastStudied} · ${r.stage}회차 · 복습일 ${r.next} (${koDay(r.next)})")
                    }
                    Text(label, color = if (dd <= 0) Red else Blue, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    TextButton(onClick = { app.go(Screen.Hub(r.topic.id)) }) { Text("복습") }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
