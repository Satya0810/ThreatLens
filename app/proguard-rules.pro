# ===========================================================================
# ThreatLens / SafeQR Scanner — ProGuard / R8 Rules
# ===========================================================================

# ── Retrofit ───────────────────────────────────────────────────────────────
# Keep Retrofit annotations
-keepattributes Signature
-keepattributes *Annotation*

# Keep Retrofit service interfaces
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Retrofit does reflection on generic parameter types
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep Retrofit response/request model classes (Gson serialization)
-keep class com.safeqr.scanner.data.remote.** { *; }

# ── Gson ───────────────────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep fields annotated with @SerializedName
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Room ───────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# ── Data model classes (used by Gson/Room) ─────────────────────────────────
-keep class com.safeqr.scanner.data.model.** { *; }
-keep class com.safeqr.scanner.data.local.ScanEntity { *; }

# ── CertificateEngine ─────────────────────────────────────────────────────
-keep class com.safeqr.scanner.security.CertificateEngine$CertPayload { *; }
-keep class com.safeqr.scanner.security.CertificateEngine$VerifyResult { *; }

# ── OkHttp ─────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**

# ── ML Kit ─────────────────────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── General ────────────────────────────────────────────────────────────────
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# Keep the application class
-keep class com.safeqr.scanner.SafeQRApplication { *; }
