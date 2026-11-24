package com.woosungtracking.ui

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.Overlay
import com.naver.maps.map.overlay.PathOverlay

data class PhotoPin(val id: Long, val position: LatLng, val label: String)

/**
 * 네이버 지도를 Compose 에서 쓰기 위한 래퍼.
 * - path: 산행 경로
 * - photos: 사진 위치 핀
 * - current: 현재 위치 (기록 중)
 * - follow: true 면 현재 위치를 따라 카메라 이동
 * - fitToPath: true 면 처음 한 번 경로 전체가 보이도록 맞춤
 */
@Composable
fun NaverMapView(
    modifier: Modifier = Modifier,
    path: List<LatLng>,
    photos: List<PhotoPin> = emptyList(),
    current: LatLng? = null,
    follow: Boolean = false,
    fitToPath: Boolean = false,
    showEndpoints: Boolean = false,
    onUserGesture: () -> Unit = {},
    onPhotoClick: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).also { it.onCreate(null) } }
    var naverMap by remember { mutableStateOf<NaverMap?>(null) }
    val gesture by rememberUpdatedState(onUserGesture)
    val photoClick by rememberUpdatedState(onPhotoClick)

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.mapType = NaverMap.MapType.Terrain
            map.setLayerGroupEnabled(NaverMap.LAYER_GROUP_MOUNTAIN, true)
            map.uiSettings.isScaleBarEnabled = true
            map.uiSettings.isZoomControlEnabled = false
            map.addOnCameraChangeListener { reason, _ ->
                if (reason == CameraUpdate.REASON_GESTURE) gesture()
            }
            naverMap = map
        }
    }

    val pathOverlay = remember {
        PathOverlay().apply {
            color = Color.parseColor("#E8590C")
            outlineColor = Color.WHITE
            width = 14
            outlineWidth = 4
        }
    }
    val startMarker = remember { Marker().apply { captionText = "출발"; iconTintColor = Color.parseColor("#1F6B4A") } }
    val endMarker = remember { Marker().apply { captionText = "도착"; iconTintColor = Color.parseColor("#C92A2A") } }
    val photoMarkers = remember { mutableListOf<Marker>() }
    var fitted by remember { mutableStateOf(false) }
    var centeredOnce by remember { mutableStateOf(false) }

    LaunchedEffect(naverMap, path) {
        val map = naverMap ?: return@LaunchedEffect
        if (path.size >= 2) {
            pathOverlay.coords = path
            pathOverlay.map = map
        } else {
            pathOverlay.map = null
        }
        if (showEndpoints && path.isNotEmpty()) {
            startMarker.position = path.first()
            startMarker.map = map
            if (path.size >= 2) {
                endMarker.position = path.last()
                endMarker.map = map
            }
        }
        if (fitToPath && !fitted && path.size >= 2) {
            map.moveCamera(CameraUpdate.fitBounds(LatLngBounds.from(path), 120))
            fitted = true
        } else if (fitToPath && !fitted && path.size == 1) {
            map.moveCamera(CameraUpdate.scrollAndZoomTo(path.first(), 15.0))
            fitted = true
        }
    }

    LaunchedEffect(naverMap, photos) {
        val map = naverMap ?: return@LaunchedEffect
        photoMarkers.forEach { it.map = null }
        photoMarkers.clear()
        photos.forEach { pin ->
            photoMarkers += Marker().apply {
                position = pin.position
                captionText = pin.label
                iconTintColor = Color.parseColor("#1971C2")
                onClickListener = Overlay.OnClickListener {
                    photoClick(pin.id)
                    true
                }
                this.map = map
            }
        }
    }

    LaunchedEffect(naverMap, current, follow) {
        val map = naverMap ?: return@LaunchedEffect
        val overlay = map.locationOverlay
        if (current == null) {
            overlay.isVisible = false
            return@LaunchedEffect
        }
        overlay.isVisible = true
        overlay.position = current
        if (!centeredOnce) {
            map.moveCamera(CameraUpdate.scrollAndZoomTo(current, 16.0))
            centeredOnce = true
        } else if (follow) {
            map.moveCamera(CameraUpdate.scrollTo(current))
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
