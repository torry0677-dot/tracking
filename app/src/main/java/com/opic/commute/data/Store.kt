package com.opic.commute.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

data class Record(val date: LocalDate, val topicId: Int, val activity: Activity, val score: Int)

data class ExamResult(val date: LocalDate, val score: Int, val level: String, val count: Int)

data class ReviewItem(val topic: Topic, val lastStudied: LocalDate, val next: LocalDate, val stage: Int)

/** 학습 기록 저장소 (기기 안 SharedPreferences) */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("opic_progress", Context.MODE_PRIVATE)

    val records = mutableStateListOf<Record>()
    val exams = mutableStateListOf<ExamResult>()
    val mastered = mutableStateMapOf<Int, LocalDate>()

    var ttsRate by mutableStateOf(prefs.getFloat("ttsRate", 0.9f))
        private set
    var masteryThreshold by mutableStateOf(prefs.getInt("masteryThreshold", 80))
        private set
    var pauseSec by mutableStateOf(prefs.getInt("pauseSec", 1))
        private set

    init {
        runCatching {
            val arr = JSONArray(prefs.getString("records", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val act = runCatching { Activity.valueOf(o.getString("a")) }.getOrNull() ?: continue
                records.add(Record(LocalDate.parse(o.getString("d")), o.getInt("t"), act, o.getInt("s")))
            }
        }
        runCatching {
            val arr = JSONArray(prefs.getString("exams", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                exams.add(ExamResult(LocalDate.parse(o.getString("d")), o.getInt("s"), o.getString("l"), o.getInt("c")))
            }
        }
        runCatching {
            val o = JSONObject(prefs.getString("mastered", "{}") ?: "{}")
            o.keys().forEach { k -> mastered[k.toInt()] = LocalDate.parse(o.getString(k)) }
        }
    }

    private fun save() {
        val arr = JSONArray()
        records.forEach {
            arr.put(JSONObject().put("d", it.date.toString()).put("t", it.topicId).put("a", it.activity.name).put("s", it.score))
        }
        val ex = JSONArray()
        exams.forEach {
            ex.put(JSONObject().put("d", it.date.toString()).put("s", it.score).put("l", it.level).put("c", it.count))
        }
        val m = JSONObject()
        mastered.forEach { (k, d) -> m.put(k.toString(), d.toString()) }
        prefs.edit()
            .putString("records", arr.toString())
            .putString("exams", ex.toString())
            .putString("mastered", m.toString())
            .apply()
    }

    fun add(topicId: Int, activity: Activity, score: Int) {
        records.add(Record(LocalDate.now(), topicId, activity, score.coerceIn(0, 100)))
        save()
    }

    fun addExam(score: Int, level: String, count: Int) {
        exams.add(ExamResult(LocalDate.now(), score, level, count))
        save()
    }

    fun setMastered(topicId: Int) {
        mastered[topicId] = LocalDate.now()
        save()
    }

    fun updateSettings(rate: Float = ttsRate, threshold: Int = masteryThreshold, pause: Int = pauseSec) {
        ttsRate = rate
        masteryThreshold = threshold
        pauseSec = pause
        prefs.edit().putFloat("ttsRate", rate).putInt("masteryThreshold", threshold).putInt("pauseSec", pause).apply()
    }

    fun best(topicId: Int, activity: Activity): Int? =
        records.filter { it.topicId == topicId && it.activity == activity }.maxOfOrNull { it.score }

    // ---------- 연속 학습일 ----------

    fun studyDates(): Set<LocalDate> = records.map { it.date }.toSet()

    fun currentStreak(today: LocalDate = LocalDate.now()): Int {
        val dates = studyDates()
        var d = if (today in dates) today else today.minusDays(1)
        var n = 0
        while (d in dates) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    fun bestStreak(): Int {
        val sorted = studyDates().sorted()
        var best = 0
        var cur = 0
        var prev: LocalDate? = null
        for (d in sorted) {
            cur = if (prev != null && ChronoUnit.DAYS.between(prev, d) == 1L) cur + 1 else 1
            best = maxOf(best, cur)
            prev = d
        }
        return best
    }

    // ---------- 복습 일정 (간격 반복: 1, 3, 7, 14, 30일) ----------

    fun reviewPlan(): List<ReviewItem> {
        val intervals = listOf(1L, 3L, 7L, 14L, 30L)
        return records.filter { it.activity != Activity.EXAM }
            .groupBy { it.topicId }
            .mapNotNull { (id, recs) ->
                val topic = Content.topics.firstOrNull { it.id == id } ?: return@mapNotNull null
                val days = recs.map { it.date }.distinct().sorted()
                val stage = (days.size - 1).coerceIn(0, intervals.size - 1)
                val last = days.last()
                ReviewItem(topic, last, last.plusDays(intervals[stage]), stage + 1)
            }
            .sortedBy { it.next }
    }

    companion object {
        /** 짝수 주 = A세트(1~5), 홀수 주 = B세트(6~10) */
        fun weekSet(date: LocalDate = LocalDate.now()): Int = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % 2

        fun topicsOfSet(set: Int): List<Topic> = Content.topics.filter { (it.id - 1) / 5 == set }

        /** 월~금이면 오늘의 주제, 주말이면 null */
        fun todayTopic(date: LocalDate = LocalDate.now()): Topic? {
            val dow = date.dayOfWeek
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return null
            return topicsOfSet(weekSet(date))[dow.value - 1]
        }
    }
}
