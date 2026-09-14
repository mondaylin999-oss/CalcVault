package com.calcvault.calc

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.acosh
import kotlin.math.asin
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.atanh
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/**
 * Recursive-descent parser and evaluator.
 *
 * Precedence, loosest to tightest:
 *   + -  |  * / mod  |  unary -  |  ^ (right associative)  |  postfix ! %
 *
 * Adjacent values multiply implicitly, so `2(3+4)`, `2\u03C0` and `sin(2)cos(2)`
 * all behave the way they do on a physical scientific calculator.
 */
class Parser(
    private val tokens: List<Token>,
    private val angleUnit: AngleUnit,
    private val lastAnswer: Double,
) {
    private var pos = 0

    fun parse(): Double {
        if (tokens.isEmpty()) throw CalcException("Empty expression")
        val value = parseAddSub()
        if (pos != tokens.size) throw CalcException("Unexpected trailing input")
        return value
    }

    private fun peek(): Token? = tokens.getOrNull(pos)

    private fun parseAddSub(): Double {
        var left = parseMulDiv()
        while (true) {
            val token = peek()
            if (token is Token.Op && (token.symbol == '+' || token.symbol == '-')) {
                pos++
                val right = parseMulDiv()
                left = if (token.symbol == '+') left + right else left - right
            } else {
                return left
            }
        }
    }

    private fun parseMulDiv(): Double {
        var left = parseUnary()
        while (true) {
            val token = peek()
            when {
                token is Token.Op && token.symbol == '*' -> {
                    pos++
                    left *= parseUnary()
                }
                token is Token.Op && token.symbol == '/' -> {
                    pos++
                    val divisor = parseUnary()
                    if (divisor == 0.0) throw CalcException("Division by zero")
                    left /= divisor
                }
                token is Token.Op && token.symbol == '%' -> {
                    pos++
                    val divisor = parseUnary()
                    if (divisor == 0.0) throw CalcException("Division by zero")
                    left %= divisor
                }
                startsValue(token) -> left *= parseUnary()
                else -> return left
            }
        }
    }

    /** True for tokens that can begin a value, which is what implicit multiplication needs. */
    private fun startsValue(token: Token?): Boolean = when (token) {
        is Token.Num, is Token.Const, is Token.Func, is Token.Answer, Token.LParen -> true
        else -> false
    }

    private fun parseUnary(): Double {
        val token = peek()
        if (token is Token.Op && (token.symbol == '-' || token.symbol == '+')) {
            pos++
            val value = parseUnary()
            return if (token.symbol == '-') -value else value
        }
        return parsePower()
    }

    private fun parsePower(): Double {
        val base = parsePostfix()
        val token = peek()
        if (token is Token.Op && token.symbol == '^') {
            pos++
            // Right associative, and the exponent may itself be signed: 2^-3.
            val exponent = parseUnary()
            val result = base.pow(exponent)
            if (result.isNaN() && !base.isNaN() && !exponent.isNaN()) {
                throw CalcException("Undefined power")
            }
            return result
        }
        return base
    }

    private fun parsePostfix(): Double {
        var value = parsePrimary()
        while (true) {
            when (peek()) {
                Token.Factorial -> { pos++; value = factorial(value) }
                Token.Percent -> { pos++; value /= 100.0 }
                else -> return value
            }
        }
    }

    private fun parsePrimary(): Double {
        val token = peek() ?: throw CalcException("Expression ends unexpectedly")
        return when (token) {
            is Token.Num -> { pos++; token.value }
            is Token.Const -> { pos++; token.value }
            Token.Answer -> { pos++; lastAnswer }
            Token.LParen -> {
                pos++
                val value = parseAddSub()
                if (peek() != Token.RParen) throw CalcException("Missing \")\"")
                pos++
                value
            }
            is Token.Func -> {
                pos++
                // sqrt9 and sqrt(9) are both accepted; a bare function binds to
                // the tightest following value.
                val argument = if (peek() == Token.LParen) parsePrimary() else parsePostfix()
                applyFunction(token.name, argument)
            }
            Token.RParen -> throw CalcException("Unbalanced \")\"")
            else -> throw CalcException("Unexpected operator")
        }
    }

    private fun applyFunction(name: String, x: Double): Double = when (name) {
        "sin" -> sin(angleUnit.toRadians(x))
        "cos" -> cos(angleUnit.toRadians(x))
        "tan" -> {
            val radians = angleUnit.toRadians(x)
            val result = tan(radians)
            if (abs(result) > 1e15) throw CalcException("tan undefined here") else result
        }
        "asin" -> {
            if (x < -1.0 || x > 1.0) throw CalcException("asin needs -1 to 1")
            angleUnit.fromRadians(asin(x))
        }
        "acos" -> {
            if (x < -1.0 || x > 1.0) throw CalcException("acos needs -1 to 1")
            angleUnit.fromRadians(acos(x))
        }
        "atan" -> angleUnit.fromRadians(atan(x))
        "sinh" -> sinh(x)
        "cosh" -> cosh(x)
        "tanh" -> tanh(x)
        "asinh" -> asinh(x)
        "acosh" -> if (x < 1.0) throw CalcException("acosh needs x \u2265 1") else acosh(x)
        "atanh" -> if (x <= -1.0 || x >= 1.0) throw CalcException("atanh needs -1 < x < 1") else atanh(x)
        "ln" -> if (x <= 0.0) throw CalcException("ln needs x > 0") else ln(x)
        "log" -> if (x <= 0.0) throw CalcException("log needs x > 0") else log10(x)
        "sqrt" -> if (x < 0.0) throw CalcException("\u221A of a negative") else sqrt(x)
        "cbrt" -> cbrt(x)
        "abs" -> abs(x)
        "exp" -> exp(x)
        else -> throw CalcException("Unknown function \"$name\"")
    }

    private fun factorial(x: Double): Double {
        if (x < 0.0) throw CalcException("! needs a non-negative number")
        val n = x.roundToLong()
        if (abs(x - n) > 1e-9) throw CalcException("! needs a whole number")
        if (n > 170) throw CalcException("! is too large")
        var result = 1.0
        for (k in 2..n) result *= k.toDouble()
        return result
    }
}
