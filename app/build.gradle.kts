import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
fun setting(name: String): String = System.getenv(name) ?: local.getProperty(name, "")
fun literal(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

android {
    namespace = "pe.vozruta.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "pe.vozruta.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["mapsKey"] = setting("MAPS_API_KEY")
        buildConfigField("boolean", "MAPS_ENABLED", setting("MAPS_API_KEY").isNotBlank().toString())
        buildConfigField("String", "SERVICE_URL", literal(setting("SERVICE_URL")))
        buildConfigField("String", "RIDER_TOKEN", literal(setting("RIDER_TOKEN")))
        buildConfigField("String", "OPERATOR_TOKEN", literal(setting("OPERATOR_TOKEN")))
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("com.google.maps.android:maps-compose:6.12.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    testImplementation("junit:junit:4.13.2")
}
