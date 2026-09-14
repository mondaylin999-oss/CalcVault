package com.calcvault

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.calcvault.crypto.PinStore
import com.calcvault.security.AppSettings
import com.calcvault.security.IntruderLog
import com.calcvault.security.VaultSession

/**
 * The app's single dependency container.
 *
 * Small enough that a DI framework would cost more than it saves, and keeping it
 * explicit makes it obvious that exactly one [VaultSession] exists — which is
 * what guarantees there is only ever one copy of the vault key in memory.
 */
class AppContainer(context: Context) {
    val settings = AppSettings(context)
    val pinStore = PinStore(context)
    val session = VaultSession(context)
    val intruderLog = IntruderLog(context)
}

class CalcVaultApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Auto-lock is driven by the whole process going to the background, not
        // by one activity pausing, so a system permission dialog does not count
        // as leaving the app.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                container.session.backgroundedAtMillis = System.currentTimeMillis()
                if (container.settings.autoLockSeconds == 0) container.session.lock()
            }

            override fun onStart(owner: LifecycleOwner) {
                container.session.lockIfExpired(container.settings.autoLockSeconds)
                container.session.backgroundedAtMillis = 0L
            }
        })
    }
}

/** Convenience accessor used from composables. */
val Context.appContainer: AppContainer
    get() = (applicationContext as CalcVaultApp).container
