package com.woosungtracking

import android.app.Application
import com.naver.maps.map.NaverMapSdk
import com.woosungtracking.data.AppDatabase
import com.woosungtracking.data.TrackStats
import com.woosungtracking.tracking.TrackingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

object MapAuth {
    /** 네이버 지도 인증 실패 메시지 (없으면 null) */
    val error = MutableStateFlow<String?>(null)
    val hasKey: Boolean get() = BuildConfig.NAVER_CLIENT_ID.isNotBlank()
}

class WoosungApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val sdk = NaverMapSdk.getInstance(this)
        if (MapAuth.hasKey) {
            sdk.client = NaverMapSdk.NcpKeyClient(BuildConfig.NAVER_CLIENT_ID)
        } else {
            MapAuth.error.value = "이 앱에는 아직 네이버 지도 키가 없어 지도가 보이지 않아요."
        }
        sdk.onAuthFailedListener = NaverMapSdk.OnAuthFailedListener { e ->
            if (MapAuth.hasKey) {
                val code = e.errorCode
                MapAuth.error.value = when (code) {
                    "401" -> "네이버 지도 키가 맞지 않아요. 콘솔에 패키지 이름 com.woosungtracking 이 등록됐는지 확인해 주세요. [401]"
                    "429" -> "네이버 콘솔에서 Dynamic Map 사용을 켜 주세요. [429]"
                    else -> "네이버 지도 인증 실패 [$code]"
                }
            }
        }
        recoverUnfinishedHikes()
    }

    /** 앱이 강제 종료되어 마무리되지 못한 산행을 저장된 지점으로 정리합니다. */
    private fun recoverUnfinishedHikes() {
        if (TrackingRepository.state.value.recording) return
        appScope.launch {
            val dao = AppDatabase.get(this@WoosungApp).hikeDao()
            for (hike in dao.getUnfinishedHikes()) {
                val points = dao.getPoints(hike.id)
                if (points.size < 2 && dao.getPhotos(hike.id).isEmpty()) {
                    dao.deleteHike(hike.id)
                    continue
                }
                val s = TrackStats.summarize(points)
                val end = points.lastOrNull()?.time ?: hike.startTime
                dao.updateHike(
                    hike.copy(
                        endTime = end,
                        distanceM = s.distanceM,
                        movingTimeMs = end - hike.startTime,
                        elevationGainM = s.elevationGainM,
                        maxAltitudeM = s.maxAltitudeM,
                        minAltitudeM = s.minAltitudeM,
                    ),
                )
            }
        }
    }
}
