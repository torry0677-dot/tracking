package com.woosungtracking.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.woosungtracking.R
import com.woosungtracking.data.AppDatabase
import com.woosungtracking.data.ElevationGainCounter
import com.woosungtracking.data.Hike
import com.woosungtracking.data.TrackPoint
import com.woosungtracking.data.TrackStats
import com.woosungtracking.ui.MainActivity
import com.woosungtracking.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 산행 기록 중 화면이 꺼져 있어도 GPS를 계속 받기 위한 포그라운드 서비스.
 */
class TrackingService : LifecycleService() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_STOP = "stop"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1001

        /** 정확도가 이보다 나쁜 지점은 경로에 넣지 않습니다. */
        private const val MAX_ACCURACY_M = 35f
        /** 이보다 짧게 움직이면 제자리 잡음으로 봅니다. */
        private const val MIN_STEP_M = 2.0

        fun send(context: Context, action: String) {
            val intent = Intent(context, TrackingService::class.java).setAction(action)
            if (action == ACTION_START) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }
    }

    private lateinit var fused: FusedLocationProviderClient
    private val dbMutex = Mutex()
    private var gainCounter = ElevationGainCounter()
    private var lastAccepted: Location? = null
    private var nextIsSegmentStart = false
    private var lastNotifyAt = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { onLocation(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    private val repo get() = TrackingRepository.mutable

    private fun start() {
        if (repo.value.recording) return
        val now = System.currentTimeMillis()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("GPS 신호를 찾는 중…"),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        gainCounter = ElevationGainCounter()
        lastAccepted = null
        nextIsSegmentStart = false
        repo.value = TrackingState(recording = true, startTime = now)

        val title = SimpleDateFormat("M월 d일 산행", Locale.KOREA).format(Date(now))
        lifecycleScope.launch(Dispatchers.IO) {
            val id = dao().insertHike(Hike(title = title, startTime = now))
            repo.value = repo.value.copy(hikeId = id)
        }
        requestUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            stopSelf()
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .build()
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun pause() {
        val s = repo.value
        if (!s.recording || s.paused) return
        repo.value = s.copy(paused = true, pauseStartedAt = System.currentTimeMillis(), speedMps = 0f)
        notifyStatus(force = true)
    }

    private fun resume() {
        val s = repo.value
        if (!s.recording || !s.paused) return
        val pausedFor = s.pauseStartedAt?.let { System.currentTimeMillis() - it } ?: 0
        repo.value = s.copy(paused = false, pauseStartedAt = null, pausedTotalMs = s.pausedTotalMs + pausedFor)
        nextIsSegmentStart = true
        notifyStatus(force = true)
    }

    private fun stop() {
        fused.removeLocationUpdates(callback)
        val s = repo.value
        val now = System.currentTimeMillis()
        val moving = s.movingTimeMs(now)
        lifecycleScope.launch(Dispatchers.IO) {
            dbMutex.withLock {
                val id = s.hikeId
                if (id != null) {
                    val hike = dao().getHike(id)
                    if (hike != null) {
                        dao().updateHike(
                            hike.copy(
                                endTime = now,
                                distanceM = s.distanceM,
                                movingTimeMs = moving,
                                elevationGainM = s.elevationGainM,
                                maxAltitudeM = s.maxAltitudeM,
                                minAltitudeM = s.minAltitudeM,
                            ),
                        )
                    }
                }
            }
            repo.value = TrackingState(finishedHikeId = s.hikeId)
            ServiceCompat.stopForeground(this@TrackingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun altitudeOf(loc: Location): Double? = when {
        Build.VERSION.SDK_INT >= 34 && loc.hasMslAltitude() -> loc.mslAltitudeMeters
        loc.hasAltitude() -> loc.altitude
        else -> null
    }

    private fun onLocation(loc: Location) {
        val s = repo.value
        if (!s.recording) return
        val alt = altitudeOf(loc)
        val here = LivePoint(loc.latitude, loc.longitude, alt)
        val speed = if (loc.hasSpeed()) loc.speed else 0f
        val acc = if (loc.hasAccuracy()) loc.accuracy else null

        if (s.paused || (acc != null && acc > MAX_ACCURACY_M)) {
            repo.value = s.copy(current = here, accuracyM = acc, altitudeM = alt ?: s.altitudeM)
            return
        }

        val prev = lastAccepted
        var added = 0.0
        if (prev != null) {
            val step = TrackStats.distanceBetween(prev.latitude, prev.longitude, loc.latitude, loc.longitude)
            if (step < MIN_STEP_M && !nextIsSegmentStart) {
                repo.value = s.copy(current = here, speedMps = speed, accuracyM = acc, altitudeM = alt ?: s.altitudeM)
                return
            }
            if (!nextIsSegmentStart) added = step
        }
        val segmentStart = nextIsSegmentStart
        nextIsSegmentStart = false
        lastAccepted = loc
        gainCounter.add(alt)

        repo.value = s.copy(
            current = here,
            points = s.points + here,
            distanceM = s.distanceM + added,
            speedMps = speed,
            altitudeM = alt ?: s.altitudeM,
            accuracyM = acc,
            elevationGainM = gainCounter.gain,
            maxAltitudeM = maxOfNullable(s.maxAltitudeM, alt),
            minAltitudeM = minOfNullable(s.minAltitudeM, alt),
        )

        val time = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        lifecycleScope.launch(Dispatchers.IO) {
            dbMutex.withLock {
                val id = repo.value.hikeId ?: return@withLock
                dao().insertPoint(
                    TrackPoint(
                        hikeId = id, time = time, lat = loc.latitude, lng = loc.longitude,
                        altitude = alt, speed = speed, accuracy = acc, segmentStart = segmentStart,
                    ),
                )
            }
        }
        notifyStatus()
    }

    private fun maxOfNullable(a: Double?, b: Double?) = if (a == null) b else if (b == null) a else maxOf(a, b)
    private fun minOfNullable(a: Double?, b: Double?) = if (a == null) b else if (b == null) a else minOf(a, b)

    private fun dao() = AppDatabase.get(this).hikeDao()

    private fun notifyStatus(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotifyAt < 5_000) return
        lastNotifyAt = now
        val s = repo.value
        val text = buildString {
            if (s.paused) append("일시정지 · ")
            append(Format.distance(s.distanceM))
            append(" · ")
            append(Format.duration(s.movingTimeMs(now)))
            s.altitudeM?.let { append(" · 고도 ").append(Format.altitude(it)) }
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hike)
            .setContentTitle("우성트래킹 기록 중")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "산행 기록", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        super.onDestroy()
    }
}
