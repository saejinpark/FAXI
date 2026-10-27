// FAXI 디자인 시스템(https://claude.ai/artifact/13q7cCn7wsigv23nJYGwV2)의 코드 쪽: 색·글자·간격 토큰, 버튼, 선 아이콘, 앱 아이콘.
// 차 앱(:app)과 폰 앱(:phone)이 같이 쓴다. 장부·가족 모델에는 기대지 않는다(그건 :app 몫).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.geppetto.faxi.design"
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
}
