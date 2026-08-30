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

# Enum constant names are data here, not just identifiers: the sort mode and view mode are
# written to DataStore as `name` and read back with `valueOf`, so a renamed constant would
# quietly lose the user's saved shelf settings on upgrade.
-keepclassmembers enum com.kzaller.shelf.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Retrofit builds the API implementation at runtime from the interface's own generic signatures,
# so both the interface and those signatures have to survive. Retrofit ships most of this itself;
# it is spelled out because losing it fails at runtime, not at build time.
-keep,allowobfuscation interface com.kzaller.shelf.data.api.ShelfApi { *; }
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault

# Credential Manager hands the Google sign-in result back inside a Bundle and reconstructs the
# credential type by class name.
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Crash reports from a shrunk build are unreadable without these, and the release build is the
# only one that ever runs on a phone. The mapping file that decodes them is written to
# app/build/outputs/mapping/release/.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
