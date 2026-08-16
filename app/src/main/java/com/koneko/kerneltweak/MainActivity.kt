package com.koneko.kerneltweak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import com.koneko.kerneltweak.ui.theme.KernelTweakTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt
import com.koneko.kerneltweak.root.RootShell
import com.koneko.kerneltweak.ui.KernelTweakApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draws behind both system bars and makes them transparent —
        // this is what fixes "status bar kepotong" / "nav bar beda
        // warna": without this the OS reserves opaque bar space itself
        // (often a mismatched default color) instead of letting your
        // Scaffold's own TopAppBar/NavigationBar colors show through.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            var rootGranted by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                RootShell.requestRoot { granted -> rootGranted = granted }
            }

            KernelTweakTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KernelTweakApp(rootGranted = rootGranted)
                }
            }
        }
    }
}
