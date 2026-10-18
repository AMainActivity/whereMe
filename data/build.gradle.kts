plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "ru.ama.whereme.data"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        mapOf(
            "BASE_URL" to "baseUrl",
            "GKK_CHECK_KOD" to "gkk_ChechKod",
            "GKK_CHECK_TOKEN" to "gkk_checkToken",
            "GKK_LOG_OUT" to "gkk_Logout",
            "GKK_WRITE_LOC_DATAS" to "gkk_WriteLocDatas"
        ).forEach { (field, property) ->
            buildConfigField("String", field, providers.gradleProperty(property).get())
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    lint { abortOnError = true }
}

kotlin { jvmToolchain(17) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencyLocking { lockAllConfigurations() }

dependencies {
    implementation(libs.datastore.preferences)
    implementation(project(":domain"))
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    // Temporary adapter; removed in the LocationManager migration.
    implementation(libs.gms.base)
    implementation(libs.gms.location)
    implementation(libs.coroutines.play.services)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)
    testImplementation(libs.junit)
    testImplementation(libs.datastore.core.okio)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
