package org.goldenpass.androiduse

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
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
    private lateinit var windowManager: WindowManager

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
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNotEmpty()) {
            geminiAgent = GeminiAgent(apiKey)
        } else {
            Log.e("UIAgentAccessibilityService", "Gemini API Key is missing! Add it to local.properties")
        }
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

            // Convert Hardware Bitmap to Software Bitmap for better SDK compatibility
            val softwareBitmap = try {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } catch (e: Exception) {
                Log.e("UIAgentAccessibilityService", "Failed to convert bitmap", e)
                bitmap
            }

            val uiTree = getClickableElementsJson()
            
            serviceScope.launch {
                val agentResponse = geminiAgent?.getNextAction(taskDescription, softwareBitmap, uiTree)
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
            val thought = json.optString("thought", "No reasoning provided")

            Log.i("UIAgentAccessibilityService", "Agent Thought: $thought")
            Log.d("UIAgentAccessibilityService", "Agent decided: $action")

            when (action) {
                "click" -> {
                    val x = json.getDouble("x").toFloat()
                    val y = json.getDouble("y").toFloat()
                    showVisualCue(x, y, Color.RED)
                    delay(800)
                    performClickAt(x, y)
                    delay(2000)
                    processNextStep(taskDescription)
                }
                "type" -> {
                    val text = json.getString("text")
                    showTypeCue()
                    delay(800)
                    typeText(text)
                    delay(2000)
                    processNextStep(taskDescription)
                }
                "swipe" -> {
                    val startX = json.getDouble("startX").toFloat()
                    val startY = json.getDouble("startY").toFloat()
                    val endX = json.getDouble("endX").toFloat()
                    val endY = json.getDouble("endY").toFloat()
                    showVisualCue(startX, startY, Color.GREEN)
                    delay(500)
                    showVisualCue(endX, endY, Color.YELLOW)
                    delay(300)
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

    private fun showVisualCue(x: Float, y: Float, color: Int) {
        val size = 60
        val view = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(4, Color.WHITE)
            }
            alpha = 0.7f
        }

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = (x - size / 2).toInt()
            this.y = (y - size / 2).toInt()
        }

        try {
            windowManager.addView(view, params)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    Log.e("UIAgentAccessibilityService", "Error removing visual cue", e)
                }
            }, 1000)
        } catch (e: Exception) {
            Log.e("UIAgentAccessibilityService", "Error showing visual cue", e)
        }
    }

    private fun showTypeCue() {
        val rootNode = rootInActiveWindow ?: return
        val focusedNode = findFocusedNode(rootNode)
        if (focusedNode != null) {
            val bounds = Rect()
            focusedNode.getBoundsInScreen(bounds)
            showVisualCue(bounds.centerX().toFloat(), bounds.centerY().toFloat(), Color.BLUE)
            focusedNode.recycle()
        }
    }

    /**
     * Types text into the currently focused input field.
     */
    private fun typeText(text: String) {
        val rootNode = rootInActiveWindow ?: return
        val focusedNode = findFocusedNode(rootNode)
        if (focusedNode != null) {
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            focusedNode.recycle()
        } else {
            Log.w("UIAgentAccessibilityService", "No focused node found to type text into.")
        }
    }

    private fun findFocusedNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val focused = findFocusedNode(child)
            if (focused != null) return focused
            child.recycle()
        }
        return null
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
