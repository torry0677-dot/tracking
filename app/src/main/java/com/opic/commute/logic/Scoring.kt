package com.opic.commute.logic

import kotlin.math.min
import kotlin.math.roundToInt

/** 말한 내용과 스크립트를 비교하는 채점 로직 (기기 안에서만 계산합니다) */
object Scoring {

    private val contractions = mapOf(
        "i'm" to "i am", "it's" to "it is", "that's" to "that is", "there's" to "there is",
        "i'd" to "i would", "i'll" to "i will", "don't" to "do not", "didn't" to "did not",
        "can't" to "can not", "cannot" to "can not", "we're" to "we are", "you're" to "you are",
        "isn't" to "is not", "wasn't" to "was not", "i've" to "i have", "let's" to "let us",
        "won't" to "will not", "doesn't" to "does not", "they're" to "they are", "what's" to "what is",
        "he's" to "he is", "she's" to "she is", "we've" to "we have", "aren't" to "are not",
        "weren't" to "were not", "haven't" to "have not", "we'll" to "we will", "you'll" to "you will",
        "here's" to "here is", "where's" to "where is", "how's" to "how is", "we'd" to "we would",
        "okay" to "ok",
    )

    private val numbers = mapOf(
        "1" to "one", "2" to "two", "3" to "three", "4" to "four", "5" to "five", "6" to "six",
        "7" to "seven", "8" to "eight", "9" to "nine", "10" to "ten", "11" to "eleven", "12" to "twelve",
        "20" to "twenty", "30" to "thirty", "40" to "forty", "50" to "fifty",
        "1st" to "first", "2nd" to "second", "3rd" to "third", "12th" to "twelfth",
    )

    val stopwords: Set<String> = setOf(
        "a", "an", "the", "and", "or", "but", "so", "to", "of", "in", "on", "at", "for", "with",
        "is", "am", "are", "was", "were", "be", "been", "it", "i", "my", "me", "we", "our", "us",
        "you", "your", "he", "she", "they", "them", "their", "this", "that", "there", "these", "those",
        "do", "does", "did", "not", "have", "has", "had", "will", "would", "can", "could", "as",
        "from", "by", "about", "um", "uh", "like", "very", "really", "just", "also", "then", "if",
        "what", "which", "who", "when", "how", "its", "all", "some", "any", "one",
    )

    /** 소문자화, 축약형 풀기, 숫자→단어, 문장부호 제거 */
    fun tokens(text: String): List<String> {
        val cleaned = text.lowercase()
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace(Regex("[^a-z0-9' ]"), " ")
        val out = ArrayList<String>()
        for (raw in cleaned.split(' ')) {
            val w = raw.trim('\'')
            if (w.isEmpty()) continue
            val expanded = contractions[w] ?: numbers[w]
            if (expanded != null) out.addAll(expanded.split(' ')) else out.add(w)
        }
        return out
    }

    fun contentWords(text: String): List<String> =
        tokens(text).filter { it !in stopwords && it.length > 1 }

    /** 최장 공통 부분수열 길이 + 목표 토큰별 일치 여부 */
    fun align(target: List<String>, spoken: List<String>): BooleanArray {
        val n = target.size
        val m = spoken.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (target[i] == spoken[j]) dp[i + 1][j + 1] + 1
                else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val matched = BooleanArray(n)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            if (target[i] == spoken[j]) {
                matched[i] = true; i++; j++
            } else if (dp[i + 1][j] >= dp[i][j + 1]) i++ else j++
        }
        return matched
    }

    /** 목표 문장을 순서대로 얼마나 말했는지 (0~100) */
    fun recall(target: String, spoken: String): Int {
        val t = tokens(target)
        if (t.isEmpty()) return 0
        val matched = align(t, tokens(spoken)).count { it }
        return (100.0 * matched / t.size).roundToInt()
    }

    /** 원문 단어 단위로 맞음/틀림 표시 (화면 하이라이트용) */
    fun wordMarks(target: String, spoken: String): List<Pair<String, Boolean>> {
        val words = target.split(' ').filter { it.isNotBlank() }
        val perWord = words.map { tokens(it) }
        val flat = perWord.flatten()
        val matched = align(flat, tokens(spoken))
        var k = 0
        return words.mapIndexed { idx, w ->
            val cnt = perWord[idx].size
            val ok = cnt == 0 || (k until k + cnt).all { matched[it] }
            k += cnt
            w to ok
        }
    }

    data class SimilarResult(
        val similarity: Int,
        val hitRate: Int,
        val hits: List<String>,
        val misses: List<String>,
        val wordCount: Int,
        val lengthScore: Int,
    ) {
        /** 유사질문/시험 종합 점수 */
        val total: Int get() = (0.4 * similarity + 0.4 * hitRate + 0.2 * lengthScore).roundToInt()
    }

    fun phraseHit(phrase: String, spokenTokens: List<String>, spokenSet: Set<String>): Boolean {
        val p = tokens(phrase)
        if (p.isEmpty()) return false
        if (p.all { it in spokenSet }) {
            // 연속으로 말했거나, 핵심어가 모두 들어 있으면 적중으로 봅니다.
            return true
        }
        // 한 단어만 빠진 긴 표현은 절반 이상 + 핵심(마지막) 단어가 있으면 적중
        if (p.size >= 3) {
            val have = p.count { it in spokenSet }
            return have >= p.size - 1 && p.last() in spokenSet
        }
        return false
    }

    /**
     * 유사질문 답변 평가
     *  - 유사도: 내가 말한 핵심 단어가 스크립트와 겹치는 비율(정밀도 60%) + 스크립트 핵심 단어를 얼마나 썼는지(재현율 40%)
     *  - 적중률: 스크립트의 핵심 표현을 몇 개 사용했는지
     *  - 분량: 70단어 이상이면 만점
     */
    fun similar(spoken: String, script: String, keyPhrases: List<String>): SimilarResult {
        val spokenTokens = tokens(spoken)
        val spokenSet = spokenTokens.toSet()
        val a = contentWords(spoken).toSet()
        val b = contentWords(script).toSet()
        val inter = a.intersect(b).size
        val precision = if (a.isEmpty()) 0.0 else inter.toDouble() / a.size
        val rec = if (b.isEmpty()) 0.0 else inter.toDouble() / b.size
        val similarity = min(100, (100 * (0.6 * precision + 0.4 * min(1.0, rec * 1.5))).roundToInt())
        val hits = keyPhrases.filter { phraseHit(it, spokenTokens, spokenSet) }
        val misses = keyPhrases - hits.toSet()
        val hitRate = if (keyPhrases.isEmpty()) 0 else (100.0 * hits.size / keyPhrases.size).roundToInt()
        val wc = spokenTokens.size
        val lengthScore = min(100, (100.0 * wc / 70).roundToInt())
        return SimilarResult(similarity, hitRate, hits, misses, wc, lengthScore)
    }

    // ---------- 시험 등급 ----------

    data class Level(val code: String, val title: String, val desc: String)

    fun level(score: Int): Level = when {
        score >= 90 -> Level("AL", "Advanced Low", "어떤 주제든 시제와 연결어를 자유롭게 쓰며 길고 논리적으로 말하는 수준입니다.")
        score >= 80 -> Level("IH", "Intermediate High", "대부분의 질문에 문단 단위로 답하고, 경험·비교 질문도 자연스럽게 풀어내는 수준입니다.")
        score >= 70 -> Level("IM3", "Intermediate Mid 3", "익숙한 주제는 길게 말하지만, 낯선 변형 질문에서 문장이 짧아지는 수준입니다.")
        score >= 60 -> Level("IM2", "Intermediate Mid 2", "외운 표현을 상황에 맞게 바꿔 쓸 수 있지만, 연결어와 디테일이 아직 부족한 수준입니다.")
        score >= 50 -> Level("IM1", "Intermediate Mid 1", "기본 문장으로 의사를 전달하지만, 답변이 짧고 같은 표현을 반복하는 수준입니다.")
        score >= 40 -> Level("IL", "Intermediate Low", "짧은 문장 위주로 답하고, 질문이 바뀌면 할 말을 찾기 어려워하는 수준입니다.")
        score >= 25 -> Level("NH", "Novice High", "단어와 짧은 구 위주로 말하는 단계입니다. 스크립트 암기부터 차근차근 늘려 가세요.")
        else -> Level("NM", "Novice Mid", "아직 문장으로 말하기가 어려운 단계입니다. 스크립트 듣기와 따라하기를 많이 해 보세요.")
    }

    private val connectors = listOf(
        "first", "then", "after that", "also", "because", "so", "but", "however", "for example",
        "to be honest", "actually", "finally", "overall", "that's why", "in addition", "on the other hand",
        "anyway", "luckily", "unfortunately", "these days", "in the past",
    )

    private val pastMarkers = setOf(
        "was", "were", "went", "had", "did", "took", "saw", "got", "made", "came", "felt", "thought",
        "ago", "last", "yesterday", "bought", "ate", "met", "found", "told", "said", "left", "lost",
    )

    fun connectorsUsed(text: String): List<String> {
        val joined = " " + tokens(text).joinToString(" ") + " "
        return connectors.filter { c -> joined.contains(" " + tokens(c).joinToString(" ") + " ") }
    }

    fun usesPastTense(text: String): Boolean {
        val t = tokens(text)
        val count = t.count { it in pastMarkers || (it.length > 4 && it.endsWith("ed")) }
        return count >= 2
    }
}
