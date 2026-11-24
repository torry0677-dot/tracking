package com.woosungtracking.share

import android.util.Base64
import com.woosungtracking.BuildConfig
import com.woosungtracking.data.Hike
import com.woosungtracking.data.TrackPoint
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * 서버 없이 공유할 수 있도록, 경로와 요약 정보를 압축해 링크의 # 뒷부분에 담습니다.
 * 링크를 열면 docs/index.html (GitHub Pages) 이 이를 풀어 지도에 그립니다.
 * # 뒷부분은 웹 서버로 전송되지 않습니다.
 */
object ShareLink {
    private const val MAX_POINTS = 1200

    fun build(hike: Hike, points: List<TrackPoint>, baseUrl: String = BuildConfig.SHARE_BASE_URL): String {
        val simplified = simplify(points)
        val json = JSONObject().apply {
            put("v", 1)
            put("t", hike.title)
            put("s", hike.startTime / 1000)
            put("d", hike.distanceM.roundToInt())
            put("m", hike.movingTimeMs / 1000)
            put("g", hike.elevationGainM.roundToInt())
            hike.maxAltitudeM?.let { put("x", it.roundToInt()) }
            put("p", encodePath(simplified))
            put("a", encodeAltitudes(simplified))
        }
        val packed = deflate(json.toString().toByteArray(Charsets.UTF_8))
        val b64 = Base64.encodeToString(packed, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        return baseUrl.trimEnd('/') + "/#" + b64
    }

    private fun deflate(input: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true) // raw deflate (브라우저 'deflate-raw')
        d.setInput(input)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) {
            val n = d.deflate(buf)
            out.write(buf, 0, n)
        }
        d.end()
        return out.toByteArray()
    }

    // ---- 구글 polyline 방식 인코딩 ----
    private fun encodeValue(value: Long, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else (value shl 1)
        while (v >= 0x20) {
            sb.append(((0x20L or (v and 0x1f)) + 63).toInt().toChar())
            v = v shr 5
        }
        sb.append((v + 63).toInt().toChar())
    }

    internal fun encodePath(points: List<TrackPoint>): String {
        val sb = StringBuilder()
        var pLat = 0L
        var pLng = 0L
        for (p in points) {
            val lat = (p.lat * 1e5).roundToLong()
            val lng = (p.lng * 1e5).roundToLong()
            encodeValue(lat - pLat, sb)
            encodeValue(lng - pLng, sb)
            pLat = lat
            pLng = lng
        }
        return sb.toString()
    }

    internal fun encodeAltitudes(points: List<TrackPoint>): String {
        val sb = StringBuilder()
        var prev = 0L
        var last = points.firstNotNullOfOrNull { it.altitude } ?: 0.0
        for (p in points) {
            last = p.altitude ?: last
            val a = last.roundToLong()
            encodeValue(a - prev, sb)
            prev = a
        }
        return sb.toString()
    }

    // ---- 더글라스-포이커 단순화 ----
    internal fun simplify(points: List<TrackPoint>): List<TrackPoint> {
        if (points.size <= 2) return points
        var tol = 4.0
        var result = douglasPeucker(points, tol)
        while (result.size > MAX_POINTS) {
            tol *= 1.6
            result = douglasPeucker(points, tol)
        }
        return result
    }

    private fun douglasPeucker(points: List<TrackPoint>, tolM: Double): List<TrackPoint> {
        val lat0 = Math.toRadians(points[0].lat)
        val kx = 111_320.0 * cos(lat0)
        val ky = 110_540.0
        val xs = DoubleArray(points.size) { points[it].lng * kx }
        val ys = DoubleArray(points.size) { points[it].lat * ky }
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.size - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var maxD = 0.0
            var idx = -1
            for (i in a + 1 until b) {
                val d = perpDistance(xs[i], ys[i], xs[a], ys[a], xs[b], ys[b])
                if (d > maxD) { maxD = d; idx = i }
            }
            if (idx >= 0 && maxD > tolM) {
                keep[idx] = true
                stack.addLast(a to idx)
                stack.addLast(idx to b)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun perpDistance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))
        return abs(dy * px - dx * py + bx * ay - by * ax) / sqrt(len2)
    }
}
