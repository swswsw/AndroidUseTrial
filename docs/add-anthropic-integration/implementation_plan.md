# Add Anthropic Capability to Mobile Agent

This plan outlines the steps to integrate Anthropic's Claude models into the Android UI Agent app. This includes adding the official Anthropic Java SDK, updating security management for API keys, and implementing the `AnthropicAgent` class.

## User Review Required

> [!IMPORTANT]
> - **API Key Security**: You will need to add `anthropic.api.key=YOUR_KEY` to your `local.properties` file for build-time support, or enter it in the app's settings.
> - **Model Selection**: I've included `claude-3-5-sonnet-latest` and `claude-3-7-sonnet-latest` (assuming future-proofing) in the spinner.
> - **System Prompt**: The system prompt for Anthropic will follow the same JSON structure as Gemini and OpenAI for compatibility with the existing action handler.

## Proposed Changes

### Dependencies & Build Config

#### [libs.versions.toml](file:///Users/t/AndroidStudioProjects/AndroidUse/gradle/libs.versions.toml)
- Add Anthropic SDK version and library definition.
```toml
[versions]
anthropic = "2.27.0"

[libraries]
anthropic-java = { group = "com.anthropic", name = "anthropic-java", version.ref = "anthropic" }
```

#### [app/build.gradle.kts](file:///Users/t/AndroidStudioProjects/AndroidUse/app/build.gradle.kts)
- Add `anthropic-java` dependency.
- Add `ANTHROPIC_API_KEY` build config field.

---

### Security & Settings

#### [SecurityManager.kt](file:///Users/t/AndroidStudioProjects/AndroidUse/app/src/main/java/org/goldenpass/androiduse/SecurityManager.kt)
- Add methods to get/set Anthropic API key securely.

#### [SettingsActivity.kt](file:///Users/t/AndroidStudioProjects/AndroidUse/app/src/main/java/org/goldenpass/androiduse/SettingsActivity.kt)
- Add UI field for entering the Anthropic API key.

---

### AI Agent Implementation

#### [NEW] [AnthropicAgent.kt](file:///Users/t/AndroidStudioProjects/AndroidUse/app/src/main/java/org/goldenpass/androiduse/AnthropicAgent.kt)
- Implement `IAgent` interface using Anthropic SDK.
- **Enhanced Prompt Engineering**:
    - **System Prompt Structure**:
      ```xml
      <role>You are an expert Android UI Automation Agent.</role>
      <instructions>
      Complete the user's TASK by analyzing the provided screenshot and UI Tree.
      Follow these steps:
      1. Use <thinking> tags to analyze the UI, identify targets, and verify coordinates.
      2. Output the final action in the required JSON format.
      </instructions>
      <rules>
      - COORDINATE SYSTEM: Normalized 0-1000.
      - RESPONSE FORMAT: JSON ONLY (after thinking).
      - IMPORTANT: Respond ONLY with the JSON object after your reasoning.
      </rules>
      ```
    - **Logic**: Use the official Anthropic SDK's `messages().create()` with the thinking block enabled by simply allowing the model to output text before the JSON.
- Handle image encoding: Convert `Bitmap` to Base64 for the Anthropic `image` content block.

#### [UIAgentAccessibilityService.kt](file:///Users/t/AndroidStudioProjects/AndroidUse/app/src/main/java/org/goldenpass/androiduse/UIAgentAccessibilityService.kt)
- Update `updateAgent` to handle models starting with "claude".

#### [MainActivity.kt](file:///Users/t/AndroidStudioProjects/AndroidUse/app/src/main/java/org/goldenpass/androiduse/MainActivity.kt)
- Add Claude models to the selection spinner.

---

## Verification Plan

### Automated Tests
- No existing unit tests for agents were found, but I will verify the build succeeds after adding the new dependency.
- Run `./gradlew assembleDebug` to ensure compilation.

### Manual Verification
- **Settings**: Verify the Anthropic API key can be saved and retrieved.
- **Model Selection**: Verify "claude-3-5-sonnet" appears in the spinner.
- **Agent Loop**: Run a simple task (e.g., "open settings") with Anthropic selected and verify it captures screenshots and sends requests to the Anthropic API (logcat check).
- **Logcat Monitoring**: Monitor logs for "AnthropicAgent" to verify request/response cycle.
