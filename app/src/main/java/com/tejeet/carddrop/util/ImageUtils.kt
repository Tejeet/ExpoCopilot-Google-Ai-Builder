package com.tejeet.carddrop.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    /** New file under filesDir/images/ (the directory FileProvider exposes, see res/xml/file_paths.xml). */
    fun newImageFile(context: Context, prefix: String): File {
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        return File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
    }

    /** Copies a picked image (e.g. from the gallery) into app storage so it outlives the picker's grant. */
    fun copyToAppStorage(context: Context, uri: Uri, prefix: String): String? = runCatching {
        val dest = newImageFile(context, prefix)
        context.contentResolver.openInputStream(uri)!!.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    }.getOrNull()

    /** Downsamples to keep memory use sane, and applies EXIF rotation so OCR sees upright text. */
    fun decodeUprightSampledBitmap(file: File, maxDimension: Int): Bitmap? {
        val path = file.absolutePath

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        var sampleSize = 1
        val largestSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
        while (largestSide / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeFile(path, decodeOptions) ?: return null

        val rotationDegrees = runCatching {
            val exif = ExifInterface(path)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)

        if (rotationDegrees == 0) return bitmap

        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Crops a scanner capture down to the white card frame and writes it to [dest] as an upright JPEG.
     *
     * The preview (PreviewView FILL_CENTER) shows a centre crop of the camera frame scaled to the
     * view, so the visible region is found first, then the frame fractions are applied inside it.
     */
    fun cropToFrame(
        source: File,
        dest: File,
        frame: CardFrame,
        viewWidth: Int,
        viewHeight: Int,
        maxDimension: Int = 2400
    ): Boolean {
        if (viewWidth <= 0 || viewHeight <= 0) return false
        val bitmap = decodeUprightSampledBitmap(source, maxDimension) ?: return false
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()

        val viewAspect = viewWidth.toFloat() / viewHeight
        val visibleW: Float
        val visibleH: Float
        if (bw / bh > viewAspect) {
            visibleH = bh
            visibleW = bh * viewAspect
        } else {
            visibleW = bw
            visibleH = bw / viewAspect
        }
        val offX = (bw - visibleW) / 2f
        val offY = (bh - visibleH) / 2f

        // A little slack so a card held slightly off-frame doesn't lose its edge text.
        val slackX = (frame.right - frame.left) * 0.04f
        val slackY = (frame.bottom - frame.top) * 0.04f
        val left = (offX + (frame.left - slackX).coerceIn(0f, 1f) * visibleW).toInt().coerceIn(0, bitmap.width - 1)
        val top = (offY + (frame.top - slackY).coerceIn(0f, 1f) * visibleH).toInt().coerceIn(0, bitmap.height - 1)
        val right = (offX + (frame.right + slackX).coerceIn(0f, 1f) * visibleW).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (offY + (frame.bottom + slackY).coerceIn(0f, 1f) * visibleH).toInt().coerceIn(top + 1, bitmap.height)

        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        FileOutputStream(dest).use { out -> cropped.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        return true
    }

    /**
     * Upright, downscaled JPEG of a photo for uploading. Camera originals are often
     * several MB, which is slow on expo-floor mobile data; 1600px keeps the card readable.
     */
    fun jpegForUpload(photoPath: String, maxDimension: Int = 1600, quality: Int = 85): ByteArray? {
        if (photoPath.isBlank()) return null
        val file = File(photoPath)
        if (!file.exists()) return null
        val bitmap = decodeUprightSampledBitmap(file, maxDimension) ?: return null
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }
}
