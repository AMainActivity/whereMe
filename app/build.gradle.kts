plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "ru.ama.whereme"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.ama.whereme"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "=3.0"
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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel)

    // Temporary adapter; removed when the LocationManager migration is complete.
    implementation(libs.gms.base)
    implementation(libs.gms.location)
    implementation(libs.coroutines.play.services)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.dagger)
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
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
