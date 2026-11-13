plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("kapt")
    id("com.google.dagger.hilt.android")
}

val releaseSigningEnvironment = listOf(
    "NAGI_SIGNING_STORE_FILE",
    "NAGI_SIGNING_STORE_PASSWORD",
    "NAGI_SIGNING_KEY_ALIAS",
    "NAGI_SIGNING_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it) }

val validateReleaseSigningEnvironment by tasks.registering {
    doLast {
        val missing = releaseSigningEnvironment.filterValues { it.orNull.isNullOrBlank() }.keys
        check(missing.isEmpty()) {
            "Release signing requires exported environment variables: ${missing.joinToString()}"
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseSigningEnvironment)
}

android {
    namespace = "dev.nagi.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.nagi.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
    signingConfigs {
        create("release") {
            storeFile = releaseSigningEnvironment.getValue("NAGI_SIGNING_STORE_FILE").orNull
                ?.takeIf { it.isNotBlank() }?.let { file(it) }
            storePassword = releaseSigningEnvironment.getValue("NAGI_SIGNING_STORE_PASSWORD").orNull
            keyAlias = releaseSigningEnvironment.getValue("NAGI_SIGNING_KEY_ALIAS").orNull
            keyPassword = releaseSigningEnvironment.getValue("NAGI_SIGNING_KEY_PASSWORD").orNull
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
kotlin { jvmToolchain(21) }
kapt { correctErrorTypes = true }

val verifyThirdPartyNotices by tasks.registering {
    group = "verification"
    description = "Check that the reviewed license inventory matches debug and release runtime dependencies."
    doLast {
        val actual = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath").flatMap { name ->
            configurations.getByName(name).resolvedConfiguration.resolvedArtifacts
                .filter { it.id.componentIdentifier is org.gradle.api.artifacts.component.ModuleComponentIdentifier }
                .map { it.moduleVersion.id.toString() }
        }.toSet()
        val reviewed = rootProject.file("third-party/components.txt").readLines().filter { it.isNotBlank() }.toSet()
        check(actual == reviewed) {
            "Third-party inventory needs review. Added: ${(actual - reviewed).sorted()}; removed: ${(reviewed - actual).sorted()}"
        }
        val notices = rootProject.file("third-party/THIRD_PARTY_NOTICES.txt").readLines().toSet()
        check(reviewed.all { it in notices }) { "Third-party notices are missing component entries" }
    }
}
tasks.named("check") { dependsOn(verifyThirdPartyNotices) }

val copyNotices by tasks.registering(Sync::class) {
    dependsOn(verifyThirdPartyNotices)
    from(rootProject.file("LICENSE"))
    from(rootProject.file("third-party/THIRD_PARTY_NOTICES.txt"))
    into(layout.buildDirectory.dir("generated/licenseAssets"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/licenseAssets"))
tasks.named("preBuild") { dependsOn(copyNotices) }

dependencies {
    implementation(project(":nga-sdk")) { exclude(group = "org.json", module = "json") }
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.google.dagger:hilt-android:2.57.2")
    kapt("com.google.dagger:hilt-compiler:2.57.2")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    kapt("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    testImplementation("com.google.dagger:hilt-android-testing:2.57.2")
    kaptTest("com.google.dagger:hilt-compiler:2.57.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
