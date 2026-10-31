#!/bin/bash
# 제출용 APK 묶기: 테스트를 돌리고 차 앱·폰 앱 release APK를 만든다.
#   apk/  공모전 규칙 이름(팀명_앱이름_release.apk)으로, git에 넣는다(심사위원이 저장소에서 받는다)
#   dist/ 같은 APK를 영문 이름으로 + INSTALL.txt, GitHub Releases에 올린다(릴리스는 한글 파일 이름을 깨뜨린다)
#   tools/package.sh
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
TEAM="박세진"   # 개인 참가라 팀명 자리에 이름

./gradlew -q testDebugUnitTest assembleRelease
version=$(grep -m1 'versionName' app/build.gradle.kts | sed -E 's/.*"(.*)".*/\1/')
car=app/build/outputs/apk/release/app-release.apk
phone=phone/build/outputs/apk/release/phone-release.apk

mkdir -p apk
cp "$car" "apk/${TEAM}_FAXI_release.apk"
cp "$phone" "apk/${TEAM}_FAXI-phone_release.apk"

rm -rf dist && mkdir -p dist
cp "$car" "dist/FAXI-car-${version}.apk"
cp "$phone" "dist/FAXI-phone-${version}.apk"
cat > dist/INSTALL.txt <<TXT
FAXI 설치 방법 (저장소 README "실행 방법"과 같은 내용)
1. 차: Pleos Connect 에뮬레이터(시스템 이미지 pleos_car_emulator)를 켜고
   adb install -r FAXI-car-${version}.apk
   런처에서 FAXI 아이콘을 누른다.
2. 폰: Android 9 이상 기기나 에뮬레이터에
   adb install -r FAXI-phone-${version}.apk
   앱에서 "엄마 · MAXI로 등록"(시연 가족) → 차에서 기사 "아빠", 손님 "엄마"를 고르고 폰의 4자리를 누른다.
3. 차량 값: adb forward tcp:8088 tcp:8088 → 브라우저 http://127.0.0.1:8088/ (Virtual Console)
   기어 D와 ACCEL로 달리고, P에 넣으면 하차한다.
4. 손님 한마디·기사 후기(폰 → 차): 차 에뮬레이터에 adb forward tcp:47800 tcp:47800
TXT
ls -la apk dist
