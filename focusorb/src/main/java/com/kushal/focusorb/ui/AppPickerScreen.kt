package com.kushal.focusorb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
                Text(text = "Done", color = AccentCyan, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = DividerColor,
                cursorColor = AccentCyan,
                focusedContainerColor = CardSurface,
                unfocusedContainerColor = CardSurface
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        when {
            visible == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
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
            .clickable { onToggle(!isBlocked) }
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
        Checkbox(
            checked = isBlocked,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(
                checkedColor = AccentRed,
                uncheckedColor = TextSecondary,
                checkmarkColor = Color.White
            )
        )
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
