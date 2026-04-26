plugins {
    alias(libs.plugins.weatherapp.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.weatherforecast.feature.citylist"
}