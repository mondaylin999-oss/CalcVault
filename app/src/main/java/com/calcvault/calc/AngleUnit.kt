package com.calcvault.calc

/** Whether trigonometric functions work in degrees or radians. */
enum class AngleUnit {
    DEG,
    RAD;

    /** Converts an angle given in the current unit to radians. */
    fun toRadians(value: Double): Double = if (this == DEG) Math.toRadians(value) else value

    /** Converts a radian result back into the current unit. */
    fun fromRadians(value: Double): Double = if (this == DEG) Math.toDegrees(value) else value

    fun toggled(): AngleUnit = if (this == DEG) RAD else DEG
}
