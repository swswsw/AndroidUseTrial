package org.goldenpass.androiduse

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiAgent(apiKey: String) {
    private val model = GenerativeModel(
        modelName = "gemini-1.5-pro",
        apiKey = apiKey
    )

    suspend fun getNextAction(prompt: String, screenshot: Bitmap, uiTree: String): String? = withContext(Dispatchers.IO) {
        val fullPrompt = """
            $prompt
            
            Current UI Tree (Clickable elements):
            $uiTree
            
            Analyze the screenshot and UI tree. Return the next action in JSON format:
            { "action": "click", "x": 100, "y": 200 } or { "action": "swipe", "startX": 500, "startY": 1000, "endX": 500, "endY": 200 } or { "action": "done" }
        """.trimIndent()

        val response = model.generateContent(
            content {
                image(screenshot)
                text(fullPrompt)
            }
        )
        return@withContext response.text
    }
}
