# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.j3pf.rp.**$$serializer { *; }
-keepclassmembers class dev.j3pf.rp.** { *** Companion; }
-keepclasseswithmembers class dev.j3pf.rp.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp / Okio
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Tink
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-keep class com.google.crypto.tink.** { *; }

# Room / WorkManager / Hilt los resuelven sus propias reglas (consumer rules)
