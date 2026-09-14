package com.calcvault.calc

/** A single lexical unit of an expression. */
sealed interface Token {
    data class Num(val value: Double) : Token
    data class Const(val name: String, val value: Double) : Token
    data class Func(val name: String) : Token
    data class Op(val symbol: Char) : Token
    data object LParen : Token
    data object RParen : Token
    data object Factorial : Token
    data object Percent : Token
    data object Answer : Token
}

class CalcException(message: String) : Exception(message)

/**
 * Turns display text into tokens. The display uses the pretty symbols the keypad
 * produces (×, ÷, −, √) rather than ASCII, so they are all accepted here.
 */
object Tokenizer {

    private val functions = setOf(
        "sin", "cos", "tan", "asin", "acos", "atan",
        "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
        "ln", "log", "sqrt", "cbrt", "abs", "exp",
    )

    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() || c == ',' || c == '_' -> i++

                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    // Scientific notation: 1.5E-3. The sign is only part of the
                    // number when a digit actually follows it.
                    if (i < input.length && (input[i] == 'E' || input[i] == 'e')) {
                        val mark = i
                        i++
                        if (i < input.length && (input[i] == '+' || input[i] == '-' || input[i] == '\u2212')) i++
                        if (i < input.length && input[i].isDigit()) {
                            while (i < input.length && input[i].isDigit()) i++
                        } else {
                            i = mark
                        }
                    }
                    val text = input.substring(start, i).replace('\u2212', '-')
                    val value = text.toDoubleOrNull()
                        ?: throw CalcException("Malformed number \"$text\"")
                    tokens += Token.Num(value)
                }

                c == '\u03C0' -> { tokens += Token.Const("\u03C0", Math.PI); i++ }
                c == '\u221A' -> { tokens += Token.Func("sqrt"); i++ }
                c == '(' || c == '[' -> { tokens += Token.LParen; i++ }
                c == ')' || c == ']' -> { tokens += Token.RParen; i++ }
                c == '!' -> { tokens += Token.Factorial; i++ }
                c == '%' -> { tokens += Token.Percent; i++ }
                c == '+' -> { tokens += Token.Op('+'); i++ }
                c == '-' || c == '\u2212' -> { tokens += Token.Op('-'); i++ }
                c == '*' || c == '\u00D7' || c == '\u22C5' -> { tokens += Token.Op('*'); i++ }
                c == '/' || c == '\u00F7' -> { tokens += Token.Op('/'); i++ }
                c == '^' -> { tokens += Token.Op('^'); i++ }

                c.isLetter() -> {
                    val start = i
                    while (i < input.length && input[i].isLetter()) i++
                    val word = input.substring(start, i)
                    val lower = word.lowercase()
                    when {
                        lower in functions -> tokens += Token.Func(lower)
                        lower == "ans" -> tokens += Token.Answer
                        lower == "pi" -> tokens += Token.Const("\u03C0", Math.PI)
                        lower == "e" -> tokens += Token.Const("e", Math.E)
                        // "mod" spelled out is the same operator as %.
                        lower == "mod" -> tokens += Token.Op('%')
                        else -> throw CalcException("Unknown name \"$word\"")
                    }
                }

                else -> throw CalcException("Unexpected character \"$c\"")
            }
        }
        return tokens
    }
}
