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
    api(projects.core.model)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.coil.compose)
    debugApi(libs.androidx.compose.ui.tooling)
}