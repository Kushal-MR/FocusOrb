package com.kushal.focusorb.presentation

import java.nio.ByteBuffer

/**
 * Wire format for the session messages the watch sends the phone.
 *
 * The phone module keeps an identical copy (the two apps share no code), so
 * any change here must be mirrored in `focusorb/.../SessionMessage.kt`.
 * Fixed-width big-endian fields via [ByteBuffer] keep it dependency-free.
 *
 * "/session_started" — 20 bytes:
 *   sessionId (Long)      start time of the session; survives pause/resume
 *   expiresAt (Long)      epoch ms after which the phone gives up on its own
 *   plannedMinutes (Int)
 *
 * "/session_ended" — 9 bytes:
 *   sessionId (Long)
 *   outcome (Byte)        one of [Outcome]
 */
object SessionMessage {

    enum class Outcome(val code: Byte) {
        COMPLETED(0), SHATTERED(1), PAUSED(2), CANCELLED(3)
    }

    fun started(sessionId: Long, expiresAt: Long, plannedMinutes: Int): ByteArray =
        ByteBuffer.allocate(20)
            .putLong(sessionId)
            .putLong(expiresAt)
            .putInt(plannedMinutes)
            .array()

    fun ended(sessionId: Long, outcome: Outcome): ByteArray =
        ByteBuffer.allocate(9)
            .putLong(sessionId)
            .put(outcome.code)
            .array()
}
