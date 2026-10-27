import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.pettranslator"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.pettranslator"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    androidResources { noCompress += "tflite" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    implementation("com.google.ai.edge.litert:litert-api:1.4.2")
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val verifyBundledYamnet by tasks.registering {
    doLast {
        val model = file("src/main/assets/yamnet.tflite")
        check(model.length() == 4_126_810L) { "Unexpected YAMNet asset size" }
        val sha = MessageDigest.getInstance("SHA-256").digest(model.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(sha == "10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de") {
            "YAMNet model checksum mismatch: $sha; do not substitute models"
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyBundledYamnet) }

val bundleYamnetLicenses by tasks.registering(Copy::class) {
    from(rootProject.file("licenses/yamnet"))
    into(layout.buildDirectory.dir("generated/yamnet-licenses"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/yamnet-licenses"))
tasks.named("preBuild") { dependsOn(bundleYamnetLicenses) }

