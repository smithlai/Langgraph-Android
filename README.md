Working Steps:

## Module
root/setting.gradle.kts
```kotlin
.....
include(":SmithToolCalls")
......
```
root/build.gradle.kts
```kotlin
plugins {
    .....
    id ("kotlinx-serialization")
    .....

}

dependencies {
    ....
    implementation(project(":SmithToolCalls"))
    // for @Serialization and @Annotation
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.10")
    implementation("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.1.10")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("io.github.classgraph:classgraph:4.8.179")
}
```


in root/Application.kt
```kotlin
class XXXApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SmolChatApplication)
            modules(
                listOf(
                    KoinAppModule().module,
                    SmithToolCallsModule().module // 添加 Android module 的 Koin module
                )
            )
        }
        ...
        ...
    }
}
```

## Jetpack compose

__smith_rag/build.gradle.kts__
```kotlin
plugins {
    .....
    alias(libs.plugins.kotlin.compose)  // https://developer.android.com/develop/ui/compose/compiler
    id("com.google.devtools.ksp")   // for @ComponentScan, or this may cause "org.koin.core.error.NoBeanDefFoundException: No definition found for type ChatViewModel"
    
}

android{
    ....
    buildFeatures {
        compose = true  // for jetpack compose
        buildConfig = true  //for define veriable
    }
    ....
}
....
// Koin Annotations 的一個 編譯時驗證機制，用來在 KSP（Kotlin Symbol Processing）階段檢查 Koin 設定是否正確
ksp {
    arg("KOIN_CONFIG_CHECK", "true")
}
....

dependencies {
    ....
    // Koin: dependency injection
    // for @Single @Module @ComponentScan
    libs.koin.annotations?.let { implementation(it) } ?: implementation("io.insert-koin:koin-annotations:1.3.1")
    ksp(libs.koin.ksp.compiler)?.let { implementation(it) } ?: implementation("io.insert-koin:koin-ksp-compiler:1.3.1")   // for @ComponentScan automate generate module
    libs.koin.android?.let { implementation(it) } ?: implementation("io.insert-koin:koin-android:3.5.6")
    libs.koin.androidx.compose?.let { implementation(it) } ?: implementation("io.insert-koin:koin-androidx-compose:3.5.6")
    libs.androidx.activity.compose?.let { implementation(it) } ?: implementation("androidx.activity:activity-compose:1.9.3")  // for rememberLauncherForActivityResult
    ....

    // for @Serialization and @Annotation
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.10")
    implementation("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.1.10")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("io.github.classgraph:classgraph:4.8.179")
}
....
```

