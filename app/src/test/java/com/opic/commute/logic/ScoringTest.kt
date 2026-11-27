package com.opic.commute.logic

import com.opic.commute.data.Content
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {

    @Test
    fun tokensExpandContractionsAndNumbers() {
        assertEquals(listOf("i", "am", "on", "the", "twelfth", "floor"), Scoring.tokens("I'm on the 12th floor."))
        assertEquals(listOf("ten", "minute", "walk"), Scoring.tokens("10-minute walk"))
    }

    @Test
    fun recallIsPerfectForExactSentence() {
        val s = "It's only a ten-minute walk from my place, so it's very convenient."
        assertEquals(100, Scoring.recall(s, "it is only a 10 minute walk from my place so it's very convenient"))
    }

    @Test
    fun recallDropsForMissingWords() {
        val s = "I usually listen to music on my smartphone."
        val r = Scoring.recall(s, "I listen to music")
        assertTrue(r in 40..70)
    }

    @Test
    fun wordMarksFlagMissingWords() {
        val marks = Scoring.wordMarks("I like pop music.", "I like music")
        assertEquals(listOf(true, true, false, true), marks.map { it.second })
    }

    @Test
    fun fullScriptScoresHigh() {
        val t = Content.topic(4)
        val r = Scoring.recall(t.fullScript, t.fullScript)
        assertEquals(100, r)
        val sim = Scoring.similar(t.fullScript, t.fullScript, t.keyPhrases)
        assertEquals(100, sim.hitRate)
        assertTrue(sim.total >= 90)
    }

    @Test
    fun unrelatedAnswerScoresLow() {
        val t = Content.topic(4)
        val sim = Scoring.similar("I don't know anything about that.", t.fullScript, t.keyPhrases)
        assertTrue(sim.total < 25)
    }

    @Test
    fun allTopicsAreComplete() {
        assertEquals(10, Content.topics.size)
        Content.topics.forEach {
            assertTrue("${it.title} 문장 수", it.sentences.size >= 10)
            assertEquals(3, it.similar.size)
            assertTrue(it.vocab.size >= 5)
            // 핵심 표현은 스크립트 안에서 적중해야 합니다.
            val sim = Scoring.similar(it.fullScript, it.fullScript, it.keyPhrases)
            assertEquals("${it.title} 핵심 표현 ${sim.misses}", 100, sim.hitRate)
        }
    }

    @Test
    fun pastTenseDetection() {
        assertTrue(Scoring.usesPastTense("Last year I went to Jeju and it was great"))
        assertFalse(Scoring.usesPastTense("I usually go to the park"))
    }
}
