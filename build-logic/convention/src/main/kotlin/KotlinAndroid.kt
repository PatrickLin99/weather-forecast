import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Configures the Kotlin compile tasks with JVM 17 target and opt-in flag.
 * Shared by all Android modules. Android DSL (compileSdk/minSdk/compileOptions)
 * is configured per-plugin because AGP 9 removed those members from the
 * shared CommonExtension type.
 */
internal fun Project.configureKotlinJvm17() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        }
    }
}