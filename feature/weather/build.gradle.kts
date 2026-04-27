plugins {
    alias(libs.plugins.weatherapp.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.weatherforecast.feature.weather"
}

dependencies {
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.accompanist.permissions)
}