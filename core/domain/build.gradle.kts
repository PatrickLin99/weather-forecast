plugins {
    alias(libs.plugins.weatherapp.android.library)
    alias(libs.plugins.weatherapp.android.hilt)
}

android {
    namespace = "com.example.weatherforecast.core.domain"
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(libs.kotlinx.coroutines.core)
}