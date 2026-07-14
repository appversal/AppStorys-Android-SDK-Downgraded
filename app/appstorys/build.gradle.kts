plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    id("maven-publish")
    id("kotlin-parcelize")
    // QA: Paparazzi snapshot tests (Layer 3 of the AppStorys-QA pipeline)
    id("app.cash.paparazzi") version "1.3.5"
}

// ─────────────────────────────────────────────────────────────────────────────
// QA pipeline build isolation — active ONLY when AppStorys-QA passes
// -PqaRunId=<id>. Android Studio, `./gradlew assembleRelease`, JitPack and
// every publish task never pass it, so they keep building into build/ exactly
// as before. Nothing about the shipped SDK changes; this only moves where
// intermediate files land on the local disk.
//
// Why it exists: the QA pipeline, the IDE's background indexer and Windows
// Defender all read/write app/appstorys/build/. On Windows one open handle is
// enough to make Gradle's own output cleanup fail with
//   "Unable to delete directory ...\build\tmp\kotlin-classes\debug"
// which killed compileDebugKotlin before a single snapshot was ever rendered.
// Giving the pipeline a private build root removes the contention entirely,
// on any machine, with no admin rights and no IDE babysitting.
//
// WARNING: never pass -PqaRunId to a publish task — the AAR would then be
// assembled out of build-qa/ instead of build/.
// This must run before the android { } block: AGP reads buildDirectory during
// configuration.
// ─────────────────────────────────────────────────────────────────────────────
val qaRunId = providers.gradleProperty("qaRunId")
if (qaRunId.isPresent) {
    layout.buildDirectory.set(layout.projectDirectory.dir("build-qa"))
}

android {
    namespace = "com.appversal.appstorys"
    compileSdk = 34
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
    defaultConfig {
        minSdk = 22
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.coil.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.retrofit.scalars)
    implementation(libs.okhttp.logging)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("com.google.accompanist:accompanist-coil:0.15.0")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("com.airbnb.android:lottie-compose:6.0.0")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    compileOnly("com.google.firebase:firebase-messaging:23.0.0")

    implementation(libs.gson)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.gif)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.exoplayer.ui)
    implementation(libs.exoplayer.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.activity.compose)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.glance.preview)

    // QA: Paparazzi test suite (Layer 3)
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.coil-kt:coil-test:2.6.0")
    // Dispatchers.setMain(Unconfined) in screenshot tests — Coil's
    // AsyncImagePainter otherwise parks on the main dispatcher queue and
    // Paparazzi renders its single frame before the image ever delivers.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.appversal"
                artifactId = "appstorys"
                version = "3.7.2"
            }
        }
    }
}

// Second half of the QA-only block (see the buildDirectory redirect above).
// Each pipeline run writes its test results to a fresh qa-<runId> directory,
// so even inside build-qa/ nothing contested ever needs deleting.
if (qaRunId.isPresent) {
    val runDir = "test-results/qa-${qaRunId.get()}"
    tasks.withType<Test>().configureEach {
        binaryResultsDirectory.set(layout.buildDirectory.dir("$runDir/binary"))
        reports.junitXml.outputLocation.set(layout.buildDirectory.dir("$runDir/xml"))
        // Paparazzi 1.3.5's PaparazziTestReporter calls a Gradle internal
        // (org.gradle.api.internal.tasks.testing.junit.result.TestFailure)
        // that no longer exists in Gradle 8.13. It only runs while building
        // the HTML test report, so whenever a snapshot genuinely differed the
        // real failure was replaced by a bogus "NoClassDefFoundError" and the
        // tester had no idea which snapshot broke. The pipeline reads the
        // failure PNGs, never this report, so turning it off costs nothing.
        reports.html.required.set(false)
        reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/qa-${qaRunId.get()}"))
    }
}