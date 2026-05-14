# Keep kotlinx.serialization metadata.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.kzaller.shelf.**$$serializer { *; }
-keepclassmembers class com.kzaller.shelf.** {
    *** Companion;
}
-keepclasseswithmembers class com.kzaller.shelf.** {
    kotlinx.serialization.KSerializer serializer(...);
}
