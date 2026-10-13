plugins {
    id("org.fcitx.fcitx5.android.lib-convention")
    id("org.fcitx.fcitx5.android.native-lib-convention")
    id("org.fcitx.fcitx5.android.fcitx-headers")
    `maven-publish`
}

group = "org.fcitx.fcitx5.android.lib"
version = "1.0"

android {
    namespace = "org.fcitx.fcitx5.android.lib.libime"

    defaultConfig {
        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                targets(
                    // dummy "cmake" target
                    "cmake",
                    // libime
                    "IMECore",
                    "IMEPinyin",
                    "IMETable"
                )
            }
        }
    }

    prefab {
        create("cmake") {
            headerOnly = true
            headers = "src/main/cpp/cmake"
        }
        val libimeHeadersPrefix = "build/headers/usr/include/LibIME"
        create("IMECore") {
            libraryName = "libIMECore"
            headers = libimeHeadersPrefix
        }
        create("IMEPinyin") {
            libraryName = "libIMEPinyin"
            headers = libimeHeadersPrefix
        }
        create("IMETable") {
            libraryName = "libIMETable"
            headers = libimeHeadersPrefix
        }
    }

    publishing {
        singleVariant("release")
    }
}

dependencies {
    implementation(project(":lib:fcitx5"))
}

publishing {
    repositories {
        maven {
            name = "oemLocal"
            url = uri(rootProject.file("../build/oem-sdk-repo"))
        }
    }
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "libime"
            version = project.version.toString()
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
