package com.woosungtracking.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.woosungtracking.MapAuth

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = if (big) 28.sp else 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun StatRow(vararg stats: Pair<String, String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.forEach { (l, v) -> Stat(l, v, Modifier.weight(1f)) }
    }
}

/** 네이버 지도 키가 없거나 인증에 실패했을 때 지도 위에 띄우는 안내 */
@Composable
fun MapAuthBanner(modifier: Modifier = Modifier) {
    val error by MapAuth.error.collectAsStateWithLifecycle()
    val msg = error ?: return
    Text(
        text = "$msg\n기록과 사진, 공유는 지도 없이도 돼요.",
        color = Color.White,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .background(Color(0xCC000000), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
