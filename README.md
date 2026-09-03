# BlackBerrySmartBridge

BlackBerrySmartBridge is an advanced Android companion application designed to transform a BlackBerry Bold 9790 (running BBOS 7.1) into a modern smartwatch/companion terminal.

## Architecture

The system operates on a rigorous client-server architecture communicating entirely via **Bluetooth Classic (RFCOMM/SPP)**.
- **Android Device**: Acts as the "Brain" and proxy, exposing modern Android APIs (Notifications, Telecom, MediaSession, Contacts).
- **BlackBerry Bold 9790**: Acts as the remote terminal.

### Communication Protocol (BSB/1)
Messages are formatted as strictly delimited text payloads ending with `\n`.
Format: `COMMAND|ARG1|ARG2|...`

Example handshake:
```
[BB] ANDROID <- HELLO|BSB/1|BLACKBERRY_9790
[Android] -> HELLO|BSB/1|ANDROID_DEVICE
[BB] ANDROID <- READY
```

## Features and Android Capabilities (Status Report)

This application adheres to strict modern Android security guidelines. No fake APIs or hacks are utilized. 

1. **Call Management** 
   - *Status*: `RESTRICTED` (Fully Supported if Permissions Granted)
   - *Details*: Uses `TelecomManager`. Answering calls requires `ANSWER_PHONE_CALLS`. Rejecting calls requires API 28+. Handled gracefully with fallback errors.

2. **Media Control**
   - *Status*: `SUPPORTED`
   - *Details*: Bridges via `MediaSessionManager`. Relies on the active `NotificationListenerService` permission to access transport controls across any media app (Spotify, YouTube, etc.).

3. **Background Notifications**
   - *Status*: `SUPPORTED`
   - *Details*: Captures packages, titles, and text via `NotificationListenerService` and streams them to the BB terminal in real-time.

4. **Weather Integration**
   - *Status*: `SUPPORTED`
   - *Details*: Fetches live forecast data via the free Open-Meteo REST API using Kotlin Coroutines in the background.

5. **Find Phone**
   - *Status*: `PARTIALLY_SUPPORTED`
   - *Details*: Plays the alarm ringtone. Bypassing "Do Not Disturb" (DND) requires `ACCESS_NOTIFICATION_POLICY` permission.

6. **Voice Reply**
   - *Status*: `RESTRICTED`
   - *Details*: The app receives Base64 audio, decodes it into an `.amr` file, and attempts delivery. Note: Most major apps (WhatsApp/Telegram) do not natively accept audio files via `RemoteInput` intents, meaning true zero-click voice replies are structurally limited by Android.

7. **Clipboard Sync**
   - *Status*: `RESTRICTED` (One-Way)
   - *Details*: Android 10+ blocks background clipboard reading. BlackBerry to Android sync is supported.

## Build Instructions

1. Clone the repository.
2. Open in Android Studio or compile via Gradle.
```bash
./gradlew assembleDebug
```
3. Grant necessary permissions (Notifications, Bluetooth, Calls) in the UI.

## GitHub Actions CI
The project includes a `.github/workflows/android.yml` action that automatically compiles the APK and attaches it as an artifact on every push.

---
*Built with Kotlin, Coroutines, and Jetpack Compose.*
