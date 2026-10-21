package com.kushal.focusorb.ui

/**
 * A well-known distracting app, used to seed the dashboard's suggestions.
 *
 * @property displayName  Human-readable app name shown in the dashboard row.
 * @property packageName  Android package identifier used for blocklist matching.
 */
data class DistractionApp(
    val displayName: String,
    val packageName: String
)

/**
 * Common time sinks, offered on the dashboard as one-tap "Suggested" blocks.
 * Only the ones actually installed are shown; anything else can be added
 * through the full app picker. Choices persist via
 * [com.kushal.focusorb.BlocklistStore].
 */
val DEFAULT_DISTRACTION_APPS = listOf(
    DistractionApp("WhatsApp",   "com.whatsapp"),
    DistractionApp("Chrome",     "com.android.chrome"),
    DistractionApp("Instagram",  "com.instagram.android"),
    DistractionApp("Reddit",     "com.reddit.frontpage"),
    DistractionApp("YouTube",    "com.google.android.youtube"),
    DistractionApp("Discord",    "com.discord"),
    DistractionApp("LinkedIn",   "com.linkedin.android"),
    DistractionApp("Telegram",   "org.telegram.messenger")
)
