plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

ktlint {
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}

tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude { it.file.path.contains("build") || it.file.path.contains("generated") }
}

kotlin {
    android {
        namespace = "dev.j3pf.rp.ui.shared"
        compileSdk = 37
        minSdk = 29

        withHostTest {}
    }

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        testRuns["test"].executionTask.configure { useJUnit() }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedUi"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":protocol"))
            api(project(":relay-client"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            // Adopted after the S13 compile spike (spike/adaptive @ d7c546d): resolves for
            // iosSimulatorArm64, no forced Compose stack changes (see docs/parity G-6, ODD L17).
            implementation(libs.compose.material3.adaptive)
            implementation(libs.markdown.m3)
            implementation(libs.markdown.code)
        }
        androidMain.dependencies {
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.mlkit.barcode)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
