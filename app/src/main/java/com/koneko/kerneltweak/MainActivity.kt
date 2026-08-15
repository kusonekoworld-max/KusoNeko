package com.koneko.kerneltweak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.koneko.kerneltweak.root.RootShell
import com.koneko.kerneltweak.ui.KernelTweakApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
