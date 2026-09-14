package com.calcvault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calcvault.crypto.VaultId
import com.calcvault.ui.calculator.CalculatorEvent
import com.calcvault.ui.calculator.CalculatorScreen
import com.calcvault.ui.calculator.CalculatorViewModel
import com.calcvault.ui.settings.SettingsScreen
import com.calcvault.ui.setup.PinPurpose
import com.calcvault.ui.setup.PinSetupScreen
import com.calcvault.ui.theme.CalcVaultTheme
import com.calcvault.ui.vault.VaultScreen
import com.calcvault.ui.vault.VaultViewModel

/** The screen currently in front of the user. */
private sealed interface Route {
    data object Calculator : Route
    data object Vault : Route
    data object Settings : Route
    data class Pin(val purpose: PinPurpose) : Route
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CalcVaultTheme { CalcVaultRoot(::applySecureFlag) } }
    }

    /**
     * FLAG_SECURE keeps the vault out of screenshots and the recent-apps
     * thumbnail. It is toggled per screen: the calculator should look ordinary
     * and screenshot like any other app.
     */
    private fun applySecureFlag(secure: Boolean) {
        if (secure) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Composable
private fun CalcVaultRoot(setSecure: (Boolean) -> Unit) {
    val container = LocalContext.current.appContainer
    val lifecycleOwner = LocalLifecycleOwner.current

    val calculatorViewModel: CalculatorViewModel = viewModel(factory = factory { CalculatorViewModel(container) })
    var route by remember { mutableStateOf<Route>(Route.Calculator) }
    val session by container.session.current.collectAsStateWithLifecycle()

    // A lock from anywhere — the button, auto-lock, or the process restarting —
    // always lands back on the calculator.
    LaunchedEffect(session) {
        if (session == null && (route == Route.Vault || route == Route.Settings)) {
            route = Route.Calculator
        }
    }

    LaunchedEffect(route, container.settings.blockScreenshots) {
        setSecure(route != Route.Calculator && container.settings.blockScreenshots)
    }

    LaunchedEffect(Unit) {
        calculatorViewModel.events.collect { event ->
            route = when (event) {
                is CalculatorEvent.VaultUnlocked -> Route.Vault
                CalculatorEvent.RequestVaultSetup -> Route.Pin(PinPurpose.CREATE)
            }
        }
    }

    when (val current = route) {
        Route.Calculator -> CalculatorScreen(
            viewModel = calculatorViewModel,
            onEquals = { calculatorViewModel.onEquals(lifecycleOwner) },
            onEqualsLongPress = calculatorViewModel::onEqualsLongPress,
        )

        Route.Vault -> {
            val open = session
            if (open == null) {
                route = Route.Calculator
            } else {
                val vaultViewModel: VaultViewModel = viewModel(
                    key = open.vaultId.name,
                    factory = factory { VaultViewModel(container) },
                )
                VaultScreen(
                    viewModel = vaultViewModel,
                    onLock = {
                        vaultViewModel.lock()
                        route = Route.Calculator
                    },
                    onOpenSettings = { route = Route.Settings },
                )
            }
        }

        Route.Settings -> SettingsScreen(
            container = container,
            onBack = { route = Route.Vault },
            onChangePin = { route = Route.Pin(PinPurpose.CHANGE) },
            onSetDecoyPin = { route = Route.Pin(PinPurpose.DECOY) },
            onVaultDestroyed = { route = Route.Calculator },
        )

        is Route.Pin -> PinSetupScreen(
            purpose = current.purpose,
            onCancel = {
                route = if (container.session.isUnlocked) Route.Settings else Route.Calculator
            },
            validate = { pin -> validatePin(container, current.purpose, pin) },
            onConfirm = { pin ->
                when (current.purpose) {
                    PinPurpose.CREATE -> {
                        val key = container.pinStore.createVault(VaultId.MAIN, pin.toCharArray())
                        container.session.unlock(VaultId.MAIN, key)
                        route = Route.Vault
                    }
                    PinPurpose.CHANGE -> {
                        // Re-wrapping the live key is what keeps existing files readable.
                        val open = container.session.current.value
                        if (open != null) {
                            container.pinStore.changePin(
                                open.vaultId,
                                currentKeyOf(container),
                                pin.toCharArray(),
                            )
                        }
                        route = Route.Settings
                    }
                    PinPurpose.DECOY -> {
                        container.pinStore.createVault(VaultId.DECOY, pin.toCharArray())
                        route = Route.Settings
                    }
                }
            },
        )
    }
}

/**
 * Rejects a new code that would collide with the other vault's code, which would
 * otherwise make one of the two vaults permanently unreachable.
 */
private fun validatePin(container: AppContainer, purpose: PinPurpose, pin: String): String? {
    if (purpose == PinPurpose.CREATE) return null
    val existing = container.pinStore.unlock(pin.toCharArray())
    val clashes = when (purpose) {
        PinPurpose.DECOY -> existing is com.calcvault.crypto.UnlockResult.Success
        PinPurpose.CHANGE ->
            (existing as? com.calcvault.crypto.UnlockResult.Success)?.vaultId == VaultId.DECOY
        else -> false
    }
    return if (clashes) "That code already opens another vault" else null
}

/** The vault key of the open session, needed to re-wrap it under a new PIN. */
private fun currentKeyOf(container: AppContainer): javax.crypto.SecretKey {
    val open = requireNotNull(container.session.current.value) { "No vault is open" }
    return open.repository.sessionKey
}

/** Minimal ViewModel factory so the container can be injected by hand. */
private inline fun <reified T : ViewModel> factory(crossinline builder: () -> T) =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = builder() as V
    }
