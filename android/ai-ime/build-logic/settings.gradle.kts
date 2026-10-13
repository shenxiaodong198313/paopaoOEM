pluginManagement {
    repositories {
        maven(url = "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven(url = "https://repo.huaweicloud.com/repository/maven/")
        maven(url = "https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.gradle.kotlin.kotlin-dsl" -> {
                    useModule("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:${requested.version}")
                }
                "org.jetbrains.kotlin.jvm",
                "org.jetbrains.kotlin.plugin.serialization" -> {
                    useModule("${requested.id.id}:${requested.id.id}.gradle.plugin:${requested.version}")
                }
                "com.palantir.git-version" -> {
                    useModule("com.palantir.gradle.gitversion:gradle-git-version:${requested.version}")
                }
            }
        }
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        maven(url = "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven(url = "https://repo.huaweicloud.com/repository/maven/")
        maven(url = "https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
