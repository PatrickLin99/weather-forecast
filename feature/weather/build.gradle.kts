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

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}