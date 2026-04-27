plugins {
    alias(libs.plugins.weatherapp.android.library)
    alias(libs.plugins.weatherapp.android.hilt)
}

android {
    namespace = "com.example.weatherforecast.core.location"
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
}