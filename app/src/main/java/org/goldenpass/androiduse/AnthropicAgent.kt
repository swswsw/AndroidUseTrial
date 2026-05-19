package org.goldenpass.androiduse

import android.content.res.Resources
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class AnthropicAgent(apiKey: String, private val modelName: String = "claude-3-5-sonnet-latest") : IAgent {
    
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    private val systemInstructions = """
        <role>You are an expert Android UI Automation Agent.</role>
        
        <goal>
        Complete the user's TASK by analyzing a screenshot and a UI Tree.
        </goal>
        
        <coordinate_system>
        - All coordinates (x, y, startX, startY, endX, endY) MUST be in normalized 0-1000 format.
        - (0, 0) is the top-left corner.
        - (1000, 1000) is the bottom-right corner.
        </coordinate_system>
        
        <ui_tree_data>
        - The UI Tree contains clickable elements and their normalized center coordinates. Use this to help locate precise targets.
        </ui_tree_data>
        
        <instructions>
        Follow these steps for every request:
        1. Use <thinking> tags to analyze the current screen, identify the relevant UI elements from the tree, and plan your next move.
        2. Verify coordinates by cross-referencing the UI Tree and the screenshot.
        3. Output exactly ONE action in the required JSON format.
        </instructions>
        
        <required_json_formats>
        1. CLICK ACTION:
        {
          "thought": "Reasoning for the action.",
          "action": "click",
          "x": 500,
          "y": 500
        }
        
        2. TYPE ACTION:
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
        </required_json_formats>
        
        <rules>
        - Respond ONLY with the thinking block followed by the JSON object. 
        - DO NOT include any text outside of the <thinking> tags and the JSON block.
        - Be precise with coordinates.
        - If the task is finished, return the "done" action.
        </rules>
    """.trimIndent()

    override suspend fun getNextAction(history: List<ChatMessage>, screenshot: Bitmap, uiTree: String): String? = withContext(Dispatchers.IO) {
        val displayMetrics = Resources.getSystem().displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // 1. Process UI Tree into normalized centers (0-1000)
        val normalizedUiTree = normalizeUiTree(uiTree, screenWidth, screenHeight)

        // 2. Resize/Downscale the screenshot
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

        val historyStr = history.joinToString("\n") { 
            if (it.isUser) "USER: ${it.text}" else "AI: ${it.text}"
        }

        val userPrompt = """
            CONVERSATION HISTORY:
            $historyStr
            
            CURRENT UI TREE (Normalized Centers):
            $normalizedUiTree
            
            Based on the history and the current screen, what is the NEXT action?
        """.trimIndent()

        try {
            val textBlock = ContentBlockParam.ofText(
                TextBlockParam.builder()
                    .text(userPrompt)
                    .build()
            )

            val imageBlock = ContentBlockParam.ofImage(
                ImageBlockParam.builder()
                    .source(
                        ImageBlockParam.Source.ofBase64(
                            Base64ImageSource.builder()
                                .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                                .data(base64Image)
                                .build()
                        )
                    )
                    .build()
            )

            val params = MessageCreateParams.builder()
                .model(Model.of(modelName))
                .maxTokens(2048)
                .system(systemInstructions)
                .addMessage(
                    MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .contentOfBlockParams(listOf(imageBlock, textBlock))
                        .build()
                )
                .build()

            val message = client.messages().create(params)
            val rawResult = message.content().joinToString("\n") { block ->
                if (block.isText()) block.asText().text() else ""
            }
            
            Log.d("AnthropicAgent", "REQUEST SEND TO LLM (Model: $modelName)")
            Log.d("AnthropicAgent", "RAW RESPONSE: $rawResult")

            // 3. Post-process the result: Convert 0-1000 back to absolute pixels
            return@withContext denormalizeResponse(rawResult, screenWidth, screenHeight)
        } catch (e: Exception) {
            Log.e("AnthropicAgent", "API Error: ${e.message}", e)
            return@withContext null
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
            Log.e("AnthropicAgent", "Error normalizing UI tree", e)
            return uiTree
        }
    }

    private fun denormalizeResponse(rawResponse: String?, screenWidth: Int, screenHeight: Int): String? {
        if (rawResponse == null) return null
        try {
            // Find JSON block, ignoring the <thinking> block
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
            Log.e("AnthropicAgent", "Error denormalizing response", e)
            return rawResponse
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
