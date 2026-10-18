# Project specific ProGuard / R8 rules for Animite

# Line numbers & attributes for readable stack traces in crash logs
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    kotlinx.serialization.KSerializer INSTANCE;
}
-keep,allowobfuscation,allowshrinking class * {
    <init>(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Keep local models serialized to disk/JSON in storage
-keep class com.imashnake.animite.profile.** { *; }
-keep class com.imashnake.animite.media.EpisodeWatchHistory { *; }
-keep class com.imashnake.animite.media.source.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.ui.** { *; }
-dontwarn androidx.media3.**

# Keep MediaCodec and renderers loaded by reflection in ExoPlayer
-keep class * extends androidx.media3.exoplayer.mediacodec.MediaCodecAdapter$Factory { *; }
-keep class * extends androidx.media3.exoplayer.Renderer { *; }
-keep class * extends androidx.media3.extractor.Extractor { *; }

# Coil 3
-keep class coil3.** { *; }
-dontwarn coil3.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**

# Apollo GraphQL
-keep class com.apollographql.apollo3.** { *; }
-dontwarn com.apollographql.apollo3.**

# Saket Cascade
-keep class me.saket.cascade.** { *; }
-dontwarn me.saket.cascade.**

# Compose runtime & navigation
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.navigation.** { *; }