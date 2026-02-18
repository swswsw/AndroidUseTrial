package org.goldenpass.androiduse

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiAgent(apiKey: String) {
    // Note: 'gemini-2.0-flash' or 'gemini-1.5-flash' are recommended for vision-based UI tasks.
    // The 'computer-use' models require specific tool definitions which are more complex to implement.
    private val model = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    suspend fun getNextAction(prompt: String, screenshot: Bitmap, uiTree: String): String? = withContext(Dispatchers.IO) {
        // --- 1. Resize/Downscale the screenshot to reduce data sent to LLM ---
        val maxDimension = 1024
        val resizedScreenshot = if (screenshot.width > maxDimension || screenshot.height > maxDimension) {
            val scale = maxDimension.toFloat() / Math.max(screenshot.width, screenshot.height)
            val newWidth = (screenshot.width * scale).toInt()
            val newHeight = (screenshot.height * scale).toInt()
            Log.d("GeminiAgent", "Resizing screenshot from ${screenshot.width}x${screenshot.height} to ${newWidth}x${newHeight}")
            Bitmap.createScaledBitmap(screenshot, newWidth, newHeight, true)
        } else {
            screenshot
        }

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

        Log.d("GeminiAgent", "REQUEST SEND TO LLM:")
        Log.d("GeminiAgent", "Prompt: $fullPrompt")
        Log.d("GeminiAgent", "Screenshot: [Resized Bitmap attached]")

        try {
            val response = model.generateContent(
                content {
                    image(resizedScreenshot)
                    text(fullPrompt)
                }
            )
            val result = response.text?.trim()
            Log.d("GeminiAgent", "RESPONSE FROM LLM: ${result ?: "EMPTY RESPONSE"}")
            return@withContext result
        } catch (e: Exception) {
            Log.e("GeminiAgent", "API Error: ${e.message}", e)
            return@withContext null
        } finally {
            // Clean up the resized bitmap if it's a separate instance from the original
            if (resizedScreenshot != screenshot) {
                resizedScreenshot.recycle()
            }
        }
    }
}
