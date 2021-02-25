-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt # core serialization annotations

# kotlinx-serialization-json specific. Add this if you have java.lang.NoClassDefFoundError kotlinx.serialization.json.JsonObjectSerializer
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.example.federated_ml.**$$serializer { *; } # <-- change package name to your app's
-keepclassmembers class com.example.federated_ml.** { # <-- change package name to your app's
    *** Companion;
}
-keepclasseswithmembers class com.example.federated_ml.** { # <-- change package name to your app's
    kotlinx.serialization.KSerializer serializer(...);
}