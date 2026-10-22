package com.kushal.focusorb.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kushal.focusorb.SessionHistoryStore
import com.kushal.focusorb.SessionHistoryStore.Outcome

/** Sessions shown on the dashboard before "See all". */
private const val PREVIEW_COUNT = 5

/**
 * "This week" totals and the most recent sessions. The full list lives on
 * [HistoryScreen]: listing every session here would push the blocklist — the
 * part people actually come to change — far down the page.
 *
 * @param openSessionId The session currently running, if any. Lets an open
 *                      entry read "In progress" rather than "Paused".
 * @param labelFor      Turns a package name into an app label for display.
 */
@Composable
fun HistorySection(
    entries: List<SessionHistoryStore.Entry>,
    week: SessionHistoryStore.WeekSummary,
    openSessionId: Long?,
    labelFor: (String) -> String,
    onSeeAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("This week")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Row(modifier = Modifier.padding(vertical = 18.dp)) {
                Stat(value = week.completed.toString(), label = "completed", modifier = Modifier.weight(1f))
                Stat(value = week.focusMinutes.toString(), label = "min focused", modifier = Modifier.weight(1f))
                Stat(value = week.distractions.toString(), label = "distractions", modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Recent sessions", modifier = Modifier.weight(1f))
            if (entries.size > PREVIEW_COUNT) {
                TextButton(onClick = onSeeAll) {
                    Text(text = "See all ${entries.size}", color = TextPrimary, fontSize = 14.sp)
                }
            }
        }

        if (entries.isEmpty()) {
            Text(
                text = "Sessions you run on your watch will show up here.",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        } else {
            val preview = entries.take(PREVIEW_COUNT)
            AppListCard(rowCount = preview.size) { index ->
                SessionRow(preview[index], openSessionId, labelFor)
            }
        }
    }
}

/**
 * Every recorded session (the store keeps the latest 100), newest first.
 * Lazy, so a long history scrolls without composing every row up front.
 */
@Composable
fun HistoryScreen(
    entries: List<SessionHistoryStore.Entry>,
    openSessionId: Long?,
    labelFor: (String) -> String,
    onDone: () -> Unit
) {
    BackHandler(onBack = onDone)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "History", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = if (entries.size == 1) "1 session" else "${entries.size} sessions",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            TextButton(onClick = onDone) {
                Text(text = "Done", color = TextPrimary, fontSize = 15.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(entries, key = { _, e -> e.sessionId }) { index, entry ->
                SessionRow(entry, openSessionId, labelFor)
                if (index < entries.lastIndex) {
                    HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(text = value, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun SessionRow(
    entry: SessionHistoryStore.Entry,
    openSessionId: Long?,
    labelFor: (String) -> String
) {
    val (outcomeText, outcomeColor) = describeOutcome(entry, openSessionId)

    val distractionText = when (entry.distractions) {
        0 -> "No distractions"
        1 -> "1 distraction"
        else -> "${entry.distractions} distractions"
    } + (entry.topApp?.let { " · mostly ${labelFor(it)}" } ?: "")

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row {
            Text(text = outcomeText, color = outcomeColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(8.dp))
            if (entry.plannedMinutes > 0) {
                Text(text = "${entry.plannedMinutes} min", color = TextSecondary, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = DateUtils.getRelativeTimeSpanString(
                    entry.sessionId,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString(),
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = distractionText, color = TextSecondary, fontSize = 13.sp)
    }
}

private fun describeOutcome(entry: SessionHistoryStore.Entry, openSessionId: Long?): Pair<String, Color> =
    when (entry.outcome) {
        // Weight carries the meaning instead of colour: outcomes that count
        // (completed, shattered, live) read in full white, the rest recede.
        Outcome.COMPLETED -> "Completed" to TextPrimary
        Outcome.SHATTERED -> "Shattered" to TextPrimary
        Outcome.CANCELLED -> "Cancelled" to TextSecondary
        // The watch went quiet and the phone closed the session itself.
        Outcome.EXPIRED -> "Lost contact" to TextSecondary
        Outcome.UNFINISHED -> "Unfinished" to TextSecondary
        null -> if (entry.sessionId == openSessionId) "In progress" to TextPrimary else "Paused" to TextSecondary
    }
