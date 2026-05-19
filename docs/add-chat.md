# Implementation Plan - Chat Overlay & Conversation History

This plan transforms the current "one-off task" execution into a persistent conversation between the user and the AI agent, accessible via a floating chat window.

## Proposed Changes

### [NEW] [ChatMessage.kt](file:///Users/t/AndroidStudioProjects/AndroidUse/app/src/main/java/org/goldenpass/androiduse/ChatMessage.kt)
- Define a data class `ChatMessage(val text: String, val isUser: Boolean, val timestamp: Long = System.currentTimeMillis())`.

---

### Agent & History Support

#### [IAgent.kt](file:///Users/t/AndroidStudioProjects/AndroidUse/app/src/main/java/org/goldenpass/androiduse/IAgent.kt)
- Update `getNextAction` to accept a list of `ChatMessage` instead of a single prompt string.
- `suspend fun getNextAction(history: List<ChatMessage>, screenshot: Bitmap, uiTree: String): String?`

#### [GeminiAgent.kt](file:///Users/t/AndroidStudioProjects/AndroidUse/app/src/main/java/org/goldenpass/androiduse/GeminiAgent.kt)
- Update implementation to handle `history`.
- Use Gemini's chat capabilities (or map history to Content objects) to maintain context.

---

### UI - Chat Overlay

#### [UIAgentAccessibilityService.kt](file:///Users/t/AndroidStudioProjects/AndroidUse/app/src/main/java/org/goldenpass/androiduse/UIAgentAccessibilityService.kt)

- **Overlay Bar Updates**:
    - Add a `chatButton` (💬) to the main bar.
    - Implement a toggle for the `chatOverlayView`.
- **Chat Window Implementation**:
    - `showChatOverlay()`: Create a new window (initially hidden or collapsed).
    - Components: `RecyclerView` for message history, `EditText` for user input, and a `Send` button.
    - Style: Semi-transparent dark background matching the main bar.
- **Interference Prevention**:
    - Update `captureScreenshot` to find the `rootInActiveWindow`'s `windowId` and use `takeScreenshotOfWindow(windowId, ...)`.
    - Ensure `getClickableElementsJson` only traverses the active app window, excluding service overlays.
- **Conversation State**:
    - Maintain a `mutableListOf<ChatMessage>` called `conversationHistory`.
    - Every agent "thought" or "action" can optionally be added to history as an AI message.

---

## Verification Plan

### Automated Tests
- None.

### Manual Verification
1.  **Chat Toggle**: Tap 💬 on the overlay bar. Verify the chat window expands/collapses.
2.  **Sending Messages**: Type a task in the chat (e.g., "Open Settings") and hit send.
    - Verify the message appears in the chat UI.
    - Verify the AI starts processing the task.
3.  **Conversation Context**:
    - Start a task ("Go to Contacts").
    - Halfway through, send a chat message: "Actually, open the Clock app instead."
    - Verify the AI switches tasks based on the new context.
4.  **Screenshot Integrity**:
    - Open the chat window so it covers a significant part of the screen.
    - Let the AI perform a click.
    - Verify (via logs) that the AI's "view" of the screenshot did NOT include the chat window.
5.  **Stop Functionality**: Click the red stop button ■. Verify both overlays are handled correctly (stopped, but chat can remain if desired).
