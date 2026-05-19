package org.goldenpass.androiduse

import android.graphics.Bitmap

interface IAgent {
    suspend fun getNextAction(history: List<ChatMessage>, screenshot: Bitmap, uiTree: String): String?
}
