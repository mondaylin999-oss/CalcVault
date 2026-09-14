# kotlinx.serialization keeps generated serializers reachable through reflection.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.calcvault.** {
    *** Companion;
}
-keepclasseswithmembers class com.calcvault.** {
    kotlinx.serialization.KSerializer serializer(...);
}
