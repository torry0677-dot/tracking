package com.woosungtracking.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.naver.maps.geometry.LatLng
import com.woosungtracking.data.AppDatabase
import com.woosungtracking.data.Photo
import com.woosungtracking.data.TrackPoint
import com.woosungtracking.data.TrackStats
import com.woosungtracking.share.ShareLink
import com.woosungtracking.util.Format
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HikeDetailScreen(hikeId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember(context) { AppDatabase.get(context).hikeDao() }
    val hike by dao.observeHike(hikeId).collectAsStateWithLifecycle(initialValue = null)
    val photos by dao.observePhotos(hikeId).collectAsStateWithLifecycle(initialValue = emptyList())
    val points by produceState<List<TrackPoint>?>(initialValue = null, hikeId) { value = dao.getPoints(hikeId) }

    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var viewPhotoId by remember { mutableStateOf<Long?>(null) }

    val path = remember(points) { points.orEmpty().map { LatLng(it.lat, it.lng) } }
    val profile = remember(points) { TrackStats.elevationProfile(points.orEmpty()) }
    val pins = remember(photos) {
        photos.mapIndexedNotNull { i, p ->
            if (p.lat != null && p.lng != null) PhotoPin(p.id, LatLng(p.lat, p.lng), "사진 ${i + 1}") else null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(hike?.title ?: "", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                actions = {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, "이름 바꾸기") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "삭제") }
                },
            )
        },
    ) { padding ->
        val h = hike
        if (h == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                if (points != null) {
                    NaverMapView(
                        modifier = Modifier.fillMaxSize(),
                        path = path,
                        photos = pins,
                        fitToPath = true,
                        showEndpoints = true,
                        onPhotoClick = { viewPhotoId = it },
                    )
                }
                MapAuthBanner(Modifier.align(Alignment.TopCenter).padding(8.dp))
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    Format.date(h.startTime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatRow(
                    "거리" to Format.distance(h.distanceM),
                    "시간" to Format.duration(h.movingTimeMs),
                    "평균 페이스" to Format.pace(h.distanceM, h.movingTimeMs),
                )
                StatRow(
                    "오른 높이" to Format.altitude(h.elevationGainM),
                    "최고 고도" to (h.maxAltitudeM?.let { Format.altitude(it) } ?: "-"),
                    "최저 고도" to (h.minAltitudeM?.let { Format.altitude(it) } ?: "-"),
                )

                if (profile.size >= 2) {
                    Text("고도 그래프", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    ElevationChart(profile, Modifier.fillMaxWidth().height(140.dp))
                }

                if (photos.isNotEmpty()) {
                    Text("사진 ${photos.size}장", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 8.dp),
                    ) {
                        itemsIndexed(photos, key = { _, p -> p.id }) { _, p ->
                            AsyncImage(
                                model = File(p.path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewPhotoId = p.id },
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val pts = points ?: return@Button
                        val url = ShareLink.build(h, pts)
                        val text = "${h.title} · ${Format.distance(h.distanceM)} · ${Format.duration(h.movingTimeMs)}\n$url"
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, h.title)
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "산행 링크 공유"))
                    },
                    enabled = (points?.size ?: 0) >= 2,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.Share, null)
                    Text("  링크로 공유하기")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 산행을 삭제할까요?") },
            text = { Text("경로와 사진 기록이 모두 지워집니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        photos.forEach { File(it.path).delete() }
                        dao.deleteHike(hikeId)
                        onBack()
                    }
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }

    if (renaming) {
        val h = hike
        if (h != null) {
            var name by remember { mutableStateOf(h.title) }
            AlertDialog(
                onDismissRequest = { renaming = false },
                title = { Text("산행 이름") },
                text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        renaming = false
                        val t = name.trim()
                        if (t.isNotEmpty()) scope.launch { dao.updateHike(h.copy(title = t)) }
                    }) { Text("저장") }
                },
                dismissButton = { TextButton(onClick = { renaming = false }) { Text("취소") } },
            )
        }
    }

    viewPhotoId?.let { id ->
        photos.firstOrNull { it.id == id }?.let { PhotoDialog(it) { viewPhotoId = null } }
    }
}

@Composable
fun PhotoDialog(photo: Photo, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.clickable(onClick = onDismiss)) {
            AsyncImage(
                model = File(photo.path),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().background(Color.Black),
            )
            Text(
                Format.date(photo.time),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun ElevationChart(profile: List<Pair<Double, Double>>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val minAlt = profile.minOf { it.second }
    val maxAlt = profile.maxOf { it.second }
    val maxDist = profile.last().first.coerceAtLeast(1.0)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val range = (maxAlt - minAlt).coerceAtLeast(10.0)
            fun x(d: Double) = (d / maxDist * size.width).toFloat()
            fun y(a: Double) = (size.height - (a - minAlt) / range * size.height * 0.9 - size.height * 0.05).toFloat()
            val stroke = Path()
            profile.forEachIndexed { i, (d, a) ->
                if (i == 0) stroke.moveTo(x(d), y(a)) else stroke.lineTo(x(d), y(a))
            }
            val fill = Path().apply {
                addPath(stroke)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), line.copy(alpha = 0.02f))))
            drawPath(stroke, line, style = Stroke(width = 3.dp.toPx()))
            drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, size.height), Offset(size.width, size.height))
        }
        Text(
            "${Format.altitude(minAlt)} ~ ${Format.altitude(maxAlt)} · ${Format.distance(maxDist)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
