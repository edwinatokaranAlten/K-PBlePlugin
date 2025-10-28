plugins {
    kotlin("multiplatform") version "1.9.22"
    id("com.android.library") version "8.2.2"
    id("maven-publish")
}

group = "com.niox.nioxplugin"
version = "1.0.0"

kotlin {
    // Android target
    androidTarget {
        publishLibraryVariants("release")
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

    // iOS targets
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "NioxPlugin"
            isStatic = true
        }
    }

    // Windows target (MinGW)
    mingwX64 {
        binaries {
            sharedLib {
                baseName = "nioxplugin"
            }
        }
        compilations.getByName("main") {
            cinterops {
                create("bluetooth") {
                    defFile(project.file("src/nativeInterop/cinterop/bluetooth.def"))
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
            }
        }

        val iosMain by creating {
            dependsOn(commonMain)
        }

        val iosX64Main by getting {
            dependsOn(iosMain)
        }

        val iosArm64Main by getting {
            dependsOn(iosMain)
        }

        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }

        val mingwX64Main by getting {
            dependsOn(commonMain)
        }
    }
}

android {
    namespace = "com.niox.nioxplugin"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

// Task to create XCFramework
tasks.register("createXCFramework") {
    group = "build"
    description = "Creates XCFramework for iOS"

    dependsOn(
        "linkDebugFrameworkIosArm64",
        "linkDebugFrameworkIosX64",
        "linkDebugFrameworkIosSimulatorArm64"
    )

    doLast {
        val xcframeworkPath = "${layout.buildDirectory.get()}/XCFrameworks/debug/NioxPlugin.xcframework"
        delete(xcframeworkPath)

        exec {
            commandLine(
                "xcodebuild", "-create-xcframework",
                "-framework", "${layout.buildDirectory.get()}/bin/iosArm64/debugFramework/NioxPlugin.framework",
                "-framework", "${layout.buildDirectory.get()}/bin/iosX64/debugFramework/NioxPlugin.framework",
                "-framework", "${layout.buildDirectory.get()}/bin/iosSimulatorArm64/debugFramework/NioxPlugin.framework",
                "-output", xcframeworkPath
            )
        }

        println("XCFramework created at: $xcframeworkPath")
    }
}

tasks.register("createReleaseXCFramework") {
    group = "build"
    description = "Creates Release XCFramework for iOS"

    dependsOn(
        "linkReleaseFrameworkIosArm64",
        "linkReleaseFrameworkIosX64",
        "linkReleaseFrameworkIosSimulatorArm64"
    )

    doLast {
        val buildDir = layout.buildDirectory.get()
        val xcframeworkPath = "$buildDir/XCFrameworks/release/NioxPlugin.xcframework"
        delete(xcframeworkPath)

        // Create a universal simulator framework by combining x64 and arm64 simulators
        val simFrameworkPath = "$buildDir/bin/iosSimulator/releaseFramework/NioxPlugin.framework"
        delete(simFrameworkPath)
        mkdir(simFrameworkPath)

        // Copy the base framework structure from one simulator
        copy {
            from("$buildDir/bin/iosSimulatorArm64/releaseFramework/NioxPlugin.framework")
            into(simFrameworkPath)
        }

        // Create fat binary combining both simulator architectures
        exec {
            commandLine(
                "lipo", "-create",
                "$buildDir/bin/iosX64/releaseFramework/NioxPlugin.framework/NioxPlugin",
                "$buildDir/bin/iosSimulatorArm64/releaseFramework/NioxPlugin.framework/NioxPlugin",
                "-output", "$simFrameworkPath/NioxPlugin"
            )
        }

        // Now create XCFramework with device and universal simulator
        exec {
            commandLine(
                "xcodebuild", "-create-xcframework",
                "-framework", "$buildDir/bin/iosArm64/releaseFramework/NioxPlugin.framework",
                "-framework", simFrameworkPath,
                "-output", xcframeworkPath
            )
        }

        println("Release XCFramework created at: $xcframeworkPath")
    }
}

// Android AAR is automatically generated in build/outputs/aar/ when running assembleRelease
