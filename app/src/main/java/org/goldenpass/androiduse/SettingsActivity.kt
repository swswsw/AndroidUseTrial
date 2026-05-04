package org.goldenpass.androiduse

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var securityManager: SecurityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        securityManager = SecurityManager(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val title = TextView(this).apply {
            text = "API Key Settings"
            textSize = 24f
            setPadding(0, 0, 0, 40)
        }

        val geminiLabel = TextView(this).apply {
            text = "Gemini API Key:"
        }
        val geminiKeyInput = EditText(this).apply {
            hint = "Enter Gemini API Key"
            setText(securityManager.getGeminiApiKey() ?: "")
        }

        val openAILabel = TextView(this).apply {
            text = "OpenAI API Key:"
            setPadding(0, 30, 0, 0)
        }
        val openAIKeyInput = EditText(this).apply {
            hint = "Enter OpenAI API Key"
            setText(securityManager.getOpenAIApiKey() ?: "")
        }

        val anthropicLabel = TextView(this).apply {
            text = "Anthropic API Key:"
            setPadding(0, 30, 0, 0)
        }
        val anthropicKeyInput = EditText(this).apply {
            hint = "Enter Anthropic API Key"
            setText(securityManager.getAnthropicApiKey() ?: "")
        }

        val saveButton = Button(this).apply {
            text = "Save Keys"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 50, 0, 0)
            }
            setOnClickListener {
                val geminiKey = geminiKeyInput.text.toString().trim()
                val openAIKey = openAIKeyInput.text.toString().trim()
                val anthropicKey = anthropicKeyInput.text.toString().trim()

                securityManager.setGeminiApiKey(geminiKey)
                securityManager.setOpenAIApiKey(openAIKey)
                securityManager.setAnthropicApiKey(anthropicKey)

                Toast.makeText(this@SettingsActivity, "Keys saved securely", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        layout.addView(title)
        layout.addView(geminiLabel)
        layout.addView(geminiKeyInput)
        layout.addView(openAILabel)
        layout.addView(openAIKeyInput)
        layout.addView(anthropicLabel)
        layout.addView(anthropicKeyInput)
        layout.addView(saveButton)

        setContentView(layout)
    }
}
