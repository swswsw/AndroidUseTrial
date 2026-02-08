package org.goldenpass.androiduse

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiAgent(apiKey: String) {
    // Using Gemini 2.0 Pro Experimental - optimized for complex reasoning and "computer use" tasks
    private val model = GenerativeModel(
        modelName = "gemini-2.0-pro-exp-02-05", 
        apiKey = apiKey
    )

    suspend fun getNextAction(prompt: String, screenshot: Bitmap, uiTree: String): String? = withContext(Dispatchers.IO) {
        val fullPrompt = """
            TASK: $prompt
            
            Current UI Tree (Clickable elements):
            $uiTree
            
            Analyze the screenshot and UI tree. Return the next action in JSON format:
            { "action": "click", "x": 100, "y": 200 } 
            { "action": "swipe", "startX": 500, "startY": 1000, "endX": 500, "endY": 200 } 
            { "action": "done" }
            
            Important: 
            - The coordinates (x, y) should be in absolute pixels based on the screen.
            - Provide ONLY the JSON object for the next immediate step.
        """.trimIndent()

        try {
            val response = model.generateContent(
                content {
                    image(screenshot)
                    text(fullPrompt)
                }
            )
            val result = response.text?.trim()
            Log.d("GeminiAgent", "Raw Response: ${result ?: "EMPTY"}")
            return@withContext result
        } catch (e: Exception) {
            Log.e("GeminiAgent", "Error generating content", e)
            // Fallback to 1.5 Pro if 2.0 Pro Experimental is not available for this key
            return@withContext tryFallback(prompt, screenshot, uiTree)
        }
    }

    private suspend fun tryFallback(prompt: String, screenshot: Bitmap, uiTree: String): String? {
        Log.w("GeminiAgent", "Falling back to gemini-1.5-pro")
        val fallbackModel = GenerativeModel("gemini-1.5-pro", model.apiKey)
        val response = fallbackModel.generateContent(content { image(screenshot); text(prompt + "\n" + uiTree) })
        return response.text
    }
}
