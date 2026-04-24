# ========================================================================
# Pantrie ProGuard / R8 rules
# Keeps reflection- and serialization-backed classes safe across minify + shrink.
# Validate on every release build via `./gradlew :app:assembleRelease` + smoke test.
# ========================================================================

# ---- Kotlin ----
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions
-dontnote kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }

# ---- Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# ---- kotlinx.serialization ----
# Preserve @Serializable classes and generated $serializer companions.
-keep,includedescriptorclasses class app.pantrie.**$$serializer { *; }
-keepclassmembers class app.pantrie.** {
  *** Companion;
}
-keepclasseswithmembers class app.pantrie.** {
  kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# ---- Retrofit ----
-keepattributes Signature, Exceptions
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-keep,allowobfuscation interface retrofit2.http.** { *; }
-keep,allowobfuscation @interface retrofit2.http.**
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# ---- Hilt / Dagger ----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep,allowobfuscation @interface dagger.hilt.**

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.Dao { *; }
-keep @androidx.room.Entity class * { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# ---- SQLCipher (Zetetic) ----
-keep class net.zetetic.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.zetetic.**
-dontwarn net.sqlcipher.**

# ---- AndroidX Security + Tink ----
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.api.client.**

# ---- Credential Manager + Google Identity ----
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.** { *; }

# ---- Play Integrity ----
-keep class com.google.android.play.core.integrity.** { *; }
-dontwarn com.google.android.play.core.integrity.**

# ---- Play Billing ----
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# ---- CameraX ----
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ---- Coil ----
-keep class coil.** { *; }
-dontwarn coil.**

# ---- WorkManager ----
-keep class androidx.work.** { *; }

# ---- Compose ----
# Compose compiler handles most keep rules; keep previews out of release builds.
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
  void sourceInformation(androidx.compose.runtime.Composer, java.lang.String);
  void sourceInformationMarkerStart(androidx.compose.runtime.Composer, int, java.lang.String);
  void sourceInformationMarkerEnd(androidx.compose.runtime.Composer);
}

# ---- Strip logs in release ----
-assumenosideeffects class android.util.Log {
  public static int v(...);
  public static int d(...);
  public static int i(...);
  public static int w(...);
  public static int e(...);
  public static boolean isLoggable(java.lang.String, int);
}

# ---- Keep Pantrie entry points reflectively referenced ----
-keep class app.pantrie.PantrieApplication { *; }
-keep class app.pantrie.MainActivity { *; }

# ---- Keep DTOs referenced by Retrofit (serializable) ----
-keep class app.pantrie.network.dto.** { *; }
-keep class app.pantrie.data.entities.** { *; }

# Defensive catch-all for Hilt generated code
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class hilt_aggregated_deps.** { *; }

# Don't strip line numbers in stack traces — map file resolves them for Play Console crashes.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
