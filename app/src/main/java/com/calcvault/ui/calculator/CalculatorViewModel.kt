package com.calcvault.ui.calculator

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcvault.AppContainer
import com.calcvault.calc.AngleUnit
import com.calcvault.calc.CalcResult
import com.calcvault.calc.Calculator
import com.calcvault.calc.HistoryItem
import com.calcvault.crypto.UnlockResult
import com.calcvault.crypto.VaultId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CalculatorState(
    val expression: String = "",
    val preview: String = "",
    val error: String? = null,
    val angleUnit: AngleUnit = AngleUnit.DEG,
    val secondFunction: Boolean = false,
    val memory: Double = 0.0,
    val history: List<HistoryItem> = emptyList(),
    val showHistory: Boolean = false,
)

/** One-shot events the screen reacts to but that do not belong in the state. */
sealed interface CalculatorEvent {
    data class VaultUnlocked(val vaultId: VaultId) : CalculatorEvent
    data object RequestVaultSetup : CalculatorEvent
}

/**
 * Drives the calculator and, invisibly, the lock screen.
 *
 * The disguise only works if a wrong PIN is indistinguishable from arithmetic:
 * a digits-only entry that fails to unlock is simply evaluated, which for a bare
 * number means it prints itself. Nothing about the UI changes.
 */
class CalculatorViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CalculatorState(angleUnit = container.settings.angleUnit))
    val state: StateFlow<CalculatorState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CalculatorEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var lastAnswer: Double = 0.0

    // ---- Key handling ----------------------------------------------------------

    fun onInput(text: String) {
        val current = _state.value
        _state.value = current.copy(
            expression = current.expression + text,
            error = null,
        )
        refreshPreview()
    }

    fun onBackspace() {
        val current = _state.value
        if (current.expression.isEmpty()) return
        _state.value = current.copy(expression = current.expression.dropLast(1), error = null)
        refreshPreview()
    }

    fun onClear() {
        _state.value = _state.value.copy(expression = "", preview = "", error = null)
    }

    fun onToggleAngleUnit() {
        val next = _state.value.angleUnit.toggled()
        container.settings.angleUnit = next
        _state.value = _state.value.copy(angleUnit = next)
        refreshPreview()
    }

    fun onToggleSecondFunction() {
        _state.value = _state.value.copy(secondFunction = !_state.value.secondFunction)
    }

    fun onToggleHistory() {
        _state.value = _state.value.copy(showHistory = !_state.value.showHistory)
    }

    fun onHistoryItemPicked(item: HistoryItem) {
        _state.value = _state.value.copy(
            expression = _state.value.expression + item.result,
            showHistory = false,
            error = null,
        )
        refreshPreview()
    }

    fun onClearHistory() {
        _state.value = _state.value.copy(history = emptyList())
    }

    // ---- Memory keys -----------------------------------------------------------

    fun onMemoryAdd(sign: Int) {
        val value = currentValue() ?: return
        _state.value = _state.value.copy(memory = _state.value.memory + sign * value)
    }

    fun onMemoryRecall() = onInput(Calculator.format(_state.value.memory))

    fun onMemoryClear() {
        _state.value = _state.value.copy(memory = 0.0)
    }

    private fun currentValue(): Double? {
        val result = Calculator.evaluate(_state.value.expression, _state.value.angleUnit, lastAnswer)
        return (result as? CalcResult.Ok)?.value
    }

    // ---- Equals, and the hidden unlock -----------------------------------------

    /**
     * Handles the `=` key.
     *
     * [lifecycleOwner] is only needed so a failed unlock can take an intruder
     * photo; the camera has to bind to a lifecycle.
     */
    fun onEquals(lifecycleOwner: LifecycleOwner) {
        val expression = _state.value.expression
        if (expression.isBlank()) return

        val pin = expression.takeIf { it.all(Char::isDigit) && it.length >= MIN_PIN_LENGTH }
        if (pin != null && container.pinStore.isConfigured()) {
            viewModelScope.launch { attemptUnlock(pin, lifecycleOwner) }
            return
        }
        evaluate(expression)
    }

    private suspend fun attemptUnlock(pin: String, lifecycleOwner: LifecycleOwner) {
        // PBKDF2 is intentionally slow; keep it off the main thread.
        val result = withContext(Dispatchers.Default) {
            container.pinStore.unlock(pin.toCharArray())
        }
        when (result) {
            is UnlockResult.Success -> {
                container.settings.failedAttempts = 0
                container.session.unlock(result.vaultId, result.key)
                onClear()
                _events.emit(CalculatorEvent.VaultUnlocked(result.vaultId))
            }
            UnlockResult.WrongPin -> {
                noteFailedAttempt(lifecycleOwner)
                // Fall through to arithmetic so the screen behaves identically
                // whether or not a vault exists.
                evaluate(pin)
            }
        }
    }

    private suspend fun noteFailedAttempt(lifecycleOwner: LifecycleOwner) {
        val attempts = container.settings.failedAttempts + 1
        container.settings.failedAttempts = attempts
        val settings = container.settings
        if (settings.intruderCaptureEnabled && attempts >= settings.intruderThreshold) {
            container.intruderLog.captureAttempt(lifecycleOwner)
        }
    }

    /** Long-pressing `=` is the only entry point to first-time setup. */
    fun onEqualsLongPress() {
        if (container.pinStore.isConfigured()) return
        viewModelScope.launch { _events.emit(CalculatorEvent.RequestVaultSetup) }
    }

    private fun evaluate(expression: String) {
        when (val result = Calculator.evaluate(expression, _state.value.angleUnit, lastAnswer)) {
            is CalcResult.Ok -> {
                lastAnswer = result.value
                val entry = HistoryItem(Calculator.balance(expression), result.formatted)
                _state.value = _state.value.copy(
                    expression = result.formatted,
                    preview = "",
                    error = null,
                    history = (listOf(entry) + _state.value.history).take(MAX_HISTORY),
                )
            }
            is CalcResult.Error -> {
                _state.value = _state.value.copy(error = result.message, preview = "")
            }
        }
    }

    /** Live result under the expression, shown only when it is already valid. */
    private fun refreshPreview() {
        val expression = _state.value.expression
        if (expression.isBlank()) {
            _state.value = _state.value.copy(preview = "")
            return
        }
        val result = Calculator.evaluate(expression, _state.value.angleUnit, lastAnswer)
        _state.value = _state.value.copy(
            preview = when {
                result !is CalcResult.Ok -> ""
                result.formatted == expression -> ""
                else -> result.formatted
            }
        )
    }

    private companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_HISTORY = 50
    }
}
