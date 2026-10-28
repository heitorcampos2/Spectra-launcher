// App de TESTE (só para o GitHub Actions): publica canais, programas e "ver a seguir"
// no provedor de canais, como fazem Sintonia, SmartTube, Crunchyroll etc.
plugins { id("com.android.application") }

android {
    namespace = "com.hazagou.spectra.testpublisher"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.hazagou.spectra.testpublisher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
