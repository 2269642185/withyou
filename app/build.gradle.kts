plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.withyou.anniversary"
    compileSdk = 36
    defaultConfig {
        applicationId = providers.gradleProperty("validationApplicationId").orElse("com.withyou.anniversary").get().also {
            require(it in setOf("com.withyou.anniversary", "com.withyou.anniversary.validation"))
        }
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("updateFixtureCode").orElse("9").get().toInt()
        versionName = "1.6.1"
        buildConfigField("String", "CLOUD_ENV", "\"withyou-sync-d0gg468vheead040b\"")
        buildConfigField("String", "CLOUD_REGISTER_URL", "\"${providers.gradleProperty("cloudRegisterUrl").orElse("https://withyou-sync-d0gg468vheead040b-1322438694.ap-shanghai.app.tcloudbase.com/withyou-register").get().replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "UPDATE_REPOSITORY", "\"${providers.gradleProperty("updateRepository").orElse("2269642185/withyou").get().replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation(files("libs/exifinterface-1.3.7.aar"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(bom)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
