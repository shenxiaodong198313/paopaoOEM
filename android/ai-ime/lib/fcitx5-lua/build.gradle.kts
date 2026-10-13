plugins {
    id("org.fcitx.fcitx5.android.lib-convention")
    id("org.fcitx.fcitx5.android.native-lib-convention")
    id("org.fcitx.fcitx5.android.fcitx-headers")
    `maven-publish`
}

group = "org.fcitx.fcitx5.android.lib"
version = "1.0"

android {
    namespace = "org.fcitx.fcitx5.android.lib.fcitx5_lua"

    defaultConfig {
        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                targets(
                    // dummy "cmake" target
                    "cmake",
                    // fcitx5-lua
                    "luaaddonloader"
                )
            }
        }
    }

    prefab {
        create("cmake") {
            headers = "src/main/cpp/cmake"
            headerOnly = true
        }
        val moduleHeadersPrefix = "build/headers/usr/include/Fcitx5/Module/fcitx-module"
        create("luaaddonloader") {
            libraryName = "libluaaddonloader"
            headers = "$moduleHeadersPrefix/luaaddonloader"
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
            artifactId = "fcitx5-lua"
            version = project.version.toString()
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
