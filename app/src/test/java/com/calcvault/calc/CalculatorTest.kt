package com.calcvault.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {

    private fun eval(expression: String, unit: AngleUnit = AngleUnit.DEG, ans: Double = 0.0): String {
        return when (val result = Calculator.evaluate(expression, unit, ans)) {
            is CalcResult.Ok -> result.formatted
            is CalcResult.Error -> "ERROR: ${result.message}"
        }
    }

    @Test
    fun `respects operator precedence`() {
        assertEquals("14", eval("2+3×4"))
        assertEquals("20", eval("(2+3)×4"))
        assertEquals("2", eval("8÷2÷2"))
    }

    @Test
    fun `power is right associative and binds tighter than unary minus`() {
        assertEquals("512", eval("2^3^2"))
        assertEquals("-4", eval("-2^2"))
        assertEquals("0.125", eval("2^-3"))
    }

    @Test
    fun `multiplies adjacent values implicitly`() {
        assertEquals("14", eval("2(3+4)"))
        // Twelve significant digits.
        assertEquals("6.28318530718", eval("2π"))
    }

    @Test
    fun `closes unbalanced parentheses on evaluation`() {
        assertEquals("7", eval("(3+4"))
        assertEquals("(3+4)", Calculator.balance("(3+4"))
    }

    @Test
    fun `honours the angle unit`() {
        assertEquals("1", eval("sin(90)", AngleUnit.DEG))
        assertEquals("0", eval("sin(0)", AngleUnit.RAD))
        assertEquals("90", eval("asin(1)", AngleUnit.DEG))
    }

    @Test
    fun `handles postfix factorial and percent`() {
        assertEquals("120", eval("5!"))
        assertEquals("0.5", eval("50%"))
        assertEquals("15", eval("300×5%"))
    }

    @Test
    fun `substitutes the previous answer`() {
        assertEquals("12", eval("Ans×3", ans = 4.0))
    }

    @Test
    fun `reports errors instead of throwing`() {
        assertTrue(eval("1÷0").startsWith("ERROR"))
        assertTrue(eval("ln(0)").startsWith("ERROR"))
        assertTrue(eval("√(-1)").startsWith("ERROR"))
        assertTrue(eval("2++").startsWith("ERROR"))
        assertTrue(eval("sin(").startsWith("ERROR"))
    }

    @Test
    fun `formats results without trailing noise`() {
        assertEquals("0.1", eval("0.1"))
        assertEquals("0.3", eval("0.1+0.2"))
        assertEquals("1E15", eval("1000000×1000000000"))
        assertEquals("0", eval("0"))
    }
}
