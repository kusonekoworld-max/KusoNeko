package com.koneko.kerneltweak.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires on BOOT_COMPLETED (plus the OEM QUICKBOOT_POWERON variants some
 * ROMs send instead). Uses Shell.getShell() — the *blocking* libsu call —
 * rather than the callback version, so goAsync()'s pendingResult isn't
 * finished before root access actually resolves.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BootConfigStore.isEnabled(context)) return

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val shell = Shell.getShell()
                if (shell.isRoot) {
                    BootConfigStore.applySnapshot(appContext)
                }
            } catch (_: Exception) {
                // No root available at boot (unrooted, not-yet-granted, etc) — skip silently.
            } finally {
                pending.finish()
            }
        }
    }
}
