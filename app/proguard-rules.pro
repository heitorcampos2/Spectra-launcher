# Spectra Launcher — regras R8
-keep class com.hazagou.spectra.service.** { *; }
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-dontwarn org.slf4j.**

# Controle de outros apps pela depuração local (AdbRemote / ShellInjector)
-keep class com.hazagou.spectra.remote.ShellInjector { *; }
-keep class dadb.** { *; }
-dontwarn dadb.**
