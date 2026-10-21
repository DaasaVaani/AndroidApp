import java.util.Properties
plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.compose) }
val settings = Properties().apply {
    rootProject.file("build/generated/config.properties").inputStream().use { load(it) }
}
android {
    namespace = settings.getProperty("applicationId")
    compileSdk = settings.getProperty("compileSdk").toInt()
    defaultConfig {
        applicationId = settings.getProperty("applicationId")
        minSdk = settings.getProperty("minSdk").toInt()
        targetSdk = settings.getProperty("targetSdk").toInt()
        versionCode = settings.getProperty("versionCode").toInt()
        versionName = settings.getProperty("versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets["main"].res.srcDirs("../shared/generated/android", "build/generated/settings")
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.compose.ui.test.manifest)
}
