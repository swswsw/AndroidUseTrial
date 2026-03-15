package org.goldenpass.androiduse

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class OpenAIAgent(apiKey: String) : IAgent {
    private val openai = OpenAI(apiKey)

    override suspend fun getNextAction(prompt: String, screenshot: Bitmap, uiTree: String): String? = withContext(Dispatchers.IO) {
        val maxDimension = 1024
        val resizedScreenshot = if (screenshot.width > maxDimension || screenshot.height > maxDimension) {
            val scale = maxDimension.toFloat() / Math.max(screenshot.width, screenshot.height)
            val newWidth = (screenshot.width * scale).toInt()
            val newHeight = (screenshot.height * scale).toInt()
            Bitmap.createScaledBitmap(screenshot, newWidth, newHeight, true)
        } else {
            screenshot
        }

        val base64Image = bitmapToBase64(resizedScreenshot)
        if (resizedScreenshot != screenshot) {
            resizedScreenshot.recycle()
        }

        val systemInstructions = """
            You are an Android UI Agent.
            Analyze the provided screenshot and the UI Tree list.
            Decide on the NEXT SINGLE ACTION to move closer to completing the TASK.
            
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

        val userPrompt = """
            TASK: $prompt
            
            Current UI Tree (Clickable elements):
            $uiTree
        """.trimIndent()

        try {
            val chatCompletionRequest = chatCompletionRequest {
                model = ModelId("gpt-5.4")
                messages {
                    message {
                        role = ChatRole.System
                        content = systemInstructions
                    }
                    message {
                        role = ChatRole.User
                        content {
                            text(userPrompt)
                            image("data:image/jpeg;base64,$base64Image")
                        }
                    }
                }
            }
            val completion = openai.chatCompletion(chatCompletionRequest)
            val result = completion.choices.firstOrNull()?.message?.content?.trim()
            Log.d("OpenAIAgent", "RESPONSE FROM LLM: ${result ?: "EMPTY RESPONSE"}")
            return@withContext result
        } catch (e: Exception) {
            Log.e("OpenAIAgent", "API Error: ${e.message}", e)
            return@withContext null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
