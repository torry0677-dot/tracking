package com.opic.commute.data

/** 기승전결 단계 */
enum class Part(val label: String, val desc: String) {
    GI("기", "도입: 질문에 대한 한 줄 답과 배경"),
    SEUNG("승", "전개: 구체적인 사실과 디테일"),
    JEON("전", "전환: 에피소드, 문제, 반전, 감정"),
    GYEOL("결", "마무리: 요약과 느낌, 앞으로의 계획"),
}

/** OPIc 질문 유형 */
enum class QType(val label: String) {
    INTRO("자기소개"),
    DESCRIBE("묘사"),
    ROUTINE("루틴/습관"),
    EXPERIENCE("과거 경험"),
    COMPARE("비교/변화"),
    ROLEPLAY("롤플레이"),
    UNEXPECTED("돌발"),
}

data class Sentence(val en: String, val ko: String, val part: Part)

data class Vocab(val word: String, val ipa: String, val koPron: String, val meaning: String)

data class SimilarQ(val en: String, val ko: String, val tip: String, val type: QType)

data class Topic(
    val id: Int,
    val title: String,
    val category: String,
    val type: QType,
    val question: String,
    val questionKo: String,
    val sentences: List<Sentence>,
    val vocab: List<Vocab>,
    val keyPhrases: List<String>,
    val similar: List<SimilarQ>,
) {
    val fullScript: String get() = sentences.joinToString(" ") { it.en }
}

/** 학습 활동 종류 (성취도 '학습유형별' 집계에 사용) */
enum class Activity(val label: String) {
    STUDY("스크립트 학습"),
    BLANK("빈칸 채우기"),
    ORDER("순서 맞추기"),
    SPEAK("말하기"),
    FOLLOW("따라하기"),
    SIMILAR("유사질문"),
    MASTERY("완벽암기"),
    EXAM("토요일 시험"),
}
