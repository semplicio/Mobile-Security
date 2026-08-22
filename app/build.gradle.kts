plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val smtpUser = providers.gradleProperty("AUTOMBOT_SMTP_USER")
    .orElse(providers.environmentVariable("AUTOMBOT_SMTP_USER"))
    .orElse("")
    .get()
val smtpPassword = providers.gradleProperty("AUTOMBOT_SMTP_PASSWORD")
    .orElse(providers.environmentVariable("AUTOMBOT_SMTP_PASSWORD"))
    .orElse("")
    .get()

fun quoteBuildConfig(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.autombot.security"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.autombot.security"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "0.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SMTP_HOST", quoteBuildConfig("smtp.hostinger.com"))
        buildConfigField("int", "SMTP_PORT", "465")
        buildConfigField("String", "SMTP_USER", quoteBuildConfig(smtpUser))
        buildConfigField("String", "SMTP_PASSWORD", quoteBuildConfig(smtpPassword))
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.3")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-video:1.3.4")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-auth:21.6.0")

    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
