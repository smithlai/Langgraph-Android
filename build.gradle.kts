plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)  // https://developer.android.com/develop/ui/compose/compiler
    id("com.google.devtools.ksp")   // for @ComponentScan, or this may cause "org.koin.core.error.NoBeanDefFoundException: No definition found for type ChatViewModel"
    id ("kotlinx-serialization")
}

android {
    namespace = "com.smith.lai.smithtoolcalls"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        compose = true  // for jetpack compose
        buildConfig = true  //for define veriable
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            //避免UnitTest因為Log.d造成錯誤 (UnitTest一般使用println，但是被呼叫的物件可能有Log.d)
            isReturnDefaultValues = true
        }
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}
// Koin Annotations 的一個 編譯時驗證機制，用來在 KSP（Kotlin Symbol Processing）階段檢查 Koin 設定是否正確
ksp {
    arg("KOIN_CONFIG_CHECK", "true")
}
dependencies {
    // Koin: dependency injection
    // for @Single @Module @ComponentScan
    libs.koin.annotations?.let { implementation(it) } ?: implementation("io.insert-koin:koin-annotations:1.3.1")
    ksp(libs.koin.ksp.compiler)?.let { implementation(it) } ?: implementation("io.insert-koin:koin-ksp-compiler:1.3.1")   // for @ComponentScan automate generate module
    libs.koin.android?.let { implementation(it) } ?: implementation("io.insert-koin:koin-android:3.5.6")
    libs.koin.androidx.compose?.let { implementation(it) } ?: implementation("io.insert-koin:koin-androidx-compose:3.5.6")
    libs.androidx.activity.compose?.let { implementation(it) } ?: implementation("androidx.activity:activity-compose:1.9.3")  // for rememberLauncherForActivityResult
    implementation(libs.androidx.navigation.compose)


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    // for @Serialization and @Annotation
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.10")
    implementation("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.1.10")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("io.github.classgraph:classgraph:4.8.179")
    implementation(project(":smollm"))
//    testImplementation(project(":smollm"))
//    androidTestImplementation(project(":smollm"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}