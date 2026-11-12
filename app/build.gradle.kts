import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localSigningFingerprint = "2eb75370e7dee27afc7297eb7c3ef2ddbef0e37144780414825b5f11421dcb13"
val ciUnsigned = providers.gradleProperty("ciUnsigned").map { it.toBooleanStrict() }.orElse(false).get()

android {
    namespace = "com.hongguo.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.hongguo.android"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("appVersionCode").orElse("41").get().toInt()
        versionName = providers.gradleProperty("appVersionName").orElse("0.7.12-local.10").get()
        buildConfigField("String", "UPDATE_REPOSITORY", "\"LiuWQ0809/hongguo-android\"")
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"https://github.com/LiuWQ0809/hongguo-android/releases/latest/download/update.json\"")
        buildConfigField("boolean", "LOCAL_UPDATE_ENABLED", "true")
        testInstrumentationRunner = "com.hongguo.android.HongguoTestRunner"
    }
    signingConfigs {
        getByName("debug") {
            providers.gradleProperty("localSigningStore").orNull?.let { storeFile = file(it) }
        }
    }
    buildTypes {
        debug { if (ciUnsigned) signingConfig = null }
        release {
            isMinifyEnabled = false
            if (!ciUnsigned) signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

val verifyLocalSigning by tasks.registering {
    doLast {
        val config = android.signingConfigs.getByName("debug")
        val store = config.storeFile ?: error("原签名密钥不存在，已停止构建；请找回原密钥")
        check(store.isFile) { "原签名密钥不存在，已停止构建；请找回原密钥" }
        val keyStore = KeyStore.getInstance("JKS")
        store.inputStream().use { keyStore.load(it, config.storePassword?.toCharArray()) }
        val certificate = keyStore.getCertificate(config.keyAlias)
            ?: error("原签名证书不存在，已停止构建")
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
        check(digest == localSigningFingerprint) { "签名与已发布版本不一致，已停止构建" }
    }
}

tasks.named("preBuild") { if (!ciUnsigned) dependsOn(verifyLocalSigning) }
if (ciUnsigned) tasks.matching { it.name in setOf("packageDebug", "packageDebugBundle", "packageDebugAndroidTest") }.configureEach {
    doFirst { error("无签名验证模式不能生成 debug 安装包") }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
