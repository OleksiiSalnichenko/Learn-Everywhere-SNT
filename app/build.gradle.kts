plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.learneverywhere.app"
    // compileSdk 37 — вимога androidx-бібліотек у поточних версіях (core-ktx 1.19,
    // compose-bom 2026.08.00 та ін. компільовані проти API 37); android-37 доступний
    // локально в SDK.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.learneverywhere.app"
        // API 26 — мінімум для стабільної роботи MediaSessionService і сучасного
        // SpeechRecognizer (рішення зафіксоване в spec.md §«Рішення щодо реалізації»).
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP 9.0+ built-in Kotlin support бере jvmTarget з compileOptions вище —
    // окремий android.kotlinOptions { } більше не існує (це був DSL плагіна
    // org.jetbrains.kotlin.android, який ми прибрали).

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Дозволяє Robolectric-тестам вантажити ресурси застосунку (шрифти, рядки)
            // і Room-базу in-memory без окремого інструментального (androidTest) раннера.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    // playback/ — foreground-сервіс і медіа-сесія (interfaces.md §playback, тікет 08)
    implementation(libs.androidx.media3.session)

    // translation/ — клієнт MyMemory Translation API (interfaces.md §translation)
    implementation(libs.squareup.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    // Реальна реалізація org.json для юніт-тестів на JVM — android.jar у
    // unit-тестах містить лише заглушки (кидають виняток при виклику).
    testImplementation(libs.org.json)
}

// Robolectric 4.17 читає внутрішні поля FileDescriptor через рефлексію
// (ApplicationSharedMemory) — на JDK 17+ з модульною системою це вимагає
// явного --add-opens, інакше IllegalAccessException до jdk.internal.access.
// Локальне середовище виконує тести на JBR 25, де обмеження ще суворіші.
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.text=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
    )
}
