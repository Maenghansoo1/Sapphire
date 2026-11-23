package com.sapphire.hawe.feature.photo

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.HChip
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HLayout
import com.sapphire.hawe.designsystem.tokens.HType
import com.sapphire.hawe.feature.mission.photoJpegBytes
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 사진 한 장의 얼굴 가리기 편집 상태(TODO_2/C3). 사진이 바뀌면 새로 만들어요.
 * 사진·검출 결과는 메모리에만 있고 파일·로그·서버에 남기지 않아요. 서버는 가렸는지 모르고, 가린 사진만 받아요.
 */
class FaceBlurEditorState internal constructor(val mode: BlurMode) {
    var state by mutableStateOf<FaceBlurState?>(null)
        internal set
    var detecting by mutableStateOf(mode != BlurMode.OFF)
        internal set
    var failed by mutableStateOf(false)
        internal set
    /** 켜면 사진 위를 끌어서 가릴 곳을 직접 그려요. 한 곳을 그리면 꺼져요. */
    var addMode by mutableStateOf(false)
    val enabled: Boolean get() = mode != BlurMode.OFF

    fun update(change: (FaceBlurState) -> FaceBlurState) { state = state?.let(change) }
    fun selected(): List<FaceRegion> = state?.selected().orEmpty()
}

/** 사진이 정해지면 기기 안에서 얼굴을 찾아요. 사진이 없으면 null. [BlurMode.OFF] 면 찾지 않아요. */
@Composable
fun rememberFaceBlurEditor(photo: Bitmap?, mode: BlurMode): FaceBlurEditorState? {
    val detector = remember { MlKitFaceDetector() }
    val editor = remember(photo, mode) { photo?.let { FaceBlurEditorState(mode) } }
    LaunchedEffect(editor) {
        if (editor == null || photo == null) return@LaunchedEffect
        val outcome = withContext(Dispatchers.Default) {
            detectFaceState(photo.width, photo.height, mode) { detector.detect(photo) }
        }
        editor.state = outcome.state
        editor.failed = outcome.failed
        editor.detecting = false
    }
    return editor
}

/**
 * 올릴 JPEG. 가린 곳이 있으면 모자이크를 **픽셀에 넣은** 새 비트맵으로, 없으면 원본을 그대로 인코딩해요(위에 덮개만 그린 이미지가 아님).
 * 화질은 기존 사진 제출과 같아요([photoJpegBytes]).
 */
fun faceBlurUploadBytes(photo: Bitmap, selected: List<FaceRegion>): ByteArray {
    val image = applyBlur(photo, selected)
    return try { photoJpegBytes(image) } finally { if (image !== photo) image.recycle() }
}

/** 사진 + 얼굴 상자(누르면 켜고 끄기) + 직접 그리기 층. 미리보기는 실제로 올라갈 모자이크 결과예요. 임시 UI: Figma 확정 시 교체. */
@Composable
fun FaceBlurPhoto(photo: Bitmap, editor: FaceBlurEditorState, modifier: Modifier = Modifier) {
    val selected = editor.selected()
    val preview by produceState(photo, photo, selected) { value = withContext(Dispatchers.Default) { applyBlur(photo, selected) } }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        Image(preview.asImageBitmap(), "올릴 사진 미리보기", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        val state = editor.state
        if (state != null && editor.enabled) {
            val viewport = FitViewport(photo.width, photo.height, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            val minTouch = with(density) { HLayout.minTouch.toPx() }
            val numbers = state.regions.withIndex().associate { (index, region) -> region.id to index + 1 }
            drawOrder(state.regions).forEach { region ->
                RegionBox(region, numbers.getValue(region.id), viewport, minTouch, enabled = !editor.addMode,
                    onToggle = { editor.update { it.toggle(region.id) } },
                    onRemove = { editor.update { it.removeManual(region.id) } })
            }
            if (editor.addMode) DrawRegionLayer(viewport) { rect ->
                if (rect.width >= MIN_MANUAL_REGION && rect.height >= MIN_MANUAL_REGION) editor.update { it.addManual(rect) }
                editor.addMode = false
            }
        }
        if (editor.detecting) Box(Modifier.fillMaxSize().background(HColors.ink.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), color = HColors.onAccent, strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun RegionBox(region: FaceRegion, number: Int, viewport: FitViewport, minTouch: Float, enabled: Boolean,
    onToggle: () -> Unit, onRemove: () -> Unit) {
    val density = LocalDensity.current
    val rect = viewport.toView(region.rect)
    val target = touchTarget(rect, minTouch)
    val description = faceRegionDescription(region, number)
    with(density) {
        Box(Modifier.offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
            .size(target.width.toDp(), target.height.toDp())
            .clickable(enabled = enabled, role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = description }) {
            // 실제 상자(누름 영역은 작은 얼굴이면 이보다 넓어요). 안 가린 상자는 흰 테두리 + 어두운 바깥선으로 밝은 사진에서도 보이게
            val frame = Modifier.offset { IntOffset((rect.left - target.left).roundToInt(), (rect.top - target.top).roundToInt()) }
                .size(rect.width.toDp(), rect.height.toDp())
            if (region.blurred) Box(frame.border(2.5.dp, HColors.accent, RoundedCornerShape(4.dp)))
            else Box(frame.border(3.dp, HColors.ink.copy(alpha = 0.35f), RoundedCornerShape(4.dp)).padding(1.dp)
                .border(2.dp, Color.White, RoundedCornerShape(4.dp)))
            if (region.source == RegionSource.MANUAL) Box(Modifier.align(Alignment.TopEnd).size(HLayout.minTouch)
                .clickable(enabled = enabled, role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = "직접 그린 영역 $number 지우기" }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(22.dp).background(HColors.ink.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
                    HIconView(HIcon.CLOSE, size = 14.dp, color = HColors.onAccent)
                }
            }
        }
    }
}

/** 끌어서 사각형을 그리는 층. 그리는 동안 테두리를 보여 주고, 손을 떼면 이미지 좌표로 넘겨요. */
@Composable
private fun DrawRegionLayer(viewport: FitViewport, onRect: (PixelRect) -> Unit) {
    val latestOnRect by rememberUpdatedState(onRect)
    var start by remember { mutableStateOf<Offset?>(null) }
    var current by remember { mutableStateOf<Offset?>(null) }
    val accent = HColors.accent
    Box(Modifier.fillMaxSize()
        .semantics { contentDescription = "가릴 곳을 손가락으로 끌어서 그려 주세요" }
        .pointerInput(viewport) {
            detectDragGestures(
                onDragStart = { start = it; current = it },
                onDrag = { change, _ -> current = change.position; change.consume() },
                onDragEnd = {
                    val from = start; val to = current
                    start = null; current = null
                    if (from != null && to != null) latestOnRect(viewport.dragToImageRect(from.x, from.y, to.x, to.y))
                },
                onDragCancel = { start = null; current = null })
        }
        .drawBehind {
            val from = start ?: return@drawBehind
            val to = current ?: return@drawBehind
            drawRect(accent, Offset(min(from.x, to.x), min(from.y, to.y)), Size(abs(to.x - from.x), abs(to.y - from.y)),
                style = Stroke(2.dp.toPx()))
        })
}

/** 사진 아래 안내와 버튼(영역 추가, 모두 가리기/풀기). 임시 UI. */
@Composable
fun FaceBlurControls(editor: FaceBlurEditorState, modifier: Modifier = Modifier) {
    if (!editor.enabled) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(faceBlurGuide(editor.state, editor.detecting, editor.failed), style = HType.plex(14, FontWeight.SemiBold),
                color = HColors.ink)
            Text(FACE_BLUR_CHECK_HINT, style = HType.caption12, color = HColors.textSecondary)
        }
        val state = editor.state ?: return@Column
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HChip(if (editor.addMode) "사진 위를 끌어 주세요" else "영역 추가", editor.addMode) { editor.addMode = !editor.addMode }
            if (state.regions.isNotEmpty()) {
                val allHidden = state.regions.all { it.blurred }
                HChip(if (allHidden) "모두 풀기" else "모두 가리기", false) {
                    editor.update { it.applyDefaultMode(if (allHidden) BlurMode.NONE else BlurMode.ALL) }
                }
            }
        }
    }
}
