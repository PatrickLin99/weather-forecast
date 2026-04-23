plugins {
    alias(libs.plugins.weatherapp.jvm.library)
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.kotlinx.coroutines.core)
}