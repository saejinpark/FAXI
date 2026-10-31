plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.geppetto.faxi"
    compileSdk {
        version = release(37)
    }
    // 속도(PERF_VEHICLE_SPEED)를 읽는 Android Car API. 기기에 있으면 쓰고, 폰·폴드에서는 없어도 깔린다(manifest uses-library required=false).
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.geppetto.faxi"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 심사용 release 빌드는 디버그 키로 서명한다(스토어 배포 전까지). 서명 없는 release APK는 깔리지 않는다.
            signingConfig = signingConfigs.getByName("debug")
            // 코드 줄이기(R8)는 끈다: 켜면 kotlin 클래스를 옮기다 시작하자마자 IllegalAccessError로 죽었다(Pleos 에뮬레이터에서 확인).
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(project(":core"))
    implementation(project(":design"))
    // 차량 값: 주행거리·기어는 Pleos Vehicle SDK, 속도는 Android Car API(CarPropertyManager, Pleos FAQ 권장)
    implementation(libs.pleos.vehicle)
    // 음성 안내: Pleos TextToSpeech SDK(차 안 엔진, ON_DEVICE)
    implementation(libs.pleos.tts)
    // 출발지·도착지·경유지: Pleos NaviHelper SDK(읽기만)
    implementation(libs.pleos.navi)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}