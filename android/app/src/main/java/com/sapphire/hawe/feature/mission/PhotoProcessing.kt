package com.sapphire.hawe.feature.mission

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream

data class CropRect(val x: Int, val y: Int, val width: Int, val height: Int)

fun centerCropRect(width: Int, height: Int): CropRect {
    if (width <= 0 || height <= 0) return CropRect(0, 0, 0, 0)
    val cropWidth = minOf(width, (height.toLong() * 3 / 4).toInt().coerceAtLeast(1))
    val cropHeight = minOf(height, (width.toLong() * 4 / 3).toInt().coerceAtLeast(1))
    return CropRect((width - cropWidth) / 2, (height - cropHeight) / 2, cropWidth, cropHeight)
}

fun sampleSizeFor(width: Int, height: Int, maxSide: Int = 2048): Int {
    if (width <= 0 || height <= 0 || maxSide <= 0) return 1
    var sample = 1
    while (maxOf(width.toLong(), height.toLong()) / sample > maxSide) sample *= 2
    return sample
}

fun loadCroppedPhoto(context: Context, uri: Uri): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val orientation = resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(
        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = resolver.openInputStream(uri) ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
    val original = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val angle = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val flip = orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL || orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL ||
        orientation == ExifInterface.ORIENTATION_TRANSPOSE || orientation == ExifInterface.ORIENTATION_TRANSVERSE
    val matrix = Matrix().apply {
        if (flip) postScale(if (orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL) 1f else -1f,
            if (orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL) -1f else 1f)
        postRotate(when (orientation) {
            ExifInterface.ORIENTATION_TRANSPOSE -> 90f
            ExifInterface.ORIENTATION_TRANSVERSE -> 270f
            else -> angle
        })
    }
    val upright = if (angle != 0f || flip) Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true).also {
        if (it !== original) original.recycle()
    } else original
    val rect = centerCropRect(upright.width, upright.height)
    Bitmap.createBitmap(upright, rect.x, rect.y, rect.width, rect.height).also {
        if (it !== upright) upright.recycle()
    }
}.getOrNull()

fun photoJpegBytes(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { output ->
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
    output.toByteArray()
}
