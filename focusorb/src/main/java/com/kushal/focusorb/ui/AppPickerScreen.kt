package com.kushal.focusorb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kushal.focusorb.InstalledApp

/**
 * Full-screen picker for choosing which installed apps to block.
 *
 * Changes apply immediately — each tick writes straight through to the
 * blocklist — so "Done" (or system back) only closes the screen; there is no
 * unsaved state to lose.
 *
 * @param apps null while the installed-app list is still loading.
 */
@Composable
fun AppPickerScreen(
    apps: List<InstalledApp>?,
    blocked: Set<String>,
    onToggle: (packageName: String, block: Boolean) -> Unit,
    onDone: () -> Unit
) {
    BackHandler(onBack = onDone)
    var query by rememberSaveable { mutableStateOf("") }

    val visible = remember(apps, query) {
        apps?.filter {
            query.isBlank() ||
                it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
            // Edge-to-edge is on, so keep clear of the system bars — and of
            // the keyboard, which covers the bottom of the list while searching.
            .safeDrawingPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Choose apps", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = if (blocked.isEmpty()) "Nothing blocked yet" else "${blocked.size} blocked",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            TextButton(onClick = onDone) {
                Text(text = "Done", color = TextPrimary, fontSize = 15.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search apps", color = TextSecondary) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = TextSecondary,
                unfocusedBorderColor = DividerColor,
                cursorColor = TextPrimary,
                focusedContainerColor = CardSurface,
                unfocusedContainerColor = CardSurface
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        when {
            visible == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TextSecondary)
            }
            visible.isEmpty() -> Text(
                text = "No apps match \"$query\".",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 24.dp)
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(visible, key = { it.packageName }) { app ->
                    val isBlocked = app.packageName in blocked
                    PickerRow(app = app, isBlocked = isBlocked, onToggle = { onToggle(app.packageName, it) })
                }
            }
        }
    }
}

@Composable
private fun PickerRow(app: InstalledApp, isBlocked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // toggleable rather than clickable: the whole row is the control,
            // announced to TalkBack as a checkbox with its checked state.
            .toggleable(value = isBlocked, role = Role.Checkbox, onValueChange = onToggle)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(icon = app.icon, label = app.label)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        CheckMark(checked = isBlocked)
    }
}

/**
 * A drawn check box. Material's Checkbox sets up several animations per
 * instance, and composing one for every row that scrolls into view was a
 * measurable share of the picker's frame time on a 120 Hz phone. This is a
 * single Canvas with no state, and the row above supplies the semantics.
 */
@Composable
private fun CheckMark(checked: Boolean) {
    Canvas(modifier = Modifier.padding(horizontal = 12.dp).size(20.dp)) {
        val corner = CornerRadius(5.dp.toPx())
        if (checked) {
            drawRoundRect(color = TextPrimary, cornerRadius = corner)
            val stroke = 2.dp.toPx()
            val w = size.width
            val h = size.height
            drawLine(Color.Black, Offset(w * 0.24f, h * 0.52f), Offset(w * 0.43f, h * 0.70f), stroke, StrokeCap.Round)
            drawLine(Color.Black, Offset(w * 0.43f, h * 0.70f), Offset(w * 0.77f, h * 0.32f), stroke, StrokeCap.Round)
        } else {
            val stroke = 1.5.dp.toPx()
            drawRoundRect(
                color = TextSecondary,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = corner,
                style = Stroke(width = stroke)
            )
        }
    }
}

/**
 * An app's icon, or a lettered tile when we have none — for example a blocked
 * app that has since been uninstalled.
 */
@Composable
fun AppIcon(icon: ImageBitmap?, label: String) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CardSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.firstOrNull()?.uppercase() ?: "?",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
