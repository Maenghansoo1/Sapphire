package com.sapphire.hawe.feature.dev

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.net.DevRepository
import com.sapphire.hawe.core.net.DevUser
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/** 목록에서 지금 계정 다음 유저(끝이면 처음으로). 지금 계정이 목록에 없으면 첫 다른 유저, 바꿀 사람이 없으면 null. */
fun nextDevUser(users: List<DevUser>, current: Long): DevUser? {
    val others = users.filter { it.userId != current }
    if (others.isEmpty()) return null
    val index = users.indexOfFirst { it.userId == current }
    return if (index < 0) others.first() else users[(index + 1) % users.size]
}

/**
 * 임시 UI: 개발자 모드에서 어느 화면에나 떠 있는 유저 전환 버튼.
 * 누르면 다음 유저로 바로 전환, 길게 누르면 유저 목록. 화면을 가리면 끌어서 옮긴다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DevUserSwitcher(dev: DevRepository, currentUserId: Long, busy: Boolean, onSwitch: (Long) -> Unit) {
    var users by remember { mutableStateOf<List<DevUser>>(emptyList()) }
    var usersError by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var pill by remember { mutableStateOf(IntSize.Zero) }
    var x by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    var y by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    LaunchedEffect(reload) {
        try {
            users = dev.users()
            usersError = false
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            usersError = true
        }
    }
    val current = users.firstOrNull { it.userId == currentUserId }
    val next = nextDevUser(users, currentUserId)
    val margin = with(LocalDensity.current) { 12.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxX = (constraints.maxWidth - pill.width).coerceAtLeast(0).toFloat()
        val maxY = (constraints.maxHeight - pill.height).coerceAtLeast(0).toFloat()
        // 처음에는 오른쪽 가운데보다 조금 아래. 화면 크기가 바뀌어도 화면 안에 머문다.
        val defaultX = (maxX - margin).coerceAtLeast(0f)
        val defaultY = maxY * 0.6f
        val px = if (x.isNaN()) defaultX else x.coerceIn(0f, maxX)
        val py = if (y.isNaN()) defaultY else y.coerceIn(0f, maxY)
        Box(Modifier.offset { IntOffset(px.roundToInt(), py.roundToInt()) }
            .onSizeChanged { pill = it }
            .pointerInput(maxX, maxY, defaultX, defaultY) {
                detectDragGestures { change, amount ->
                    change.consume()
                    x = ((if (x.isNaN()) defaultX else x) + amount.x).coerceIn(0f, maxX)
                    y = ((if (y.isNaN()) defaultY else y) + amount.y).coerceIn(0f, maxY)
                }
            }
            .background(HColors.ink.copy(alpha = 0.85f), CircleShape)
            .combinedClickable(enabled = !busy,
                onClick = {
                    if (next != null) onSwitch(next.userId)
                    else { reload++; menu = true }
                },
                onLongClick = { reload++; menu = true })
            .semantics { contentDescription = "개발자 유저 전환" }
            .padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(if (busy) "전환 중…" else "⇄ " + (current?.nickname ?: "유저 전환"),
                style = HType.plex(13, FontWeight.SemiBold), color = HColors.onAccent, maxLines = 1)
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                users.forEach { user ->
                    val selected = user.userId == currentUserId
                    DropdownMenuItem(text = { Text((if (selected) "✓ " else "") + user.nickname, style = HType.plex(14)) },
                        enabled = !busy && !selected,
                        onClick = { menu = false; onSwitch(user.userId) })
                }
                if (usersError) DropdownMenuItem(text = { Text("목록을 불러오지 못했어요 · 다시 시도", style = HType.plex(14)) },
                    onClick = { reload++ })
            }
        }
    }
}
