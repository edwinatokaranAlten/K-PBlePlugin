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

    // iOS target (device only)
    iosArm64 {
        binaries.framework {
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

        val iosArm64Main by getting {
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

// Task to create XCFramework (device only)
tasks.register("createXCFramework") {
    group = "build"
    description = "Creates XCFramework for iOS (device only)"

    dependsOn("linkDebugFrameworkIosArm64")

    doLast {
        val xcframeworkPath = "${layout.buildDirectory.get()}/XCFrameworks/debug/NioxPlugin.xcframework"
        delete(xcframeworkPath)

        exec {
            commandLine(
                "xcodebuild", "-create-xcframework",
                "-framework", "${layout.buildDirectory.get()}/bin/iosArm64/debugFramework/NioxPlugin.framework",
                "-output", xcframeworkPath
            )
        }

        println("XCFramework created at: $xcframeworkPath")
    }
}

tasks.register("createReleaseXCFramework") {
    group = "build"
    description = "Creates Release XCFramework for iOS (device only)"

    dependsOn("linkReleaseFrameworkIosArm64")

    doLast {
        val buildDir = layout.buildDirectory.get()
        val xcframeworkPath = "$buildDir/XCFrameworks/release/NioxPlugin.xcframework"
        delete(xcframeworkPath)

        // Create XCFramework with device framework only
        exec {
            commandLine(
                "xcodebuild", "-create-xcframework",
                "-framework", "$buildDir/bin/iosArm64/releaseFramework/NioxPlugin.framework",
                "-output", xcframeworkPath
            )
        }

        println("Release XCFramework created at: $xcframeworkPath")
    }
}

// Android AAR is automatically generated in build/outputs/aar/ when running assembleRelease
