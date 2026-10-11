package com.sapphire.hawe.feature.mission

enum class DrawTool { PEN, ERASER }

enum class DrawThickness(val dotDp: Int, val penWidthDp: Float, val label: String) {
    THIN(4, 3f, "가는 굵기"), MEDIUM(8, 6f, "보통 굵기"), THICK(14, 12f, "굵은 굵기")
}

object DrawPalette {
    val colors = listOf(0xFF111111.toInt(), 0xFF8E8E93.toInt(), 0xFFE5484D.toInt(),
        0xFFF5A524.toInt(), 0xFFF2D74E.toInt(), 0xFF30A46C.toInt(), 0xFF2F5BFF.toInt())
}

data class StrokePoint(val xDp: Float, val yDp: Float)
data class DrawStroke(val points: List<StrokePoint>, val color: Int, val widthDp: Float, val isEraser: Boolean)

class DrawingModel {
    var tool = DrawTool.PEN
        private set
    var thickness = DrawThickness.MEDIUM
        private set
    var colorIndex = 0
        private set
    private val history = mutableListOf<DrawStroke>()
    private val redoHistory = mutableListOf<DrawStroke>()
    private val currentPoints = mutableListOf<StrokePoint>()
    private var currentColor = DrawPalette.colors[0]
    private var currentWidth = thickness.penWidthDp
    private var currentEraser = false

    val strokes: List<DrawStroke> get() = history.toList()
    val redoStack: List<DrawStroke> get() = redoHistory.toList()
    val activeStroke: DrawStroke? get() = if (currentPoints.isEmpty()) null else
        DrawStroke(currentPoints.toList(), currentColor, currentWidth, currentEraser)
    val canUndo get() = history.isNotEmpty()
    val canRedo get() = redoHistory.isNotEmpty()
    val isEmpty get() = history.none { !it.isEraser } && (activeStroke?.isEraser != false)
    val showsColorSelection get() = tool == DrawTool.PEN
    val widthDp get() = thickness.penWidthDp * if (tool == DrawTool.ERASER) 3f else 1f

    fun selectTool(value: DrawTool) { tool = value }
    fun selectThickness(value: DrawThickness) { thickness = value }
    fun selectColor(index: Int) {
        if (index in DrawPalette.colors.indices) { colorIndex = index; tool = DrawTool.PEN }
    }
    fun begin(point: StrokePoint) {
        currentPoints.clear()
        currentPoints += point
        currentColor = DrawPalette.colors[colorIndex]
        currentWidth = widthDp
        currentEraser = tool == DrawTool.ERASER
    }
    fun append(point: StrokePoint) { if (currentPoints.isNotEmpty()) currentPoints += point }
    fun end() {
        activeStroke?.let { history += it; redoHistory.clear() }
        currentPoints.clear()
    }
    fun undo() { if (history.isNotEmpty()) redoHistory += history.removeAt(history.lastIndex) }
    fun redo() { if (redoHistory.isNotEmpty()) history += redoHistory.removeAt(redoHistory.lastIndex) }
}
