plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest {
            // Keep fixture paths compatible with sync-fixtures.sh and existing tooling.
            resources.srcDir("src/test/resources")
        }
    }
}

// Compatibility entry point used by CI, scripts, and existing developer workflows.
tasks.register<Test>("test") {
    group = "verification"
    description = "Runs protocol tests on the JVM, including the shared tests."
    val jvmTest = tasks.named<Test>("jvmTest")
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    dependsOn("jvmTestClasses")
    useJUnit()
}
