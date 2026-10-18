package com.sapphire.hawe.feature.mission

<<<<<<< HEAD
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
import com.sapphire.hawe.feature.draw.AiGuessDialog
import com.sapphire.hawe.feature.draw.AiShowButton
import com.sapphire.hawe.feature.draw.DrawSubjectMatcher
import com.sapphire.hawe.feature.draw.rememberAiGuessAvailable
import com.sapphire.hawe.feature.draw.rememberAiGuessRunner
import com.sapphire.hawe.feature.draw.strokeInkImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

@Composable
fun DrawMissionScreen(topic: String, template: String, spec: MissionSpec, onClose: () -> Unit,
    onSubmit: suspend (ByteArray) -> Boolean) {
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
    val aiAvailable = rememberAiGuessAvailable()
    val aiGuess = rememberAiGuessRunner()
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // AI 추측은 재미용이라 올리기·도장에 영향이 없고, 그림은 기기 밖으로 나가지 않아요(A8)
            if (aiAvailable) AiShowButton(enabled = hasDraft && canvasSize.width > 0) {
                val strokes = model.strokes
                val width = canvasSize.width / density
                val height = canvasSize.height / density
                aiGuess.guess(mine = true, load = { withContext(Dispatchers.Default) { strokeInkImage(strokes, width, height) } },
                    subjectOf = { DrawSubjectMatcher.match(template, topic, spec, it) })
            }
            HButton(if (submitted) "올리는 중..." else "올리기", Modifier.weight(1f),
                enabled = hasDraft && !submitted && canvasSize.width > 0) {
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
        }
        Spacer(Modifier.height(bottom))
    }
    HToastHost(toast)
    AiGuessDialog(aiGuess)
    }
}

@Composable
private fun DrawHistoryButton(icon: HIcon, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(enabled = enabled, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        HIconView(icon, size = 22.dp, color = if (enabled) HColors.ink else HColors.controlBorder)
    }
}
=======
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.sapphire.hawe.designsystem.HButton
import com.sapphire.hawe.designsystem.HChip
import com.sapphire.hawe.designsystem.HColors
import com.sapphire.hawe.designsystem.HIcon
import com.sapphire.hawe.designsystem.HLayout
import com.sapphire.hawe.designsystem.HRadius
import com.sapphire.hawe.designsystem.bottomInsetPadding
import com.sapphire.hawe.designsystem.plainClickable
import com.sapphire.hawe.model.MissionType
import com.sapphire.hawe.model.SubmissionContent

/**
 * 06 미션 수행 · 그림 (Figma 1:594)
 * 펜/지우개, 굵기 3단계, 7색, 되돌리기/다시 실행. 올리기 → 흰 바탕 PNG
 */
@Composable
fun DrawMissionScreen(topic: String, onClose: () -> Unit, onSubmit: (SubmissionContent) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val board = remember { DrawingBoard() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    MissionScaffold(
        hasDraft = !board.isEmpty,
        onClose = onClose,
        trailing = {
            HeaderIconRow {
                HeaderIconButton(HIcon.Undo, tint = if (board.canUndo) HColors.ink else HColors.controlBorder, enabled = board.canUndo) { board.undo() }
                HeaderIconButton(HIcon.Redo, tint = if (board.canRedo) HColors.ink else HColors.controlBorder, enabled = board.canRedo) { board.redo() }
            }
        },
    ) {
        Column(
            Modifier
                .weight(1f)
                .navigationBarsPadding()
                .padding(horizontal = HLayout.screenPaddingCompact)
                // 올리기 버튼은 화면 아래 끝에서 32 위 (Figma pb 32)
                .padding(bottom = bottomInsetPadding(32.dp)),
        ) {
            Column(Modifier.weight(1f).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MissionTitle(MissionType.Draw, topic)
                // 테두리 line · 모서리 20 · 높이 400 (작은 화면에서는 줄어들어요)
                val shape = RoundedCornerShape(HRadius.card)
                Canvas(
                    Modifier
                        .weight(1f, fill = false)
                        .heightIn(min = 220.dp)
                        .height(400.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .border(1.dp, HColors.line, shape)
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(board) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                board.begin(down.position)
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    board.extend(change.position)
                                    change.consume()
                                }
                                board.end()
                            }
                        },
                ) {
                    drawRect(HColors.bg)
                    board.draw(this)
                }
                Tools(board)
                Colors(board)
            }
            Spacer(Modifier.height(32.dp))
            HButton("올리기", enabled = !board.isEmpty) {
                if (canvasSize == IntSize.Zero) return@HButton
                val bitmap = board.render(canvasSize, density)
                val uri = writeSubmissionFile(context, "png") { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                onSubmit(SubmissionContent.Image(uri))
            }
        }
    }
}

/** 펜/지우개 칩 (간격 6) + 굵기 3단계 (44 원, 고른 것만 surfaceStrong 바탕, 점 4·8·14) */
@Composable
private fun Tools(board: DrawingBoard) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HChip("펜", selected = board.tool == DrawTool.Pen) { board.tool = DrawTool.Pen }
            HChip("지우개", selected = board.tool == DrawTool.Eraser) { board.tool = DrawTool.Eraser }
        }
        Spacer(Modifier.weight(1f).width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Thickness.entries.forEach { thickness ->
                Box(
                    Modifier
                        .size(44.dp)
                        .background(if (board.thickness == thickness) HColors.surfaceStrong else HColors.bg, CircleShape)
                        .plainClickable { board.thickness = thickness },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(thickness.dot).background(HColors.ink, CircleShape))
                }
            }
        }
    }
}

/** 7색 — 44 칸에 지름 30 원, 고른 색은 ink 테두리 2 */
@Composable
private fun Colors(board: DrawingBoard) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DrawingBoard.palette.forEachIndexed { index, color ->
            val selected = board.colorIndex == index && board.tool == DrawTool.Pen
            Box(
                Modifier
                    .size(44.dp)
                    .then(if (selected) Modifier.border(2.dp, HColors.ink, CircleShape) else Modifier)
                    .plainClickable {
                        board.colorIndex = index
                        board.tool = DrawTool.Pen
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(30.dp).background(color, CircleShape))
            }
        }
    }
}

enum class DrawTool { Pen, Eraser }

/** Figma 굵기 버튼 안 점 지름 [dot], 실제 펜 굵기 [penWidth] (Figma 에 없어서 점 크기에 비례 — iOS 와 같음) */
enum class Thickness(val dot: Dp, val penWidth: Dp) {
    Thin(4.dp, 3.dp), Medium(8.dp, 6.dp), Thick(14.dp, 12.dp)
}

/** 그은 선 하나. 지우개는 바탕색(흰색)으로 덮어 그려요. */
private class InkStroke(val points: List<Offset>, val color: Color, val width: Dp)

/** 그림판 상태 — 선 목록, 도구·굵기·색, 되돌리기/다시 실행 */
private class DrawingBoard {
    private val strokes = mutableStateListOf<InkStroke>()
    private val undone = mutableStateListOf<InkStroke>()
    private val current = mutableStateListOf<Offset>()

    var tool by mutableStateOf(DrawTool.Pen)
    var thickness by mutableStateOf(Thickness.Medium)
    var colorIndex by mutableStateOf(0)

    /** 펜으로 그은 선이 하나도 없으면 비어 있어요. */
    val isEmpty: Boolean get() = strokes.none { it.color != HColors.bg }
    val canUndo: Boolean get() = strokes.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()

    private val currentColor: Color get() = if (tool == DrawTool.Pen) palette[colorIndex] else HColors.bg
    private val currentWidth: Dp get() = if (tool == DrawTool.Pen) thickness.penWidth else thickness.penWidth * 3

    fun begin(point: Offset) {
        current.clear()
        current.add(point)
    }

    fun extend(point: Offset) {
        current.add(point)
    }

    fun end() {
        if (current.isEmpty()) return
        strokes.add(InkStroke(current.toList(), currentColor, currentWidth))
        undone.clear()
        current.clear()
    }

    fun undo() {
        strokes.removeLastOrNull()?.let { undone.add(it) }
    }

    fun redo() {
        undone.removeLastOrNull()?.let { strokes.add(it) }
    }

    fun draw(scope: DrawScope) {
        strokes.forEach { scope.drawInk(it.points, it.color, it.width) }
        if (current.isNotEmpty()) scope.drawInk(current, currentColor, currentWidth)
    }

    /** 흰 바탕 그림 (올리기용) */
    fun render(size: IntSize, density: Density): Bitmap {
        val image = ImageBitmap(size.width, size.height)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), size.toSize()) {
            drawRect(HColors.bg)
            draw(this)
        }
        return image.asAndroidBitmap()
    }

    companion object {
        /** Figma 06 색상 7개 (그림 물감 색이라 디자인 토큰이 아니에요 — iOS 와 같은 값) */
        val palette = listOf(
            HColors.ink,
            Color(0xFF8E8E93),
            Color(0xFFE5484D),
            Color(0xFFF5A524),
            Color(0xFFF2D74E),
            Color(0xFF30A46C),
            Color(0xFF2F5BFF),
        )
    }
}

/** 점 하나면 동그라미, 아니면 중간점을 이은 부드러운 곡선 */
private fun DrawScope.drawInk(points: List<Offset>, color: Color, width: Dp) {
    val px = width.toPx()
    if (points.size == 1) {
        drawCircle(color, px / 2, points[0])
        return
    }
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val mid = (prev + points[i]) / 2f
            quadraticTo(prev.x, prev.y, mid.x, mid.y)
        }
        lineTo(points.last().x, points.last().y)
    }
    drawPath(path, color, style = Stroke(px, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
>>>>>>> feat/ios-step1
