# Tink (androidx.security-crypto) uses service loading/reflection - keep it intact.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-keep class com.google.android.gms.security.** { *; }
-dontwarn com.google.android.gms.security.**

# Room generated code is fine with R8; OkHttp/Compose ship their own rules.
