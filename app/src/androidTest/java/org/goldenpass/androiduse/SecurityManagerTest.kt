package org.goldenpass.androiduse

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityManagerTest {

    private lateinit var securityManager: SecurityManager

    @Before
    fun setup() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        securityManager = SecurityManager(appContext)
    }

    @Test
    fun testSaveAndRetrieveGeminiApiKey() {
        val testKey = "AIzaSy-TEST-GEMINI-KEY"
        securityManager.setGeminiApiKey(testKey)
        
        val retrievedKey = securityManager.getGeminiApiKey()
        assertEquals("Gemini API Key should match the saved value", testKey, retrievedKey)
    }

    @Test
    fun testSaveAndRetrieveOpenAIApiKey() {
        val testKey = "sk-TEST-OPENAI-KEY-123456789"
        securityManager.setOpenAIApiKey(testKey)
        
        val retrievedKey = securityManager.getOpenAIApiKey()
        assertEquals("OpenAI API Key should match the saved value", testKey, retrievedKey)
    }

    @Test
    fun testEmptyKeyReturnsFallback() {
        // Clearing keys to test fallback (or null if fallback is also empty)
        securityManager.setGeminiApiKey("")
        
        val retrievedKey = securityManager.getGeminiApiKey()
        // It should either be the BuildConfig value or null, but NOT empty string
        assertTrue("Retrieved key should not be empty string", retrievedKey != "")
    }
}
