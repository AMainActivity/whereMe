plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }
dependencyLocking { lockAllConfigurations() }

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(libs.junit)
}
