package org.goldenpass.androiduse

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val statusTextView = TextView(this).apply {
            text = "Accessibility Service Enabled: ${isAccessibilityServiceEnabled(this@MainActivity)}"
            textSize = 18f
        }
        
        val settingsButton = Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val taskLabel = TextView(this).apply {
            text = "Target Task:"
            setPadding(0, 40, 0, 10)
        }

        val taskEditText = EditText(this).apply {
            setText("go to contacts, and add a new contact John Smith with email johnsmith123@gmail.com")
            isEnabled = false // Hardcoded for demo
        }

        val runTaskButton = Button(this).apply {
            text = "RUN DEMO TASK"
            setOnClickListener {
                if (isAccessibilityServiceEnabled(this@MainActivity)) {
                    // In a real agent, this would trigger the AI loop.
                    // For now, we'll just show a toast.
                    Toast.makeText(this@MainActivity, "Agent Started: Processing task...", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Please enable Accessibility Service first", Toast.LENGTH_SHORT).show()
                }
            }
        }

        layout.addView(statusTextView)
        layout.addView(settingsButton)
        layout.addView(taskLabel)
        layout.addView(taskEditText)
        layout.addView(runTaskButton)

        setContentView(layout)
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
