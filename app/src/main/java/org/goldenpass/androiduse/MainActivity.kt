package org.goldenpass.androiduse

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var statusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        statusTextView = TextView(this).apply {
            text = "Accessibility Service Enabled: ${isAccessibilityServiceEnabled(this@MainActivity)}"
            textSize = 18f
        }
        
        val settingsButton = Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val modelLabel = TextView(this).apply {
            text = "Select Model:"
            setPadding(0, 40, 0, 10)
        }

        val modelSpinner = Spinner(this).apply {
            val models = arrayOf("Gemini", "OpenAI")
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, models)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedModel = models[position]
                    UIAgentAccessibilityService.instance?.updateAgent(selectedModel)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        val taskLabel = TextView(this).apply {
            text = "Target Task:"
            setPadding(0, 40, 0, 10)
        }

        val taskEditText = EditText(this).apply {
            hint = "Enter task here..."
            setText("go to contacts, and add a new contact John Smith with email johnsmith123@gmail.com")
            isEnabled = true
            minLines = 5
            gravity = Gravity.TOP
            setBackgroundResource(R.drawable.edit_text_border)
            setPadding(20, 20, 20, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                setMargins(0, 0, 0, 40)
            }
        }

        val runTaskButton = Button(this).apply {
            text = "RUN TASK"
            setOnClickListener {
                val service = UIAgentAccessibilityService.instance
                if (service != null) {
                    val selectedModel = modelSpinner.selectedItem.toString()
                    service.updateAgent(selectedModel)
                    
                    val task = taskEditText.text.toString()
                    service.startAgentLoop(task)
                    Toast.makeText(this@MainActivity, "Agent Started ($selectedModel): Processing task...", Toast.LENGTH_LONG).show()
                    // Send app to background so agent can work on other apps
                    val startMain = Intent(Intent.ACTION_MAIN)
                    startMain.addCategory(Intent.CATEGORY_HOME)
                    startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(startMain)
                } else {
                    Toast.makeText(this@MainActivity, "Please enable Accessibility Service first", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }

        layout.addView(statusTextView)
        layout.addView(settingsButton)
        layout.addView(modelLabel)
        layout.addView(modelSpinner)
        layout.addView(taskLabel)
        layout.addView(taskEditText)
        layout.addView(runTaskButton)

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        if (::statusTextView.isInitialized) {
            statusTextView.text = "Accessibility Service Enabled: ${isAccessibilityServiceEnabled(this)}"
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.name == UIAgentAccessibilityService::class.java.name) {
                return true
            }
        }
        return false
    }
}
