plugins {
    alias(libs.plugins.weatherapp.android.library)
    alias(libs.plugins.weatherapp.android.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.weatherforecast.core.database"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.datetime)
    ksp(libs.androidx.room.compiler)
}