package com.sapphire.hawe.feature.mission

import org.junit.Assert.*
import org.junit.Test

class DrawingModelTest {
    private val point = StrokePoint(5f, 10f)
    private fun DrawingModel.stroke() { begin(point); append(StrokePoint(8f, 12f)); end() }

    @Test fun defaultsAndPalette() {
        val model = DrawingModel()
        assertEquals(DrawTool.PEN, model.tool)
        assertEquals(DrawThickness.MEDIUM, model.thickness)
        assertEquals(0, model.colorIndex)
        assertTrue(model.isEmpty)
        assertFalse(model.canUndo)
        assertFalse(model.canRedo)
        assertEquals(listOf(0xFF111111.toInt(), 0xFF8E8E93.toInt(), 0xFFE5484D.toInt(),
            0xFFF5A524.toInt(), 0xFFF2D74E.toInt(), 0xFF30A46C.toInt(), 0xFF2F5BFF.toInt()), DrawPalette.colors)
    }

    @Test fun widthsAndColorSelection() {
        assertEquals(listOf(4, 8, 14), DrawThickness.entries.map { it.dotDp })
        val model = DrawingModel()
        assertEquals(listOf(3f, 6f, 12f), DrawThickness.entries.map { it.penWidthDp })
        assertEquals(6f, model.widthDp)
        model.stroke()
        model.selectTool(DrawTool.ERASER)
        assertFalse(model.showsColorSelection)
        assertEquals(18f, model.widthDp)
        model.selectThickness(DrawThickness.THIN)
        assertEquals(9f, model.widthDp)
        model.selectThickness(DrawThickness.THICK)
        assertEquals(36f, model.widthDp)
        assertEquals(6f, model.strokes.first().widthDp)
        model.selectColor(2)
        assertEquals(DrawTool.PEN, model.tool)
        assertTrue(model.showsColorSelection)
        assertEquals(2, model.colorIndex)
    }

    @Test fun historyAndEmptyRules() {
        val model = DrawingModel()
        model.undo(); model.redo()
        model.selectTool(DrawTool.ERASER)
        model.stroke()
        assertTrue(model.isEmpty)
        model.selectTool(DrawTool.PEN)
        model.stroke()
        assertFalse(model.isEmpty)
        assertTrue(model.canUndo)
        model.undo()
        assertTrue(model.isEmpty)
        assertTrue(model.canRedo)
        model.redo()
        assertFalse(model.isEmpty)
        model.undo()
        model.stroke()
        assertFalse(model.canRedo)
    }
}
