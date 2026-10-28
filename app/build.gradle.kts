import com.android.build.api.variant.BuildConfigField
import java.util.Properties

plugins {
    id("com.android.application")
    // Kotlin support is built into AGP 9+, so the old "org.jetbrains.kotlin.android" plugin is gone.
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------
// App configuration (React .env -> BuildConfig). Looked up in this order:
//   1) environment variable  2) local.properties  3) gradle -P property
// Names: SUPABASE_URL, SUPABASE_KEY, ADMIN_PHONE, ADMIN_WA
// Everything is a lazy Provider (no eager file/env reads while Gradle configures) and the values
// are pushed into BuildConfig through the variant API (androidComponents.onVariants) below.
// ---------------------------------------------------------------------------
val localProps: Provider<Properties> = providers
    .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
    .asText
    .map { text -> Properties().apply { text.reader().use { load(it) } } }

fun cfg(name: String): Provider<String> =
    providers.environmentVariable(name)
        .orElse(localProps.map { it.getProperty(name) })   // null (key missing) = no value -> falls through
        .orElse(providers.gradleProperty(name))
        .orElse("")
        .map { it.trim().replace("\\", "\\\\").replace("\"", "\\\"") }

val buildConfigKeys = listOf("SUPABASE_URL", "SUPABASE_KEY", "ADMIN_PHONE", "ADMIN_WA")

android {
    namespace = "com.srizwan.task"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.srizwan.task"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

// Variant API: BuildConfig fields are lazy providers (replaces defaultConfig.buildConfigField).
androidComponents {
    onVariants { variant ->
        buildConfigKeys.forEach { key ->
            variant.buildConfigFields.put(
                key,
                cfg(key).map { value -> BuildConfigField("String", "\"$value\"", null) }
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
