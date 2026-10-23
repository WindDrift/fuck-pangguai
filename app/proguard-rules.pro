
# Keep metadata used by kotlinx.serialization and Retrofit.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, Exceptions
-keep class kotlin.Metadata { *; }

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.winddrift.pangguai.**$$serializer { *; }
-keepclassmembers class io.github.winddrift.pangguai.** { *** Companion; }
-keepclasseswithmembers class io.github.winddrift.pangguai.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class io.github.winddrift.pangguai.net.dto.** { *; }

# Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations, AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
