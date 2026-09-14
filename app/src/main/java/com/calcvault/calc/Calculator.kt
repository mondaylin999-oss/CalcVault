package com.calcvault.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

/** One finished calculation, kept for the history tape. */
data class HistoryItem(val expression: String, val result: String)

/** Either a formatted result or a human-readable reason the input was rejected. */
sealed interface CalcResult {
    data class Ok(val value: Double, val formatted: String) : CalcResult
    data class Error(val message: String) : CalcResult
}

object Calculator {

    private const val SIGNIFICANT_DIGITS = 12

    fun evaluate(expression: String, angleUnit: AngleUnit, lastAnswer: Double): CalcResult = try {
        val tokens = Tokenizer.tokenize(balance(expression))
        val value = Parser(tokens, angleUnit, lastAnswer).parse()
        when {
            value.isNaN() -> CalcResult.Error("Not a number")
            value.isInfinite() -> CalcResult.Error("Result is too large")
            else -> CalcResult.Ok(value, format(value))
        }
    } catch (e: CalcException) {
        CalcResult.Error(e.message ?: "Invalid expression")
    } catch (e: ArithmeticException) {
        CalcResult.Error(e.message ?: "Arithmetic error")
    }

    /**
     * Closes parentheses the user left open. Physical calculators do this on `=`
     * and typing every closing bracket is the single most annoying part of a
     * touch keypad.
     */
    fun balance(expression: String): String {
        var open = 0
        for (c in expression) {
            if (c == '(') open++
            if (c == ')') open--
        }
        return if (open > 0) expression + ")".repeat(open) else expression
    }

    /**
     * Formats a result the way a calculator display does: plain decimal in the
     * comfortable range, scientific notation outside it, and no trailing zeros.
     */
    fun format(value: Double): String {
        if (value == 0.0) return "0"
        val magnitude = abs(value)
        val rounded = BigDecimal(value).round(MathContext(SIGNIFICANT_DIGITS, RoundingMode.HALF_UP))
        if (magnitude >= 1e12 || magnitude < 1e-9) {
            val scientific = String.format("%.9E", rounded)
            val (mantissa, exponent) = scientific.split("E")
            return trimZeros(mantissa) + "E" + exponent.toInt()
        }
        return trimZeros(rounded.stripTrailingZeros().toPlainString())
    }

    private fun trimZeros(text: String): String {
        if (!text.contains('.')) return text
        return text.trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }
}
