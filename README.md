<p align="center">
  <img src="https://img.shields.io/badge/Platform-Wear%20OS%20%2B%20Android-4285F4?style=for-the-badge&logo=wear-os&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-3DDC84?style=for-the-badge&logo=jetpack-compose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/Min%20SDK-30-brightgreen?style=for-the-badge" alt="Min SDK" />
</p>

# FocusOrb

**An anti-distraction system for Wear OS + Android.**

Start a focus session on your watch, and your phone becomes your shield — blocking distracting apps in real-time and punishing you if you stray. Complete a session to earn stars and build your personal galaxy.

---

## How It Works

FocusOrb is a **dual-device** system with two components working together over Bluetooth:

### Watch — *The Focus Orb*
A glowing, animated energy orb lives on your wrist. Start a focus session and the orb pulses with life. Stay focused, and the orb rewards you. Get distracted, and it fights back.

### Phone — *The Distraction Shield*
While a session is active, the phone runs a lightweight background service (the **"Distraction Sniper"**) that monitors which app is in the foreground. Open a blocked app? The phone instantly sends a signal to your watch.

### The Penalty System
- The orb has **3 Health Points**.
- Each distraction triggers **screen shake**, **crystalline fracture visuals**, and **heavy haptic feedback** on the watch.
- Stay inside a blocked app and a **Poison Tick** deals repeated damage every 5 seconds.
- Lose all HP → the orb **shatters** and your session is lost.

### The Reward
Complete a session and the orb **collapses into a supernova**, birthing a collectible star. Stars are placed into an interactive, scrollable **2D hex-spiral galaxy** you build over time.

| Duration | Reward |
|:--------:|:------:|
| 20 min   |  Small Star |
| 30 min   | Medium Star |
| 40 min   |  Large Star |
| 60 min   |  Epic Star  |

---

## Key Features

- **Dynamic Canvas Orb** — Breathing radial gradient animations with real-time fracture rendering on damage
- **Ambient Mode Support** — Ongoing Activity API keeps the session alive when you lower your wrist (Wear OS 3+)
- **Interactive Star Galaxy** — Fling-scrollable hex-grid layout with fish-eye lens magnification
- **Supernova Animation** — Explosive reward animation on session completion
- **Smart Distraction Detection** — Battery-optimized `AccessibilityService` with O(1) package lookups
- **Configurable Blocklist** — Toggle individual apps on/off from the phone dashboard (WhatsApp, Instagram, YouTube, Reddit, Chrome, Discord, LinkedIn, Telegram pre-configured)
- **Bluetooth Sync** — Real-time watch ↔ phone communication via Google Play Services Wearable API

---

## Project Structure

```
FocusOrb/
├── app/                    #  Wear OS Watch App
│   └── src/main/.../presentation/
│       ├── MainActivity.kt              # Compose Canvas UI, Orb, Supernova, Galaxy
│       ├── FocusViewModel.kt            # Session timer, state, star rewards
│       ├── OngoingActivityManager.kt    # Ambient Mode persistence
│       ├── PhoneBridgeManager.kt        # Sends session signals to phone
│       └── DistractionMessageReceiver.kt # Receives distraction hits from phone
│
├── focusorb/               #  Android Phone Companion App
│   └── src/main/.../
│       ├── MainActivity.kt              # Phone Activity with Compose UI
│       ├── DistractionSniperService.kt  # AccessibilityService (app monitor)
│       ├── WatchBridgeManager.kt        # Sends distraction signals to watch
│       ├── WatchListenerService.kt      # Listens for session start/end
│       ├── BlocklistStore.kt            # Blocked apps storage
│       ├── SessionStateStore.kt         # Active session state
│       └── ui/
│           ├── FocusOrbDashboard.kt     # Material 3 dashboard & blocklist UI
│           └── DistractionApp.kt        # Pre-configured app catalog
│
└── gradle/                 # Version catalog & wrapper
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 2.0 |
| Watch UI | Jetpack Wear Compose + Canvas API |
| Phone UI | Jetpack Compose Material 3 |
| Architecture | MVVM (ViewModel + StateFlow) |
| Communication | Google Play Services Wearable (MessageClient) |
| Background | AccessibilityService, WearableListenerService |
| Concurrency | Kotlin Coroutines + Channels |
| Persistence | SharedPreferences |
| Build System | Gradle (KTS) with Version Catalog |

---

## Getting Started

### Prerequisites
- Android Studio (Ladybug or newer recommended)
- Wear OS device or emulator (API 30+)
- Android phone or emulator (API 28+)

### Build & Run

1. **Clone the repository**
   ```bash
   git clone https://github.com/Kushal-MR/FocusOrb.git
   cd FocusOrb
   ```

2. **Open in Android Studio** and let Gradle sync.

3. **Run the watch app** — Select the `app` module and deploy to a Wear OS device/emulator.

4. **Run the phone app** — Select the `focusorb` module and deploy to a phone.

5. **Grant permissions** on the phone:
   - Enable the **Accessibility Service** for Distraction Sniper from the phone dashboard.
   - This is required for real-time foreground app detection.

6. **Pair the devices** — Ensure both devices are connected via the Wear OS companion app for Bluetooth communication.

---

## Permissions

| Permission | Device | Purpose |
|------------|--------|---------|
| `WAKE_LOCK` | Watch | Keep screen active during damage/completion animations |
| `VIBRATE` | Watch | Haptic feedback on distraction hits |
| `POST_NOTIFICATIONS` | Watch | Ongoing Activity for Ambient Mode |
| `BIND_ACCESSIBILITY_SERVICE` | Phone | Monitor foreground app switches |
| `QUERY_ALL_PACKAGES` | Phone | Identify running applications |

---

## License

This project is open source. Feel free to explore, learn from, and build upon it.

---

<p align="center">
  <b>Stay focused. Earn stars. Build your galaxy.</b> 🌌
</p>
