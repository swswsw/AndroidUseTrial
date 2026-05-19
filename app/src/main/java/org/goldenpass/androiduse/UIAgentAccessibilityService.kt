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
import android.view.MotionEvent
import android.widget.TextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Toast
import android.util.TypedValue
import android.view.accessibility.AccessibilityWindowInfo
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
    private var agent: IAgent? = null
    private var isProcessing = false
    private lateinit var windowManager: WindowManager
    
    private var overlayView: View? = null
    private lateinit var statusTextView: TextView
    private lateinit var stepTextView: TextView
    private lateinit var modelTextView: TextView
    private var currentModelName: String = ""
    private var conversationHistory = mutableListOf<ChatMessage>()
    private var chatOverlayView: View? = null
    private var isChatVisible = false
    private lateinit var chatMessageContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView

    private var currentStepCount = 0
    private var lastActionJson: String? = null
    private var repeatCount = 0
    private val MAX_STEPS = 30
    private val MAX_REPEATS = 7

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
        
        // Default model
        updateAgent("gemini-3.1-pro-preview")
    }

    fun updateAgent(modelName: String) {
        currentModelName = modelName
        updateOverlay(model = modelName)
        val securityManager = SecurityManager(this)
        if (modelName.startsWith("gemini")) {
            val apiKey = securityManager.getGeminiApiKey()
            if (apiKey != null) {
                agent = GeminiAgent(apiKey, modelName)
                Log.d("UIAgentAccessibilityService", "Agent updated to Gemini ($modelName)")
            } else {
                Log.e("UIAgentAccessibilityService", "Gemini API Key is missing!")
            }
        } else if (modelName.startsWith("gpt")) {
            val apiKey = securityManager.getOpenAIApiKey()
            if (apiKey != null) {
                agent = OpenAIAgent(apiKey, modelName)
                Log.d("UIAgentAccessibilityService", "Agent updated to OpenAI ($modelName)")
            } else {
                Log.e("UIAgentAccessibilityService", "OpenAI API Key is missing!")
            }
        } else if (modelName.startsWith("claude")) {
            val apiKey = securityManager.getAnthropicApiKey()
            if (apiKey != null) {
                agent = AnthropicAgent(apiKey, modelName)
                Log.d("UIAgentAccessibilityService", "Agent updated to Anthropic ($modelName)")
            } else {
                Log.e("UIAgentAccessibilityService", "Anthropic API Key is missing!")
            }
        } else {
            Log.e("UIAgentAccessibilityService", "Unknown model type: $modelName")
        }
    }

    fun startAgentLoop(taskDescription: String) {
        if (isProcessing) return
        isProcessing = true
        currentStepCount = 0
        lastActionJson = null
        repeatCount = 0
        
        if (conversationHistory.none { it.isUser && it.text == taskDescription }) {
            conversationHistory.add(ChatMessage(taskDescription, true))
            updateChatUI()
        }
        
        showOverlay()
        updateOverlay("Starting...", 0, currentModelName)
        
        serviceScope.launch {
            processNextStep()
        }
    }

    private suspend fun processNextStep() {
        if (!isProcessing) return
        
        currentStepCount++
        if (currentStepCount > MAX_STEPS) {
            stopWithNotification("Task timed out: Maximum steps ($MAX_STEPS) reached.")
            return
        }

        updateOverlay("Capturing screen...", currentStepCount)
        Log.d("UIAgentAccessibilityService", "Capturing screen for step $currentStepCount...")
        
        val targetWindowId = rootInActiveWindow?.windowId ?: -1
        
        captureScreenshot(mainExecutor, targetWindowId) { bitmap ->
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
                updateOverlay("Thinking...", currentStepCount)
                val agentResponse = agent?.getNextAction(conversationHistory, softwareBitmap, uiTree)
                if (agentResponse != null) {
                    handleAgentAction(agentResponse)
                } else {
                    Log.e("UIAgentAccessibilityService", "No response from Agent")
                    isProcessing = false
                }
            }
        }
    }

    private suspend fun handleAgentAction(jsonResponse: String) {
        try {
            val jsonStr = if (jsonResponse.contains("{")) {
                jsonResponse.substring(jsonResponse.indexOf("{"), jsonResponse.lastIndexOf("}") + 1)
            } else jsonResponse

            val json = JSONObject(jsonStr)
            val action = json.optString("action")
            val thought = json.optString("thought", "No reasoning provided")
            
            // Add AI thought to conversation if it's new
            if (thought.isNotBlank()) {
                conversationHistory.add(ChatMessage(thought, false))
                updateChatUI()
            }
            val actionContent = JSONObject(json.toString()).apply { remove("thought") }.toString()
            if (actionContent == lastActionJson) {
                repeatCount++
                if (repeatCount >= MAX_REPEATS) {
                    stopWithNotification("Agent is stuck in a loop. Same action repeated $MAX_REPEATS times.")
                    return
                }
            } else {
                repeatCount = 0
            }
            lastActionJson = actionContent

            Log.i("UIAgentAccessibilityService", "Agent Thought: $thought")
            Log.d("UIAgentAccessibilityService", "Agent decided: $action")

            when (action) {
                "click" -> {
                    val x = json.getDouble("x").toFloat()
                    val y = json.getDouble("y").toFloat()
                    updateOverlay("Clicking at ($x, $y)", currentStepCount)
                    showVisualCue(x, y, Color.RED)
                    delay(800)
                    performClickAt(x, y)
                    delay(2000)
                    processNextStep()
                }
                "type" -> {
                    val text = json.getString("text")
                    updateOverlay("Typing: $text", currentStepCount)
                    showTypeCue()
                    delay(800)
                    typeText(text)
                    delay(2000)
                    processNextStep()
                }
                "swipe" -> {
                    val startX = json.getDouble("startX").toFloat()
                    val startY = json.getDouble("startY").toFloat()
                    val endX = json.getDouble("endX").toFloat()
                    val endY = json.getDouble("endY").toFloat()
                    updateOverlay("Swiping...", currentStepCount)
                    showVisualCue(startX, startY, Color.GREEN)
                    delay(500)
                    showVisualCue(endX, endY, Color.YELLOW)
                    delay(300)
                    performSwipe(startX, startY, endX, endY)
                    delay(2000)
                    processNextStep()
                }
                "done" -> {
                    Log.i("UIAgentAccessibilityService", "Task completed!")
                    conversationHistory.add(ChatMessage("Task Completed Successfully!", false))
                    updateChatUI()
                    stopWithNotification("Task Completed Successfully!")
                }
                else -> {
                    Log.w("UIAgentAccessibilityService", "Unknown action: $action")
                    conversationHistory.add(ChatMessage("Error: Unknown action received: $action", false))
                    updateChatUI()
                    stopWithNotification("Unknown action received: $action")
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
    fun captureScreenshot(executor: Executor, windowId: Int = -1, callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && windowId != -1) {
            takeScreenshotOfWindow(windowId, executor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                    callback(bitmap)
                }
                override fun onFailure(errorCode: Int) {
                    Log.e("UIAgentAccessibilityService", "Window screenshot failed ($errorCode), falling back")
                    captureScreenshotLegacy(executor, callback)
                }
            })
        } else {
            captureScreenshotLegacy(executor, callback)
        }
    }

    private fun captureScreenshotLegacy(executor: Executor, callback: (Bitmap?) -> Unit) {
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

    private fun stopWithNotification(message: String) {
        Log.w("UIAgentAccessibilityService", message)
        isProcessing = false
        hideOverlay()
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC222222"))
                cornerRadius = 32f
            }
            setPadding(24, 16, 24, 16)
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dragHandle = TextView(this).apply {
            text = "⠿"
            setTextColor(Color.WHITE)
            textSize = 24f
            setPadding(0, 0, 20, 0)
        }

        modelTextView = TextView(this).apply {
            text = "🤖 $currentModelName"
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val chatButton = TextView(this).apply {
            text = "💬"
            textSize = 20f
            setPadding(20, 0, 20, 0)
            setOnClickListener {
                toggleChat()
            }
        }

        stepTextView = TextView(this).apply {
            text = "Step 0/$MAX_STEPS"
            setTextColor(Color.LTGRAY)
            textSize = 14f
            setPadding(20, 0, 20, 0)
        }

        val stopButton = Button(this).apply {
            text = "■"
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            textSize = 18f
            setOnClickListener {
                stopWithNotification("Agent stopped by user.")
            }
            // Make it square
            val buttonSize = (40 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
        }

        topRow.addView(dragHandle)
        topRow.addView(modelTextView)
        topRow.addView(chatButton)
        topRow.addView(stepTextView)
        topRow.addView(stopButton)

        statusTextView = TextView(this).apply {
            text = "Initializing..."
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, 8, 0, 0)
        }

        root.addView(topRow)
        root.addView(statusTextView)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 100
            width = (resources.displayMetrics.widthPixels * 0.95).toInt()
        }

        dragHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(root, params)
                        
                        // Sync chat overlay position
                        if (isChatVisible) {
                            updateChatPosition(params.x, params.y + root.height + 20)
                        }
                        return true
                    }
                }
                return false
            }
        })

        overlayView = root
        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e("UIAgentAccessibilityService", "Error adding overlay", e)
        }
    }

    private fun toggleChat() {
        if (isChatVisible) {
            hideChatOverlay()
        } else {
            showChatOverlay()
        }
    }

    private fun showChatOverlay() {
        if (chatOverlayView != null) return
        isChatVisible = true

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE111111"))
                cornerRadius = 24f
            }
            setPadding(20, 20, 20, 20)
        }

        chatScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.4).toInt()
            )
        }

        chatMessageContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        chatScrollView.addView(chatMessageContainer)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 0)
        }

        val inputField = EditText(this).apply {
            hint = "Send instruction..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val sendButton = Button(this).apply {
            text = "SEND"
            setOnClickListener {
                val text = inputField.text.toString()
                if (text.isNotBlank()) {
                    conversationHistory.add(ChatMessage(text, true))
                    inputField.setText("")
                    updateChatUI()
                    if (!isProcessing) {
                        startAgentLoop(text)
                    }
                }
            }
        }

        inputRow.addView(inputField)
        inputRow.addView(sendButton)

        root.addView(chatScrollView)
        root.addView(inputRow)

        val barParams = overlayView?.layoutParams as? WindowManager.LayoutParams
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // Allow interaction with keyboard
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = barParams?.x ?: 0
            y = (barParams?.y ?: 100) + (overlayView?.height ?: 200) + 20
        }

        chatOverlayView = root
        windowManager.addView(chatOverlayView, params)
        updateChatUI()
    }

    private fun hideChatOverlay() {
        chatOverlayView?.let {
            windowManager.removeView(it)
            chatOverlayView = null
        }
        isChatVisible = false
    }

    private fun updateChatPosition(x: Int, y: Int) {
        chatOverlayView?.let {
            val params = it.layoutParams as WindowManager.LayoutParams
            params.x = x
            params.y = y
            windowManager.updateViewLayout(it, params)
        }
    }

    private fun updateChatUI() {
        Handler(Looper.getMainLooper()).post {
            if (!::chatMessageContainer.isInitialized) return@post
            chatMessageContainer.removeAllViews()
            conversationHistory.takeLast(10).forEach { msg ->
                val tv = TextView(this).apply {
                    text = if (msg.isUser) "👤: ${msg.text}" else "🤖: ${msg.text}"
                    setTextColor(if (msg.isUser) Color.CYAN else Color.WHITE)
                    setPadding(0, 5, 0, 5)
                    textSize = 13f
                }
                chatMessageContainer.addView(tv)
            }
            chatScrollView.post { chatScrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun hideOverlay() {
        hideChatOverlay()
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e("UIAgentAccessibilityService", "Error removing overlay", e)
            }
            overlayView = null
        }
    }

    private fun updateOverlay(status: String? = null, step: Int? = null, model: String? = null) {
        Handler(Looper.getMainLooper()).post {
            if (overlayView == null) return@post
            status?.let { statusTextView.text = it }
            step?.let { stepTextView.text = "Step $it/$MAX_STEPS" }
            model?.let { modelTextView.text = "🤖 $it" }
        }
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
        // Ensure we are only traversing the target app's window
        if (rootNode.packageName == packageName) {
            Log.w("UIAgentAccessibilityService", "Root node belongs to our service, skipping UI tree")
            return "[]"
        }
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
