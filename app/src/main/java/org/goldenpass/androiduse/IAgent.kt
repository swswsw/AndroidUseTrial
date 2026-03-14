package org.goldenpass.androiduse

import android.graphics.Bitmap

interface IAgent {
    suspend fun getNextAction(prompt: String, screenshot: Bitmap, uiTree: String): String?
}
