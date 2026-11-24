package com.woosungtracking.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LivePoint(val lat: Double, val lng: Double, val altitude: Double?)

data class TrackingState(
    val hikeId: Long? = null,
    val recording: Boolean = false,
    val paused: Boolean = false,
    val startTime: Long = 0,
    val pausedTotalMs: Long = 0,
    val pauseStartedAt: Long? = null,
    val points: List<LivePoint> = emptyList(),
    val current: LivePoint? = null,
    val distanceM: Double = 0.0,
    val speedMps: Float = 0f,
    val altitudeM: Double? = null,
    val elevationGainM: Double = 0.0,
    val maxAltitudeM: Double? = null,
    val minAltitudeM: Double? = null,
    val accuracyM: Float? = null,
    /** 방금 종료된 산행 id (화면 이동용) */
    val finishedHikeId: Long? = null,
) {
    fun movingTimeMs(now: Long = System.currentTimeMillis()): Long {
        if (!recording) return 0
        val pausedNow = pauseStartedAt?.let { now - it } ?: 0
        return (now - startTime - pausedTotalMs - pausedNow).coerceAtLeast(0)
    }
}

object TrackingRepository {
    internal val mutable = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = mutable

    fun consumeFinished() {
        mutable.value = mutable.value.copy(finishedHikeId = null)
    }
}
