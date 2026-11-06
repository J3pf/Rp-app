plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        testRuns["test"].executionTask.configure { useJUnit() }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":protocol"))
            api(project(":relay-client"))
            api(libs.androidx.room.runtime)
            implementation("androidx.room:room-common:${libs.versions.room.get()}")
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    add("kspJvm", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
