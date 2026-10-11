package com.sapphire.hawe.feature.mission

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

fun drawStrokes(canvas: Canvas, strokes: List<DrawStroke>, scale: Float) {
    strokes.forEach { stroke ->
        val points = stroke.points
        if (points.isEmpty()) return@forEach
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color
            strokeWidth = stroke.widthDp * scale
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (stroke.isEraser) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        val first = points.first()
        if (points.size == 1) {
            canvas.drawCircle(first.xDp * scale, first.yDp * scale, paint.strokeWidth / 2f,
                Paint(paint).apply { style = Paint.Style.FILL })
        } else {
            val path = Path().apply { moveTo(first.xDp * scale, first.yDp * scale) }
            for (index in 1 until points.size) {
                val previous = points[index - 1]
                val next = points[index]
                path.quadTo(previous.xDp * scale, previous.yDp * scale,
                    (previous.xDp + next.xDp) * scale / 2f, (previous.yDp + next.yDp) * scale / 2f)
            }
            val last = points.last()
            path.lineTo(last.xDp * scale, last.yDp * scale)
            canvas.drawPath(path, paint)
        }
    }
}

fun renderDrawingPng(strokes: List<DrawStroke>, widthDp: Float, heightDp: Float, scale: Float = 2f): ByteArray {
    val width = (widthDp * scale).roundToInt().coerceAtLeast(1)
    val height = (heightDp * scale).roundToInt().coerceAtLeast(1)
    val ink = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    return try {
        drawStrokes(Canvas(ink), strokes, scale)
        val canvas = Canvas(flattened)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(ink, 0f, 0f, null)
        ByteArrayOutputStream().use { output ->
            flattened.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    } finally { ink.recycle(); flattened.recycle() }
}
