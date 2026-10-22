<p align="center">
  <img src="https://img.shields.io/badge/Platform-Wear%20OS%20%2B%20Android-4285F4?style=for-the-badge&logo=wear-os&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-3DDC84?style=for-the-badge&logo=jetpack-compose&logoColor=white" alt="Compose" />
</p>

# FocusOrb

**A focus timer that catches you getting distracted, across two devices, in real time.**

Start a session on your Wear OS watch. If you open a blocked app on your phone, the phone detects it and your orb on the watch takes damage. Finish the session and you earn a star for your galaxy.

<p align="center">
  <img src="docs/screenshots/hero.png" alt="FocusOrb on the watch and phone" width="100%" />
</p>

## How it works

- **Watch:** a Canvas-drawn orb with 3 HP. Each distraction cracks it with a screen shake and haptics, and staying in a blocked app costs another hit every 5 s. At 0 HP it shatters.
- **Phone:** during a session, a foreground service checks the foreground app once a second and signals the watch over the Wearable Data Layer.
- **Reward:** completing a session triggers a supernova that adds a star to a scrollable galaxy.

<p align="center">
  <img src="docs/screenshots/watch-running.png" width="30%" alt="Session running" />
  <img src="docs/screenshots/watch-damage-1.png" width="30%" alt="The orb cracks on a distraction" />
  <img src="docs/screenshots/watch-shattered.png" width="30%" alt="Focus lost" />
</p>
<p align="center">
  <img src="docs/screenshots/watch-supernova.png" width="30%" alt="Supernova" />
  <img src="docs/screenshots/watch-voyage.png" width="30%" alt="The new star joins the galaxy" />
  <img src="docs/screenshots/watch-galaxy.png" width="30%" alt="Galaxy" />
</p>
<p align="center">
  <img src="docs/screenshots/phone-setup.png" width="30%" alt="Setup" />
  <img src="docs/screenshots/phone-dashboard.png" width="30%" alt="History" />
  <img src="docs/screenshots/phone-picker.png" width="30%" alt="App picker" />
</p>

## Why Usage Access, not an Accessibility Service

The first version used an Accessibility Service to detect the foreground app, and **UPI and banking apps refused to run** while it was enabled, because screen-reading services are a known fraud vector. FocusOrb now uses `UsageStatsManager`, which reports only *which* app is open, never screen content. The trade-off is polling once a second instead of instant events, plus a battery-optimisation exemption so the watch can start the service in the background on Android 12+.

## Built with

Kotlin · Jetpack Compose (Wear + Material 3) · Wearable Data Layer API · UsageStatsManager · foreground services · Coroutines

## Try it

Download both APKs from the [latest release](https://github.com/Kushal-MR/FocusOrb/releases/latest): `FocusOrb-watch` for Wear OS (API 30+) and `FocusOrb-phone` for Android (API 28+). Open the phone app, then grant usage access, notifications, and unrestricted battery.

> **Usage access greyed out?** Android 13+ restricts it for apps installed from a downloaded APK. Open FocusOrb's App info, tap ⋮ → **Allow restricted settings**, then grant it.

To build from source, open the project in Android Studio and run the `app` module (watch) and the `focusorb` module (phone).
