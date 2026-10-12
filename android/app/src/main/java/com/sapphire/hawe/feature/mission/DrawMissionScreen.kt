package com.sapphire.hawe.feature.mission

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.HButton
import com.sapphire.hawe.designsystem.components.HChip
import com.sapphire.hawe.designsystem.components.HToastHost
import com.sapphire.hawe.designsystem.components.rememberHToastState
import com.sapphire.hawe.designsystem.layout.ctaBottomPadding
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

@Composable
fun DrawMissionScreen(topic: String, spec: MissionSpec, onClose: () -> Unit, onSubmit: suspend (ByteArray) -> Boolean) {
    val model = remember { DrawingModel() }
    val timer = remember(spec.seconds) { spec.seconds?.let { SketchTimer(it) } }
    val toast = rememberHToastState()
    var remaining by remember(timer) { mutableIntStateOf(timer?.totalSeconds ?: 0) }
    LaunchedEffect(timer) {
        while (timer != null) {
            remaining = timer.remainingSeconds()
            if (timer.expired) { toast.show("시간이 끝났어요"); break }
            delay(200)
        }
    }
    var version by remember { mutableIntStateOf(0) }
    val drawVersion = version
    val hasDraft = drawVersion.let { !model.isEmpty }
    val canUndo = drawVersion.let { model.canUndo }
    val canRedo = drawVersion.let { model.canRedo }
    val selectedTool = drawVersion.let { model.tool }
    val selectedThickness = drawVersion.let { model.thickness }
    val selectedColor = drawVersion.let { model.colorIndex }
    var submitted by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val bottom = ctaBottomPadding(32.dp)
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(HColors.bg).padding(horizontal = 20.dp)) {
        MissionHeader(hasDraft, onClose, trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (timer != null) Text("0:${remaining.toString().padStart(2, '0')}",
                    style = HType.caption13, color = HColors.textSecondary)
                DrawHistoryButton(HIcon.UNDO, "되돌리기", canUndo) { model.undo(); version++ }
                DrawHistoryButton(HIcon.REDO, "다시 실행", canRedo) { model.redo(); version++ }
            }
        })
        Spacer(Modifier.height(16.dp))
        MissionTitle(MissionType.DRAW, topic)
        if (spec.drawFrom != null && spec.drawTo != null) Text("‘${spec.drawFrom}’를 ‘${spec.drawTo}’로",
            style = HType.caption13, color = HColors.textSecondary)
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val canvasHeight = minOf(maxHeight, 400.dp)
            Box(Modifier.fillMaxWidth().height(canvasHeight)
                .clip(RoundedCornerShape(HRadius.card)).background(Color.White)
                .border(1.dp, HColors.line, RoundedCornerShape(HRadius.card))) {
                Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(model, density, timer) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (timer?.expired == true) return@awaitEachGesture
                            timer?.start()
                            model.begin(StrokePoint(down.position.x / density, down.position.y / density))
                            version++
                            down.consume()
                            drag(down.id) { change ->
                                if (timer?.expired != true) {
                                    model.append(StrokePoint(change.position.x / density, change.position.y / density))
                                    version++
                                }
                                change.consume()
                            }
                            model.end()
                            version++
                        }
                    }) {
                    @Suppress("UNUSED_VARIABLE") val observed = drawVersion
                    drawIntoCanvas { canvas ->
                        drawStrokes(canvas.nativeCanvas,
                            model.strokes + listOfNotNull(model.activeStroke), density)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HChip("펜", selectedTool == DrawTool.PEN) { model.selectTool(DrawTool.PEN); version++ }
                HChip("지우개", selectedTool == DrawTool.ERASER) { model.selectTool(DrawTool.ERASER); version++ }
            }
            Spacer(Modifier.weight(1f))
            DrawThickness.entries.forEach { thickness ->
                Box(Modifier.size(44.dp)
                    .background(if (selectedThickness == thickness) HColors.surfaceStrong else HColors.bg, CircleShape)
                    .clickable { model.selectThickness(thickness); version++ }
                    .semantics { contentDescription = thickness.label }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(thickness.dotDp.dp).background(HColors.ink, CircleShape))
                }
                if (thickness != DrawThickness.THICK) Spacer(Modifier.width(4.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DrawPalette.colors.forEachIndexed { index, argb ->
                val selected = selectedTool == DrawTool.PEN && selectedColor == index
                Box(Modifier.size(44.dp).clickable { model.selectColor(index); version++ }
                    .semantics { contentDescription = "색 ${index + 1}" }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(30.dp).background(Color(argb), CircleShape))
                    if (selected) Box(Modifier.size(44.dp).border(2.dp, HColors.ink, CircleShape))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HButton(if (submitted) "올리는 중..." else "올리기", enabled = hasDraft && !submitted && canvasSize.width > 0) {
            if (submitted) return@HButton
            submitted = true
            val strokes = model.strokes
            val width = canvasSize.width / density
            val height = canvasSize.height / density
            scope.launch {
                try { if (!onSubmit(withContext(Dispatchers.Default) { renderDrawingPng(strokes, width, height) })) submitted = false }
                catch (error: Exception) { submitted = false }
            }
        }
        Spacer(Modifier.height(bottom))
    }
    HToastHost(toast)
    }
}

@Composable
private fun DrawHistoryButton(icon: HIcon, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(enabled = enabled, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        HIconView(icon, size = 22.dp, color = if (enabled) HColors.ink else HColors.controlBorder)
    }
}
