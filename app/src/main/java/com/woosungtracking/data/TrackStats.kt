package com.woosungtracking.data

import android.location.Location

/** 고도 누적 상승 계산용 (GPS 고도 잡음을 거르기 위한 문턱값) */
class ElevationGainCounter(private val thresholdM: Double = 3.0) {
    private var ref: Double? = null
    var gain: Double = 0.0
        private set

    fun add(altitude: Double?) {
        if (altitude == null) return
        val r = ref
        when {
            r == null -> ref = altitude
            altitude >= r + thresholdM -> { gain += altitude - r; ref = altitude }
            altitude <= r - thresholdM -> ref = altitude
        }
    }
}

data class Summary(
    val distanceM: Double,
    val elevationGainM: Double,
    val maxAltitudeM: Double?,
    val minAltitudeM: Double?,
)

object TrackStats {
    fun distanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val out = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, out)
        return out[0].toDouble()
    }

    fun summarize(points: List<TrackPoint>): Summary {
        var dist = 0.0
        val gain = ElevationGainCounter()
        for (i in points.indices) {
            val p = points[i]
            if (i > 0 && !p.segmentStart) {
                val q = points[i - 1]
                dist += distanceBetween(q.lat, q.lng, p.lat, p.lng)
            }
            gain.add(p.altitude)
        }
        val alts = points.mapNotNull { it.altitude }
        return Summary(dist, gain.gain, alts.maxOrNull(), alts.minOrNull())
    }

    /** 그래프용: (누적거리 m, 고도 m) */
    fun elevationProfile(points: List<TrackPoint>): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>(points.size)
        var dist = 0.0
        points.forEachIndexed { i, p ->
            if (i > 0 && !p.segmentStart) {
                val q = points[i - 1]
                dist += distanceBetween(q.lat, q.lng, p.lat, p.lng)
            }
            p.altitude?.let { out += dist to it }
        }
        return out
    }
}
