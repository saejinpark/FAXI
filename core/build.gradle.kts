// 차 앱(:app)과 폰 앱(:phone)이 같이 쓰는 순수 로직: 요금 엔진, 탑승 인증(TOTP), 초대 링크.
// 화면·안드로이드 기능에 기대지 않는다. 그래야 단위 테스트로 검증하고 두 앱에서 똑같이 돈다.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.geppetto.faxi.core"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 28
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    // QR 만들기·읽기(순수 Java, 인터넷·구글 서비스 없이 돈다)
    api(libs.zxing.core)
    testImplementation(libs.junit)
}
