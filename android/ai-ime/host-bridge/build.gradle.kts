plugins {
    id("org.fcitx.fcitx5.android.lib-convention")
    id("org.fcitx.fcitx5.android.data-descriptor")
    id("org.fcitx.fcitx5.android.fcitx-component")
    `maven-publish`
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

group = "org.fcitx.fcitx5.android"
version = oemRuntimeProperty("oemFcitxRuntimeVersion")
val buildTime = providers.gradleProperty("buildTimestamp").orNull?.toLong() ?: System.currentTimeMillis()

android {
    namespace = "org.fcitx.fcitx5.android"
    ndkVersion = providers.gradleProperty("ndkVersion")
        .orElse(providers.environmentVariable("NDK_VERSION"))
        .getOrElse("28.0.13004108")
    defaultConfig {
        buildConfigField("String", "APPLICATION_ID", "\"org.fcitx.fcitx5.android\"")
        buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
        buildConfigField("String", "BUILD_GIT_HASH", "\"local\"")
        buildConfigField("long", "BUILD_TIME", "${buildTime}L")
        buildConfigField("String", "DATA_DESCRIPTOR_NAME", "\"descriptor.json\"")
        consumerProguardFiles("proguard-rules.pro", "../app/onnx-jni-rules.pro")
    }

    buildFeatures {
        buildConfig = true
        prefab = true
        viewBinding = true
        resValues = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    androidResources {
        noCompress += listOf("onnx", "wav")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    defaultConfig {
        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                val prebuiltDir = rootProject.projectDir.resolve("lib/fcitx5/src/main/cpp/prebuilt")
                arguments(
                    "-DANDROID_STL=c++_shared",
                    "-DVERSION_NAME=1.0",
                    "-DPREBUILT_DIR=${prebuiltDir.absolutePath}"
                )
                targets(
                    "native-lib",
                    "copy-fcitx5-modules",
                    "androidfrontend",
                    "androidkeyboard",
                    "androidnotification",
                    "rime"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../app/src/main/cpp/CMakeLists.txt")
            version = providers.gradleProperty("cmakeVersion")
                .orElse(providers.environmentVariable("CMAKE_VERSION"))
                .getOrElse("3.31.6")
        }
    }

    buildTypes {
        release {
            resValue("mipmap", "app_icon", "@mipmap/ic_launcher")
            resValue("mipmap", "app_icon_round", "@mipmap/ic_launcher_round")
            resValue("string", "fcitx_app_name", "@string/app_name_release")
        }
        debug {
            resValue("mipmap", "app_icon", "@mipmap/ic_launcher_debug")
            resValue("mipmap", "app_icon_round", "@mipmap/ic_launcher_round_debug")
            resValue("string", "fcitx_app_name", "@string/app_name_debug")
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("../app/src/main/AndroidManifest.xml")
            java.setSrcDirs(listOf("../app/src/main/java"))
            kotlin.setSrcDirs(listOf("../app/src/main/java"))
            res.setSrcDirs(listOf("../app/src/main/res", "../app/src/main/res-icons", "src/main/res"))
            // Avoid duplicate asset packaging when host-bridge and app are consumed together.
            assets.setSrcDirs(listOf(layout.buildDirectory.dir("runtime-assets")))
            jniLibs.setSrcDirs(listOf("../app/src/main/jniLibs"))
        }
    }
}

generateDataDescriptor {
    // Inference reads the model directly from AssetManager.
    excludes.add("handwriting/ppocrv6-small.onnx")
    symlinks.put("usr/share/rime-data/opencc", "usr/share/opencc")
}

fcitxComponent {
    includeLibs = listOf(
        "fcitx5",
        "fcitx5-lua",
        "libime",
        "fcitx5-chinese-addons"
    )
    excludeFiles = listOf("cangjie", "erbi", "qxm", "wanfeng").map {
        "usr/share/fcitx5/inputmethod/$it.conf"
    }
    installPrebuiltAssets = true
}

ksp {
    arg("room.schemaLocation", "${projectDir.parentFile.resolve("app/schemas").absolutePath}")
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.32.0")
    ksp(project(":codegen"))
    implementation(project(":lib:fcitx5"))
    implementation(project(":lib:fcitx5-lua"))
    implementation(project(":lib:libime"))
    implementation(project(":lib:fcitx5-chinese-addons"))
    implementation(project(":lib:common"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.startup)
    implementation(libs.androidx.viewpager2)
    implementation(libs.material)
    implementation(libs.arrow.core)
    implementation(libs.arrow.functions)
    implementation(libs.imagecropper)
    implementation(libs.flexbox)
    implementation(libs.dependency)
    implementation(libs.timber)
    implementation(libs.splitties.bitflags)
    implementation(libs.splitties.dimensions)
    implementation(libs.splitties.resources)
    implementation(libs.splitties.views.dsl)
    implementation(libs.splitties.views.dsl.appcompat)
    implementation(libs.splitties.views.dsl.constraintlayout)
    implementation(libs.splitties.views.dsl.coordinatorlayout)
    implementation(libs.splitties.views.dsl.recyclerview)
    implementation(libs.splitties.views.recyclerview)
    implementation(libs.aboutlibraries.core)
    implementation("dev.rikka.shizuku:api:13.1.5")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.lifecycle.testing)
    androidTestImplementation(libs.junit)
}

configurations {
    all {
        exclude(group = "androidx.profileinstaller", module = "profileinstaller")
        exclude(group = "com.louiscad.splitties", module = "splitties-appctx")
        exclude(group = "com.louiscad.splitties", module = "splitties-systemservices")
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn("installFcitxComponent", "generateDataDescriptor")
}

tasks.named("generateDataDescriptor").configure {
    dependsOn("installFcitxComponent", "deleteFcitxComponentExcludeFiles")
}

tasks.matching { it.name.startsWith("generate") && it.name.contains("Lint") && it.name.endsWith("Model") }.configureEach {
    dependsOn("generateDataDescriptor")
}

tasks.matching { it.name.startsWith("lint") && it.name.contains("Analyze") }.configureEach {
    dependsOn("generateDataDescriptor")
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
            artifactId = "app"
            version = project.version.toString()
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

// Stage precompiled dictionaries with the native configs and translations.
// The original Rime and libime data are installed by the optional packages.
val stageRuntimeAssets by tasks.registering(Sync::class) {
    dependsOn("deleteFcitxComponentExcludeFiles")
    from("src/main/assets") {
        exclude("descriptor.json", "usr/share/rime-data/**", "usr/share/libime/**", "usr/share/fcitx5/table/**")
    }
    from(layout.buildDirectory.dir("fcitx-component-assets")) {
        exclude("descriptor.json", "usr/share/rime-data/**", "usr/share/libime/**", "usr/share/fcitx5/table/**")
    }
    from("src/main/offline-dictionaries")
    from("src/main/precompiled-rime") { into("usr/share/rime-data/build") }
    from("../app/src/main/assets/handwriting") { into("handwriting") }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    into(layout.buildDirectory.dir("runtime-assets"))
}

tasks.named<DataDescriptorPlugin.DataDescriptorTask>("generateDataDescriptor") {
    dependsOn(stageRuntimeAssets)
    inputDir.set(layout.buildDirectory.dir("runtime-assets"))
    outputFile.set(layout.buildDirectory.file("runtime-assets/descriptor.json"))
}
