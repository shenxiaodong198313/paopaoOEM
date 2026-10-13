plugins {
    id("org.fcitx.fcitx5.android.lib-convention")
    id("org.fcitx.fcitx5.android.native-lib-convention")
    id("org.fcitx.fcitx5.android.fcitx-headers")
    `maven-publish`
}

group = "org.fcitx.fcitx5.android.lib"
version = "1.0"

android {
    namespace = "org.fcitx.fcitx5.android.lib.fcitx5_chinese_addons"

    defaultConfig {
        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                targets(
                    // dummy "cmake" target
                    "cmake",
                    // fcitx5-chinese-addons
                    "pinyin",
                    "scel2org5",
                    "table",
                    "chttrans",
                    "fullwidth",
                    "pinyinhelper",
                    "punctuation",
                )
            }
        }
    }

    prefab {
        create("cmake") {
            headerOnly = true
            headers = "src/main/cpp/cmake"
        }
        create("pinyin") {
            libraryName = "libpinyin"
            // no headers
        }
        create("table") {
            libraryName = "libtable"
            // no headers
        }
        create("scel2org5") {
            libraryName = "libscel2org5"
            // no headers
        }
        val moduleHeadersPrefix = "build/headers/usr/include/Fcitx5/Module/fcitx-module"
        create("chttrans") {
            libraryName = "libchttrans"
            // no headers
        }
        create("fullwidth") {
            libraryName = "libfullwidth"
            // no headers
        }
        create("pinyinhelper") {
            libraryName = "libpinyinhelper"
            headers = "$moduleHeadersPrefix/pinyinhelper"
        }
        create("punctuation") {
            libraryName = "libpunctuation"
            headers = "$moduleHeadersPrefix/punctuation"
        }
    }

    publishing {
        singleVariant("release")
    }
}

dependencies {
    implementation(project(":lib:fcitx5"))
    implementation(project(":lib:libime"))
    implementation(project(":lib:fcitx5-lua"))
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
            artifactId = "fcitx5-chinese-addons"
            version = project.version.toString()
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
