package org.goldenpass.androiduse

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

object BitmapUtils {
    /**
     * Converts a Bitmap to a Base64 encoded JPEG string.
     */
    fun toBase64Jpeg(bitmap: Bitmap, quality: Int = 80): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
