dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    // Share the same version catalog as the main build.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"

include(":convention")