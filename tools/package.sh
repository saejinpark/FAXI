#!/bin/bash
# 제출용 APK 묶기: 테스트를 돌리고 차 앱·폰 앱 APK를 dist/에 이름 붙여 모은다(심사위원이 adb install로 설치).
#   tools/package.sh
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

./gradlew -q testDebugUnitTest assembleDebug
version=$(grep -m1 'versionName' app/build.gradle.kts | sed -E 's/.*"(.*)".*/\1/')
stamp=$(date +%Y%m%d)
rm -rf dist && mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk "dist/FAXI-car-${version}-${stamp}.apk"
cp phone/build/outputs/apk/debug/phone-debug.apk "dist/FAXI-phone-${version}-${stamp}.apk"
cat > dist/설치.txt <<TXT
FAXI 설치 방법
1. 차: Pleos Connect 에뮬레이터(Pleos 이미지)를 켜고
   adb install -r FAXI-car-${version}-${stamp}.apk
   (차 에뮬레이터는 운전자 사용자 10으로 돈다. 런처에서 FAXI 아이콘을 누른다)
2. 폰: Android 9 이상 기기나 에뮬레이터에
   adb install -r FAXI-phone-${version}-${stamp}.apk
   앱에서 "엄마 · MAXI로 등록"(시연 가족) → 차에서 손님 "엄마"를 고르고 폰의 4자리를 누른다.
3. 차량 값: adb forward tcp:8088 tcp:8088 → 브라우저 http://127.0.0.1:8088/ (Virtual Console)
   손님 한마디(폰 → 차): adb forward tcp:47800 tcp:47800 (폰 에뮬레이터가 10.0.2.2로 보낸다)
시연 순서는 저장소의 tools/DEMO.md.
TXT
ls -la dist
