package com.kushal.focusorb.ui

/**
 * Represents a single distractable application entry in the blocklist manager UI.
 *
 * @property displayName  Human-readable app name shown in the dashboard row.
 * @property packageName  Android package identifier used for blocklist matching.
 */
data class DistractionApp(
    val displayName: String,
    val packageName: String
)

/**
 * Pre-populated catalog of common distraction targets.
 * The UI will render these as toggleable rows; the user's
 * on/off state is persisted via [com.kushal.focusorb.BlocklistStore].
 */
val DEFAULT_DISTRACTION_APPS = listOf(
    DistractionApp("Instagram",  "com.instagram.android"),
    DistractionApp("Reddit",     "com.reddit.frontpage"),
    DistractionApp("YouTube",    "com.google.android.youtube"),
    DistractionApp("TikTok",     "com.zhiliaoapp.musically"),
    DistractionApp("X (Twitter)","com.twitter.android"),
    DistractionApp("Snapchat",   "com.snapchat.android"),
    DistractionApp("Facebook",   "com.facebook.katana"),
    DistractionApp("Messenger",  "com.facebook.orca"),
    DistractionApp("Pinterest",  "com.pinterest"),
    DistractionApp("Tumblr",     "com.tumblr"),
    DistractionApp("Discord",    "com.discord"),
    DistractionApp("Twitch",     "tv.twitch.android.app"),
    DistractionApp("LinkedIn",   "com.linkedin.android"),
    DistractionApp("Telegram",   "org.telegram.messenger")
)
