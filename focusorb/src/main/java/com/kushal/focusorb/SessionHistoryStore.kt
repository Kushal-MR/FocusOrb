package com.kushal.focusorb

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * SessionHistoryStore — a log of focus sessions as the phone saw them.
 *
 * The phone is the side that knows about distractions, so it keeps the
 * history. Stored as a JSON array in SharedPreferences (org.json ships with
 * Android, so no dependency), newest first, capped at [MAX_ENTRIES].
 *
 * Writes come from two threads — [WatchListenerService] and
 * [FocusShieldService]'s poll loop — and each is a read-modify-write of the
 * whole array, so every mutation holds [lock].
 */
object SessionHistoryStore {

    private const val PREFS_NAME = "focusorb_history"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 100
    private const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L

    /** How a session finished, as recorded on the phone. */
    enum class Outcome {
        COMPLETED, SHATTERED, CANCELLED,
        /** The watch never said "ended" and the session's expiry passed. */
        EXPIRED,
        /** Paused and never resumed before a new session began. */
        UNFINISHED
    }

    data class Entry(
        /** The session's start time, as sent by the watch. */
        val sessionId: Long,
        val plannedMinutes: Int,
        /** Null while the session is still open — running or paused. */
        val outcome: Outcome?,
        /** Times a blocked app was opened. Poison ticks don't add to this. */
        val distractions: Int,
        /** Distraction count per package, for "most distracting app". */
        val perApp: Map<String, Int>
    ) {
        val topApp: String? get() = perApp.maxByOrNull { it.value }?.key
    }

    data class WeekSummary(val completed: Int, val focusMinutes: Int, val distractions: Int)

    private val lock = Any()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Reads ────────────────────────────────────────────────────────────

    fun entries(context: Context): List<Entry> = synchronized(lock) { read(context) }

    fun weekSummary(context: Context, now: Long = System.currentTimeMillis()): WeekSummary {
        val week = entries(context).filter { now - it.sessionId < WEEK_MS }
        val completed = week.filter { it.outcome == Outcome.COMPLETED }
        return WeekSummary(
            completed = completed.size,
            focusMinutes = completed.sumOf { it.plannedMinutes },
            distractions = week.sumOf { it.distractions }
        )
    }

    // ── Writes ───────────────────────────────────────────────────────────

    /**
     * A session started or resumed. Resuming reuses the session ID, so an
     * existing open entry is kept as it is. Any *other* entry still open is a
     * session that was paused and abandoned, so it is closed as unfinished.
     */
    fun onSessionStarted(context: Context, sessionId: Long, plannedMinutes: Int) = mutate(context) { list ->
        val closed = list.map {
            if (it.outcome == null && it.sessionId != sessionId) it.copy(outcome = Outcome.UNFINISHED) else it
        }
        if (closed.any { it.sessionId == sessionId }) closed
        else listOf(Entry(sessionId, plannedMinutes, null, 0, emptyMap())) + closed
    }

    fun onDistraction(context: Context, sessionId: Long, packageName: String) = mutate(context) { list ->
        list.map {
            if (it.sessionId != sessionId) it
            else it.copy(
                distractions = it.distractions + 1,
                perApp = it.perApp + (packageName to (it.perApp[packageName] ?: 0) + 1)
            )
        }
    }

    /** Closes the session. Ignored for an unknown ID or one already closed. */
    fun onSessionEnded(context: Context, sessionId: Long, outcome: Outcome) = mutate(context) { list ->
        list.map { if (it.sessionId == sessionId && it.outcome == null) it.copy(outcome = outcome) else it }
    }

    // ── Persistence ──────────────────────────────────────────────────────

    private fun mutate(context: Context, transform: (List<Entry>) -> List<Entry>) {
        synchronized(lock) {
            val updated = transform(read(context)).take(MAX_ENTRIES)
            prefs(context).edit { putString(KEY_ENTRIES, encode(updated)) }
        }
    }

    private fun read(context: Context): List<Entry> {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        // A corrupt blob loses the history rather than crashing the shield.
        return runCatching { decode(raw) }.getOrDefault(emptyList())
    }

    private fun encode(entries: List<Entry>): String = JSONArray().apply {
        entries.forEach { e ->
            put(JSONObject().apply {
                put("id", e.sessionId)
                put("planned", e.plannedMinutes)
                e.outcome?.let { put("outcome", it.name) }
                put("distractions", e.distractions)
                put("perApp", JSONObject().apply { e.perApp.forEach { (pkg, n) -> put(pkg, n) } })
            })
        }
    }.toString()

    private fun decode(raw: String): List<Entry> {
        val array = JSONArray(raw)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val perAppJson = o.optJSONObject("perApp") ?: JSONObject()
            Entry(
                sessionId = o.getLong("id"),
                plannedMinutes = o.optInt("planned"),
                outcome = o.optString("outcome").takeIf { it.isNotEmpty() }
                    ?.let { name -> Outcome.entries.firstOrNull { it.name == name } },
                distractions = o.optInt("distractions"),
                perApp = perAppJson.keys().asSequence().associateWith { perAppJson.getInt(it) }
            )
        }
    }
}
