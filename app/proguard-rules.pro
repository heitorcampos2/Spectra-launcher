# Spectra Launcher — regras R8
-keep class com.hazagou.spectra.service.** { *; }
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-dontwarn org.slf4j.**
