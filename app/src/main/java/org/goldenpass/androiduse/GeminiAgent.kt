package org.goldenpass.androiduse

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiAgent(apiKey: String) {
    // Using the most current Gemini 3 models as specified
    private val model = GenerativeModel(
        modelName = "gemini-2.5-computer-use-preview-10-2025",
        apiKey = apiKey
    )

    suspend fun getNextAction(prompt: String, screenshot: Bitmap, uiTree: String): String? = withContext(Dispatchers.IO) {
        val fullPrompt = """
            TASK: $prompt
            
            Current UI Tree (Clickable elements):
            $uiTree
            
            INSTRUCTIONS:
            1. You are an Android UI Agent.
            2. Analyze the provided screenshot and the UI Tree list.
            3. Decide on the NEXT SINGLE ACTION to move closer to completing the TASK.
            
            REQUIRED RESPONSE FORMAT (JSON ONLY):
            {
              "thought": "Brief explanation of what you see and why you are taking this action.",
              "action": "click", "x": <px>, "y": <px>
            }
            OR
            {
              "thought": "Entering text into a focused field.",
              "action": "type", "text": "string to type"
            }
            OR
            {
              "thought": "Brief explanation of why you are swiping.",
              "action": "swipe", "startX": <px>, "startY": <px>, "endX": <px>, "endY": <px>
            }
            OR
            {
              "thought": "Task is finished.",
              "action": "done"
            }
            
            Important: 
            - Coordinates must be in absolute pixels for a 1080x2400 screen.
            - The 'type' action should be used after clicking on an input field to focus it.
            - Respond ONLY with the JSON object.
        """.trimIndent()

        Log.d("GeminiAgent", "Sending request to Gemini 3 Flash Preview...")

        try {
            val response = model.generateContent(
                content {
                    image(screenshot)
                    text(fullPrompt)
                }
            )
            val result = response.text?.trim()
            Log.d("GeminiAgent", "Raw Response: ${result ?: "EMPTY RESPONSE"}")
            return@withContext result
        } catch (e: Exception) {
            Log.e("GeminiAgent", "API Error: ${e.message}", e)
            return@withContext null
        }
    }
}
