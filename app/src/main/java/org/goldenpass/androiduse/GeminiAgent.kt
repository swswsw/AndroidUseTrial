package org.goldenpass.androiduse

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiAgent(apiKey: String) {
    // Note: Use Firebase.ai for the new Firebase AI Logic SDK.
    // Ensure you have google-services.json and AI enabled in Firebase console.
    private val model = Firebase.ai.generativeModel(
        modelName = "gemini-2.0-flash" 
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
            return@withContext null
        }
    }
}
