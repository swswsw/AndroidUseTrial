package org.goldenpass.androiduse

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executor

class UIAgentAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var geminiAgent: GeminiAgent? = null
    private var isProcessing = false

    companion object {
        var instance: UIAgentAccessibilityService? = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events here
    }

    override fun onInterrupt() {
        Log.e("UIAgentAccessibilityService", "Service Interrupted")
        instance = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("UIAgentAccessibilityService", "Service Connected")
        instance = this

        // Firebase auto-initializes via the google-services plugin and google-services.json
        val apiKey = BuildConfig.GEMINI_API_KEY
        geminiAgent = GeminiAgent(apiKey)
    }

    fun startAgentLoop(taskDescription: String) {
        if (isProcessing) return
        isProcessing = true
        
        serviceScope.launch {
            processNextStep(taskDescription)
        }
    }

    private suspend fun processNextStep(taskDescription: String) {
        if (!isProcessing) return

        Log.d("UIAgentAccessibilityService", "Capturing screen for next step...")
        
        captureScreenshot(mainExecutor) { bitmap ->
            if (bitmap == null) {
                Log.e("UIAgentAccessibilityService", "Failed to capture screenshot")
                isProcessing = false
                return@captureScreenshot
            }

            val uiTree = getClickableElementsJson()
            
            serviceScope.launch {
                val agentResponse = geminiAgent?.getNextAction(taskDescription, bitmap, uiTree)
                if (agentResponse != null) {
                    handleAgentAction(agentResponse, taskDescription)
                } else {
                    Log.e("UIAgentAccessibilityService", "No response from Gemini")
                    isProcessing = false
                }
            }
        }
    }

    private suspend fun handleAgentAction(jsonResponse: String, taskDescription: String) {
        try {
            val jsonStr = if (jsonResponse.contains("{")) {
                jsonResponse.substring(jsonResponse.indexOf("{"), jsonResponse.lastIndexOf("}") + 1)
            } else jsonResponse

            val json = JSONObject(jsonStr)
            val action = json.optString("action")

            Log.d("UIAgentAccessibilityService", "Agent decided: $action")

            when (action) {
                "click" -> {
                    val x = json.getDouble("x").toFloat()
                    val y = json.getDouble("y").toFloat()
                    performClickAt(x, y)
                    delay(2000) // Wait for UI to settle
                    processNextStep(taskDescription)
                }
                "swipe" -> {
                    val startX = json.getDouble("startX").toFloat()
                    val startY = json.getDouble("startY").toFloat()
                    val endX = json.getDouble("endX").toFloat()
                    val endY = json.getDouble("endY").toFloat()
                    performSwipe(startX, startY, endX, endY)
                    delay(2000)
                    processNextStep(taskDescription)
                }
                "done" -> {
                    Log.i("UIAgentAccessibilityService", "Task completed!")
                    isProcessing = false
                }
                else -> {
                    Log.w("UIAgentAccessibilityService", "Unknown action: $action")
                    isProcessing = false
                }
            }
        } catch (e: Exception) {
            Log.e("UIAgentAccessibilityService", "Error parsing agent action", e)
            isProcessing = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun captureScreenshot(executor: Executor, callback: (Bitmap?) -> Unit) {
        takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                callback(bitmap)
            }

            override fun onFailure(errorCode: Int) {
                Log.e("UIAgentAccessibilityService", "Screenshot failed with error code: $errorCode")
                callback(null)
            }
        })
    }

    fun performClickAt(x: Float, y: Float) {
        val clickPath = Path()
        clickPath.moveTo(x, y)
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(clickPath, 0, 100))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 500L) {
        val swipePath = Path()
        swipePath.moveTo(startX, startY)
        swipePath.lineTo(endX, endY)
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(swipePath, 0, duration))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    fun getClickableElementsJson(): String {
        val rootNode = rootInActiveWindow ?: return "[]"
        val clickableItems = JSONArray()
        traverseAndCollectClickable(rootNode, clickableItems)
        return clickableItems.toString()
    }

    private fun traverseAndCollectClickable(node: AccessibilityNodeInfo?, items: JSONArray) {
        if (node == null) return
        if (node.isClickable) {
            val item = JSONObject()
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            item.put("text", node.text?.toString() ?: "")
            item.put("contentDescription", node.contentDescription?.toString() ?: "")
            item.put("bounds", "[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]")
            items.put(item)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverseAndCollectClickable(child, items)
            child?.recycle()
        }
    }
}
