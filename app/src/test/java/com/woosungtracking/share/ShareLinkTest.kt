package com.woosungtracking.share

import com.woosungtracking.data.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareLinkTest {
    private fun pt(lat: Double, lng: Double, alt: Double? = null) =
        TrackPoint(hikeId = 1, time = 0, lat = lat, lng = lng, altitude = alt, speed = null, accuracy = null)

    @Test
    fun encodesPathLikeGooglePolyline() {
        val pts = listOf(pt(38.5, -120.2), pt(40.7, -120.95), pt(43.252, -126.453))
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", ShareLink.encodePath(pts))
    }

    @Test
    fun simplifyKeepsEndpointsAndDropsStraightLinePoints() {
        val pts = (0..100).map { pt(37.5 + it * 0.0001, 127.0) }
        val s = ShareLink.simplify(pts)
        assertEquals(2, s.size)
        assertEquals(pts.first(), s.first())
        assertEquals(pts.last(), s.last())
    }
}
