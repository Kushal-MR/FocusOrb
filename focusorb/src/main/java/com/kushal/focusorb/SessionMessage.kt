package com.kushal.focusorb

import java.nio.ByteBuffer

/**
 * Wire format for the session messages the watch sends.
 *
 * Mirror of `app/.../presentation/SessionMessage.kt` (the two apps share no
 * code) — keep the two in step.
 *
 * Decoding is lenient: an empty or short payload decodes to null rather than
 * throwing, so the phone still works with a watch build from before payloads
 * existed. Callers fall back to conservative defaults.
 */
object SessionMessage {

    enum class Outcome(val code: Byte) {
        COMPLETED(0), SHATTERED(1), PAUSED(2), CANCELLED(3);

        companion object {
            fun fromCode(code: Byte): Outcome? = entries.firstOrNull { it.code == code }
        }
    }

    data class Started(val sessionId: Long, val expiresAt: Long, val plannedMinutes: Int)
    data class Ended(val sessionId: Long, val outcome: Outcome)

    fun decodeStarted(bytes: ByteArray): Started? {
        if (bytes.size < 20) return null
        val buffer = ByteBuffer.wrap(bytes)
        return Started(
            sessionId = buffer.getLong(),
            expiresAt = buffer.getLong(),
            plannedMinutes = buffer.getInt()
        )
    }

    fun decodeEnded(bytes: ByteArray): Ended? {
        if (bytes.size < 9) return null
        val buffer = ByteBuffer.wrap(bytes)
        val sessionId = buffer.getLong()
        val outcome = Outcome.fromCode(buffer.get()) ?: return null
        return Ended(sessionId, outcome)
    }
}
