package org.goldenpass.androiduse

import android.content.res.Resources
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GeminiAgent(apiKey: String) : IAgent {
    // Note: 'gemini-2.0-flash' or 'gemini-1.5-flash' are recommended for vision-based UI tasks.
    private val model = GenerativeModel(
        modelName = "gemini-3.1-pro-preview",
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    override suspend fun getNextAction(prompt: String, screenshot: Bitmap, uiTree: String): String? = withContext(Dispatchers.IO) {
        val displayMetrics = Resources.getSystem().displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // 1. Process UI Tree into normalized centers (0-1000)
        val normalizedUiTree = normalizeUiTree(uiTree, screenWidth, screenHeight)

        // 2. Resize/Downscale the screenshot to reduce data sent to LLM
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

        val systemInstructions = """
            You are an expert Android UI Automation Agent.
            Your goal is to complete a user-specified TASK by analyzing a screenshot and a UI Tree.
            
            COORDINATE SYSTEM:
            - All coordinates (x, y, startX, startY, endX, endY) MUST be in normalized 0-1000 format.
            - (0, 0) is the top-left corner.
            - (1000, 1000) is the bottom-right corner.
            
            UI TREE DATA:
            - The UI Tree contains clickable elements and their normalized center coordinates. Use this to help locate precise targets.
            
            REQUIRED RESPONSE FORMAT (JSON ONLY):
            You must respond with a SINGLE JSON object in one of these formats:
            
            1. CLICK ACTION:
            {
              "thought": "Reasoning for the action.",
              "action": "click",
              "x": 500,
              "y": 500
            }
            
            2. TYPE ACTION (Use this after clicking/focusing an input field):
            {
              "thought": "Reasoning for the action.",
              "action": "type",
              "text": "text to type"
            }
            
            3. SWIPE ACTION:
            {
              "thought": "Reasoning for the action.",
              "action": "swipe",
              "startX": 500,
              "startY": 800,
              "endX": 500,
              "endY": 200
            }
            
            4. DONE:
            {
              "thought": "Task is complete.",
              "action": "done"
            }
            
            IMPORTANT RULES:
            - Respond ONLY with the JSON object. No other text.
            - Be precise with coordinates.
            - If the task is finished, return the "done" action.
        """.trimIndent()

        val fullPrompt = """
            TASK: $prompt
            
            CURRENT UI TREE (Normalized Centers):
            $normalizedUiTree
            
            $systemInstructions
        """.trimIndent()

        Log.d("GeminiAgent", "REQUEST SEND TO LLM:")
        Log.d("GeminiAgent", "Prompt: $fullPrompt")

        try {
            val response = model.generateContent(
                content {
                    image(resizedScreenshot)
                    text(fullPrompt)
                }
            )
            val result = response.text?.trim()
            Log.d("GeminiAgent", "RESPONSE FROM LLM: ${result ?: "EMPTY RESPONSE"}")
            
            // 3. Post-process the result: Convert 0-1000 back to absolute pixels
            return@withContext denormalizeResponse(result, screenWidth, screenHeight)
        } catch (e: Exception) {
            Log.e("GeminiAgent", "API Error: ${e.message}", e)
            return@withContext null
        } finally {
            if (resizedScreenshot != screenshot) {
                resizedScreenshot.recycle()
            }
        }
    }

    private fun normalizeUiTree(uiTree: String, screenWidth: Int, screenHeight: Int): String {
        try {
            val originalArray = JSONArray(uiTree)
            val normalizedArray = JSONArray()
            for (i in 0 until originalArray.length()) {
                val item = originalArray.getJSONObject(i)
                val boundsStr = item.optString("bounds", "")
                
                if (boundsStr.isNotEmpty()) {
                    val regex = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]")
                    val match = regex.find(boundsStr)
                    if (match != null) {
                        val left = match.groupValues[1].toInt()
                        val top = match.groupValues[2].toInt()
                        val right = match.groupValues[3].toInt()
                        val bottom = match.groupValues[4].toInt()
                        
                        val centerX = (left + right) / 2
                        val centerY = (top + bottom) / 2
                        
                        val nx = (centerX * 1000 / screenWidth).coerceIn(0, 1000)
                        val ny = (centerY * 1000 / screenHeight).coerceIn(0, 1000)
                        
                        val normalizedItem = JSONObject()
                        normalizedItem.put("text", item.optString("text"))
                        normalizedItem.put("contentDescription", item.optString("contentDescription"))
                        normalizedItem.put("center", "($nx, $ny)")
                        normalizedArray.put(normalizedItem)
                    }
                }
            }
            return normalizedArray.toString(2)
        } catch (e: Exception) {
            Log.e("GeminiAgent", "Error normalizing UI tree", e)
            return uiTree
        }
    }

    private fun denormalizeResponse(rawResponse: String?, screenWidth: Int, screenHeight: Int): String? {
        if (rawResponse == null) return null
        try {
            val jsonStr = if (rawResponse.contains("{")) {
                rawResponse.substring(rawResponse.indexOf("{"), rawResponse.lastIndexOf("}") + 1)
            } else rawResponse

            val json = JSONObject(jsonStr)
            val action = json.optString("action")
            
            if (action == "click") {
                val nx = json.optDouble("x", -1.0)
                val ny = json.optDouble("y", -1.0)
                if (nx >= 0 && ny >= 0) {
                    val x = (nx / 1000.0 * screenWidth).toInt()
                    val y = (ny / 1000.0 * screenHeight).toInt()
                    json.put("x", x)
                    json.put("y", y)
                }
            } else if (action == "swipe") {
                val nsx = json.optDouble("startX", -1.0)
                val nsy = json.optDouble("startY", -1.0)
                val nex = json.optDouble("endX", -1.0)
                val ney = json.optDouble("endY", -1.0)
                
                if (nsx >= 0 && nsy >= 0 && nex >= 0 && ney >= 0) {
                    json.put("startX", (nsx / 1000.0 * screenWidth).toInt())
                    json.put("startY", (nsy / 1000.0 * screenHeight).toInt())
                    json.put("endX", (nex / 1000.0 * screenWidth).toInt())
                    json.put("endY", (ney / 1000.0 * screenHeight).toInt())
                }
            }
            return json.toString()
        } catch (e: Exception) {
            Log.e("GeminiAgent", "Error denormalizing response", e)
            return rawResponse
        }
    }
}
