pluginManagement {
    includeBuild("build-logic")
    repositories {
        maven { url = java.net.URI("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        maven { url = java.net.URI("https://repo.huaweicloud.com/repository/maven/") }
        maven { url = java.net.URI("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application", "com.android.library" -> {
                    useModule("com.android.tools.build:gradle:${requested.version}")
                }
                "org.jetbrains.kotlin.android",
                "org.jetbrains.kotlin.jvm",
                "org.jetbrains.kotlin.plugin.serialization",
                "org.jetbrains.kotlin.plugin.parcelize" -> {
                    useModule("${requested.id.id}:${requested.id.id}.gradle.plugin:${requested.version}")
                }
                "com.google.devtools.ksp" -> {
                    useModule("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:${requested.version}")
                }
                "com.palantir.git-version" -> {
                    useModule("com.palantir.gradle.gitversion:gradle-git-version:${requested.version}")
                }
                "com.mikepenz.aboutlibraries.plugin.android" -> {
                    useModule("com.mikepenz.aboutlibraries.plugin.android:com.mikepenz.aboutlibraries.plugin.android.gradle.plugin:${requested.version}")
                }
            }
        }
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = java.net.URI("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        maven { url = java.net.URI("https://repo.huaweicloud.com/repository/maven/") }
        maven { url = java.net.URI("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}

rootProject.name = "fcitx5-android"

include(":lib:common")
include(":lib:fcitx5")
include(":lib:fcitx5-lua")
include(":lib:libime")
include(":lib:fcitx5-chinese-addons")
include(":codegen")
include(":app")
include(":host-bridge")
include(":lib:plugin-base")
include(":plugin:anthy")
include(":plugin:clipboard-filter")
include(":plugin:unikey")
include(":plugin:rime")
include(":plugin:hangul")
include(":plugin:chewing")
include(":plugin:sayura")
include(":plugin:jyutping")
include(":plugin:thai")
