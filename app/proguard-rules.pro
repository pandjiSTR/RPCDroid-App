-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Dibuat lewat nama kelas di proses shell oleh Shizuku; tidak boleh di-rename/dihapus R8
-keep class dev.rpcdroid.nr.shizuku.** { *; }
