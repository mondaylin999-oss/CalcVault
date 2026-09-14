package com.calcvault.ui.calculator

/** What pressing a key does. */
sealed interface KeyAction {
    /** Appends text to the expression. */
    data class Insert(val text: String) : KeyAction
    data object Clear : KeyAction
    data object Backspace : KeyAction
    data object Equals : KeyAction
    data object ToggleSecond : KeyAction
    data object ToggleAngleUnit : KeyAction
}

/** Visual weight, which decides a key's colour. */
enum class KeyStyle { DIGIT, FUNCTION, OPERATOR, ACCENT, DESTRUCTIVE }

data class Key(
    val label: String,
    val action: KeyAction,
    val style: KeyStyle = KeyStyle.FUNCTION,
    /** Label and action while the 2nd modifier is engaged. */
    val secondLabel: String? = null,
    val secondAction: KeyAction? = null,
)

/**
 * The scientific keypad: five columns, with the numeric block on the right where
 * a thumb reaches it and the function column on the left.
 */
object Keypad {

    private fun fn(name: String) = KeyAction.Insert("$name(")

    val rows: List<List<Key>> = listOf(
        listOf(
            Key("2nd", KeyAction.ToggleSecond, KeyStyle.FUNCTION),
            Key("DEG", KeyAction.ToggleAngleUnit, KeyStyle.FUNCTION),
            Key("(", KeyAction.Insert("("), KeyStyle.FUNCTION),
            Key(")", KeyAction.Insert(")"), KeyStyle.FUNCTION),
            Key("⌫", KeyAction.Backspace, KeyStyle.DESTRUCTIVE),
        ),
        listOf(
            Key("sin", fn("sin"), KeyStyle.FUNCTION, "asin", fn("asin")),
            Key("cos", fn("cos"), KeyStyle.FUNCTION, "acos", fn("acos")),
            Key("tan", fn("tan"), KeyStyle.FUNCTION, "atan", fn("atan")),
            Key("π", KeyAction.Insert("π"), KeyStyle.FUNCTION, "e", KeyAction.Insert("e")),
            Key("C", KeyAction.Clear, KeyStyle.DESTRUCTIVE),
        ),
        listOf(
            Key("ln", fn("ln"), KeyStyle.FUNCTION, "e^", KeyAction.Insert("e^")),
            Key("log", fn("log"), KeyStyle.FUNCTION, "10^", KeyAction.Insert("10^")),
            Key("√", KeyAction.Insert("√("), KeyStyle.FUNCTION, "∛", fn("cbrt")),
            Key("^", KeyAction.Insert("^"), KeyStyle.FUNCTION, "|x|", fn("abs")),
            Key("÷", KeyAction.Insert("÷"), KeyStyle.OPERATOR),
        ),
        listOf(
            Key("!", KeyAction.Insert("!"), KeyStyle.FUNCTION, "sinh", fn("sinh")),
            Key("7", KeyAction.Insert("7"), KeyStyle.DIGIT),
            Key("8", KeyAction.Insert("8"), KeyStyle.DIGIT),
            Key("9", KeyAction.Insert("9"), KeyStyle.DIGIT),
            Key("×", KeyAction.Insert("×"), KeyStyle.OPERATOR),
        ),
        listOf(
            Key("e", KeyAction.Insert("e"), KeyStyle.FUNCTION, "cosh", fn("cosh")),
            Key("4", KeyAction.Insert("4"), KeyStyle.DIGIT),
            Key("5", KeyAction.Insert("5"), KeyStyle.DIGIT),
            Key("6", KeyAction.Insert("6"), KeyStyle.DIGIT),
            Key("−", KeyAction.Insert("−"), KeyStyle.OPERATOR),
        ),
        listOf(
            Key("%", KeyAction.Insert("%"), KeyStyle.FUNCTION, "tanh", fn("tanh")),
            Key("1", KeyAction.Insert("1"), KeyStyle.DIGIT),
            Key("2", KeyAction.Insert("2"), KeyStyle.DIGIT),
            Key("3", KeyAction.Insert("3"), KeyStyle.DIGIT),
            Key("+", KeyAction.Insert("+"), KeyStyle.OPERATOR),
        ),
        listOf(
            Key("Ans", KeyAction.Insert("Ans"), KeyStyle.FUNCTION, "mod", KeyAction.Insert(" mod ")),
            Key("0", KeyAction.Insert("0"), KeyStyle.DIGIT),
            Key(".", KeyAction.Insert("."), KeyStyle.DIGIT),
            Key("E", KeyAction.Insert("E"), KeyStyle.DIGIT),
            Key("=", KeyAction.Equals, KeyStyle.ACCENT),
        ),
    )
}
