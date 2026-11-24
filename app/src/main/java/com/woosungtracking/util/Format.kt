package com.woosungtracking.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Format {
    fun distance(m: Double): String =
        if (m < 1000) "${m.roundToInt()} m" else String.format(Locale.KOREA, "%.2f km", m / 1000)

    fun duration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.KOREA, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.KOREA, "%02d:%02d", m, s)
    }

    fun altitude(m: Double): String = "${m.roundToInt()} m"

    fun speedKmh(mps: Float): String = String.format(Locale.KOREA, "%.1f km/h", mps * 3.6f)

    /** 평균 페이스 (분/km) */
    fun pace(distanceM: Double, ms: Long): String {
        if (distanceM < 50 || ms <= 0) return "-"
        val secPerKm = (ms / 1000.0) / (distanceM / 1000.0)
        val min = (secPerKm / 60).toInt()
        val sec = (secPerKm % 60).roundToInt().coerceAtMost(59)
        return String.format(Locale.KOREA, "%d'%02d\"/km", min, sec)
    }

    fun date(epochMs: Long): String =
        SimpleDateFormat("yyyy.MM.dd (E) HH:mm", Locale.KOREA).format(Date(epochMs))
}
