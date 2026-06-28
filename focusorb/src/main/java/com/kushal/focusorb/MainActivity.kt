package com.kushal.focusorb

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kushal.focusorb.ui.FocusOrbDashboard
import com.kushal.focusorb.ui.theme.FocusOrbTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force the app to run at the maximum refresh rate (e.g. 120Hz)
        val win = window
        val lp = win.attributes
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            val modes = win.windowManager.defaultDisplay.supportedModes
            val maxRefreshMode = modes.maxByOrNull { it.refreshRate }
            if (maxRefreshMode != null) {
                lp.preferredDisplayModeId = maxRefreshMode.modeId
            }
        }
        win.attributes = lp
        
        enableEdgeToEdge()
        setContent {
            FocusOrbTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    FocusOrbDashboard()
                }
            }
        }
    }
}
