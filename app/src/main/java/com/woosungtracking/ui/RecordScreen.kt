package com.woosungtracking.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naver.maps.geometry.LatLng
import com.woosungtracking.data.AppDatabase
import com.woosungtracking.data.Photo
import com.woosungtracking.tracking.TrackingRepository
import com.woosungtracking.tracking.TrackingService
import com.woosungtracking.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File

object PhotoFiles {
    fun newFile(context: Context, hikeId: Long): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "hike_$hikeId")
        dir.mkdirs()
        return File(dir, "IMG_${System.currentTimeMillis()}.jpg")
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
fun RecordScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember(context) { AppDatabase.get(context).hikeDao() }
    val s by TrackingRepository.state.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    var follow by rememberSaveable { mutableStateOf(true) }
    var confirmStop by remember { mutableStateOf(false) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var viewPhotoId by remember { mutableStateOf<Long?>(null) }

    val photos by remember(s.hikeId) {
        s.hikeId?.let { dao.observePhotos(it) } ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPhoto
        pendingPhoto = null
        val hikeId = TrackingRepository.state.value.hikeId
        if (path == null || hikeId == null) return@rememberLauncherForActivityResult
        if (!ok) {
            File(path).delete()
            return@rememberLauncherForActivityResult
        }
        val here = TrackingRepository.state.value.current
        scope.launch {
            dao.insertPhoto(
                Photo(hikeId = hikeId, time = System.currentTimeMillis(), lat = here?.lat, lng = here?.lng, path = path),
            )
        }
        Toast.makeText(context, "사진을 경로에 추가했어요.", Toast.LENGTH_SHORT).show()
    }

    BackHandler { onBack() }

    val path = remember(s.points) { s.points.map { LatLng(it.lat, it.lng) } }
    val current = s.current?.let { LatLng(it.lat, it.lng) }
    val pins = remember(photos) {
        photos.mapIndexedNotNull { i, p ->
            if (p.lat != null && p.lng != null) PhotoPin(p.id, LatLng(p.lat, p.lng), "사진 ${i + 1}") else null
        }
    }

    Box(Modifier.fillMaxSize()) {
        NaverMapView(
            modifier = Modifier.fillMaxSize(),
            path = path,
            photos = pins,
            current = current,
            follow = follow,
            onUserGesture = { follow = false },
            onPhotoClick = { viewPhotoId = it },
        )

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            SmallFloatingActionButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
            MapAuthBanner(Modifier.padding(horizontal = 8.dp).weight(1f, fill = false))
            SmallFloatingActionButton(onClick = {
                follow = true
            }) { Icon(Icons.Filled.MyLocation, "내 위치 따라가기") }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.navigationBarsPadding().padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Stat("거리", Format.distance(s.distanceM), Modifier.weight(1f), big = true)
                    Stat("시간", Format.duration(s.movingTimeMs(now)), Modifier.weight(1f), big = true)
                }
                Spacer(Modifier.height(12.dp))
                StatRow(
                    "고도" to (s.altitudeM?.let { Format.altitude(it) } ?: "-"),
                    "속도" to Format.speedKmh(s.speedMps),
                    "오른 높이" to Format.altitude(s.elevationGainM),
                )
                if (s.current == null) {
                    Text(
                        "GPS 신호를 찾는 중이에요. 하늘이 보이는 곳에서 잠시 기다려 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else if (s.paused) {
                    Text(
                        "일시정지 중",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val hikeId = s.hikeId ?: return@FilledTonalButton
                            val file = PhotoFiles.newFile(context, hikeId)
                            pendingPhoto = file.absolutePath
                            try {
                                camera.launch(PhotoFiles.uriFor(context, file))
                            } catch (e: Exception) {
                                pendingPhoto = null
                                Toast.makeText(context, "카메라 앱을 열 수 없어요.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Filled.CameraAlt, null, Modifier.size(20.dp))
                        Text("  사진")
                    }
                    FilledTonalButton(
                        onClick = {
                            TrackingService.send(
                                context,
                                if (s.paused) TrackingService.ACTION_RESUME else TrackingService.ACTION_PAUSE,
                            )
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(if (s.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null, Modifier.size(20.dp))
                        Text(if (s.paused) "  재개" else "  일시정지")
                    }
                    Button(
                        onClick = { confirmStop = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Filled.Stop, null, Modifier.size(20.dp))
                        Text("  종료")
                    }
                }
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("산행을 마칠까요?") },
            text = { Text("${Format.distance(s.distanceM)} · ${Format.duration(s.movingTimeMs(now))} 기록을 저장합니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    TrackingService.send(context, TrackingService.ACTION_STOP)
                }) { Text("저장하고 종료") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("계속 기록") } },
        )
    }

    viewPhotoId?.let { id ->
        photos.firstOrNull { it.id == id }?.let { PhotoDialog(it) { viewPhotoId = null } }
    }
}
