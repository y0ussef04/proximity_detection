# Touch Lock for Calls 🔒

A lightweight, privacy-friendly, software-based proximity sensor replacement application written in **Kotlin** for **Android 8.0+ (API 26+)**, designed specifically for phones where the physical hardware proximity sensor is broken or missing.

---

## Purpose & Overview

When phone proximity sensors malfunction, user facial touches during calls accidentally press UI buttons such as **Mute**, **Speaker**, **Keypad**, **Hold**, or **End Call**.

**Touch Lock for Calls** solves this completely in software:
- It detects when a call becomes active (incoming or outgoing).
- After a configurable delay (default **2 seconds**), it displays a semi-transparent, touch-intercepting lock overlay over the phone UI.
- The phone UI and call duration remain clearly visible underneath.
- All screen touch events are consumed by the overlay to prevent accidental cheek presses.
- **Double-tapping anywhere on the overlay** unlocks the screen immediately.
- When the call ends, the overlay is removed automatically.

---

## Compatibility & Target Devices

- **Minimum SDK**: API 26 (Android 8.0 Oreo)
- **Target SDK**: API 34 (Android 14) / API 35 (Android 15 ready)
- **Language**: Kotlin
- **Primary Test Device**: **OPPO Reno5 5G** (Android 13 / ColorOS)

---

## Permissions & Rationale

| Permission | Scope / API Level | Why It Is Required |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | All versions | Display the touch lock overlay above the system phone dialer UI. |
| `READ_PHONE_STATE` | All versions | Detect call state changes (`RINGING`, `OFFHOOK`, `IDLE`). |
| `FOREGROUND_SERVICE` | API 28+ | Keeps the call-detection service active in the background. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | API 34+ | Mandatory Android 14 foreground service classification. |
| `POST_NOTIFICATIONS` | API 33+ | Required to display the foreground service status notification on Android 13+. |
| `RECEIVE_BOOT_COMPLETED` | All versions | Restarts the call detection service automatically when the device reboots. |

**No Unnecessary Permissions**:
- ❌ No Contacts access
- ❌ No Microphone or Camera access
- ❌ No Location access
- ❌ No Internet connection (100% offline & private)

---

## Manufacturer Compatibility (OPPO ColorOS, Xiaomi, Samsung)

Custom Android skins (such as OPPO ColorOS, Xiaomi MIUI/HyperOS, and Vivo FuntouchOS) enforce strict background execution limits. To ensure Touch Lock stays active:

### For OPPO Reno5 5G (ColorOS 12 / 13 / 14):
1. Go to **Settings** → **Apps** → **App management** → **Touch Lock for Calls**.
2. Tap **Battery usage** → Turn ON **"Allow background activity"** and **"Allow auto-launch"**.
3. Go to **Settings** → **Battery** → **More settings** → **Optimize battery use** → Select **Touch Lock for Calls** → Set to **"Don't optimize"**.
4. Open **Recent Apps** (Task Switcher), tap the **⋮** menu on the Touch Lock card, and tap **Lock**.

---

## Architecture & Project Structure

```
app/src/main/java/com/touchlock/calls/
├── MainActivity.kt                # Settings & permission status UI
├── service/
│   └── CallDetectionService.kt    # Foreground service monitoring call state
├── overlay/
│   └── TouchLockOverlayManager.kt # WindowManager overlay & double-tap listener
├── receiver/
│   └── BootReceiver.kt            # BOOT_COMPLETED auto-restart handler
├── preferences/
│   └── AppPreferences.kt          # Jetpack DataStore preferences
└── util/
    └── PermissionUtils.kt         # Version-aware permission helper
```

---

## Touch Interception Acceptance Criteria

| Scenario | Action | Expected Result |
|---|---|---|
| Locked | Tap **Mute** button | Call is NOT muted. Overlay consumes tap. |
| Locked | Tap **Speaker** button | Speaker is NOT activated. |
| Locked | Tap **Keypad** button | Keypad does NOT open. |
| Locked | Tap **End Call** button | Call does NOT end. |
| Locked | Single tap anywhere | Overlay does NOT unlock. |
| Locked | Double tap anywhere | Overlay unlocks immediately; Call UI becomes interactive. |
| Unlocked | Tap **Mute** / **End Call** | Works normally. |

---

## Step-by-Step Testing Guide

### Test 1: Incoming Call
1. Receive an incoming call and answer it.
2. Wait 2 seconds (or configured delay).
3. Confirm the semi-transparent overlay `🔒 Touch Locked` appears.

### Test 2: Outgoing Call
1. Place an outgoing phone call.
2. Wait 2 seconds after dialing starts.
3. Confirm the overlay appears.

### Test 3: Touch Interception Test
1. While the overlay is locked, touch various parts of the screen (Mute, Speaker, Keypad).
2. Verify that NO underlying phone UI buttons respond.

### Test 4: Double-Tap Unlock
1. Double-tap rapidly anywhere on the locked screen overlay.
2. Verify the overlay disappears instantly and phone UI buttons are responsive.

### Test 5: Call End
1. End a call (or have the remote party hang up) while locked or unlocked.
2. Verify the overlay disappears immediately and timers are cleaned up.

---

## Building the Project

Open the project in **Android Studio (Hedgehog / Iguana / Jellyfish or newer)**:
1. Select `File -> Open` and choose `d:\proximity detection`.
2. Sync Gradle files.
3. Run `./gradlew assembleDebug` or click **Run 'app'** to deploy to your device.
