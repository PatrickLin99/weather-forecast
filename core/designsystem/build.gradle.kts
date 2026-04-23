plugins {
    alias(libs.plugins.weatherapp.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.weatherforecast.core.designsystem"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}