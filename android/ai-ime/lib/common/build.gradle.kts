plugins {
    id("org.fcitx.fcitx5.android.lib-convention")
    `maven-publish`
}

android {
    namespace = "org.fcitx.fcitx5.android.lib.common"

    buildFeatures {
        aidl = true
    }
    publishing {
        singleVariant("release")
    }
}

group = "org.fcitx.fcitx5.android.lib"
version = "1.0"

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
            artifactId = "common"
            version = project.version.toString()
            pom {
                licenses {
                    name.set("LGPL-2.1")
                    url.set("https://spdx.org/licenses/LGPL-2.1.html")
                }
            }
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
