package com.sina.uninotes

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.view.WindowCompat
import android.graphics.Color as AndroidColor
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sina.uninotes.ui.navigation.UniNotesNavHost
import com.sina.uninotes.ui.theme.UniNotesTheme

class MainActivity : AppCompatActivity() {
    private fun useLightSystemIcons() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    override fun onResume() {
        super.onResume()
        useLightSystemIcons()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) useLightSystemIcons()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.rgb(20, 26, 31)),
        )
        val app = application as UniNotesApp
        setContent {
            UniNotesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF141A1F),
                ) {
                    UniNotesNavHost(container = app.container)
                }
            }
        }
    }
}
