#!/bin/bash
# FAXI 시연 준비: 차·폰 에뮬레이터를 찾아 시연 데이터로 되돌리고, 권한·포트를 맞춰 두 앱을 띄운다.
# 먼저 에뮬레이터 두 대를 켜 둔다: 차 = Pleos_Connect_SDK(Pleos 이미지), 폰 = Medium_Phone_API_37.0
#
#   tools/demo.sh            빌드 + 설치 + 시연 데이터로 초기화 + 실행
#   tools/demo.sh --keep     장부를 지우지 않고 설치·실행만(이어서 찍을 때)
#   tools/demo.sh --no-build 빌드 없이(이미 빌드한 APK로)
set -euo pipefail
cd "$(dirname "$0")/.."

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
CAR_AVD="Pleos_Connect_SDK"
PHONE_AVD="Medium_Phone_API_37.0"
CAR_USER=10   # Pleos 에뮬레이터는 운전자 사용자 10으로 돈다
KEEP=0; BUILD=1
for a in "$@"; do
  case "$a" in
    --keep) KEEP=1 ;;
    --no-build) BUILD=0 ;;
    *) echo "모르는 옵션: $a"; exit 1 ;;
  esac
done

serial_of() { # AVD 이름 → adb 시리얼
  for s in $("$ADB" devices | awk '/^emulator-/{print $1}'); do
    if [ "$("$ADB" -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r')" = "$1" ]; then echo "$s"; return; fi
  done
}
CAR=$(serial_of "$CAR_AVD"); PHONE=$(serial_of "$PHONE_AVD")
[ -n "$CAR" ] || { echo "차 에뮬레이터($CAR_AVD)가 꺼져 있어요."; exit 1; }
echo "차: $CAR  폰: ${PHONE:-없음}"

if [ "$BUILD" = 1 ]; then
  export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
  ./gradlew -q :app:assembleDebug :phone:assembleDebug
fi

# 차 앱
"$ADB" -s "$CAR" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
[ "$KEEP" = 1 ] || "$ADB" -s "$CAR" shell pm clear --user "$CAR_USER" com.geppetto.faxi >/dev/null
# 촬영 중에 권한 창이 뜨지 않게 미리 허용(속도 = 차에서는 "위치 정보"로 묻는다)
"$ADB" -s "$CAR" shell pm grant --user "$CAR_USER" com.geppetto.faxi android.car.permission.CAR_SPEED
# Virtual Console(차량 값 바꾸기)과 손님 한마디(폰 → 차)
"$ADB" -s "$CAR" forward tcp:8088 tcp:8088 >/dev/null
"$ADB" -s "$CAR" forward tcp:47800 tcp:47800 >/dev/null
"$ADB" -s "$CAR" shell am start --user "$CAR_USER" -n com.geppetto.faxi/.MainActivity >/dev/null

# 폰 앱(엄마로 등록돼 있으면 그대로 둔다. 처음이면 앱에서 "엄마 · MAXI로 등록")
if [ -n "$PHONE" ]; then
  "$ADB" -s "$PHONE" install -r phone/build/outputs/apk/debug/phone-debug.apk >/dev/null
  "$ADB" -s "$PHONE" shell pm grant com.geppetto.faxi.phone android.permission.CAMERA || true
  "$ADB" -s "$PHONE" shell pm grant com.geppetto.faxi.phone android.permission.ACCESS_LOCAL_NETWORK 2>/dev/null || true
  "$ADB" -s "$PHONE" shell am start -n com.geppetto.faxi.phone/.PhoneActivity >/dev/null
fi

echo
echo "준비됐어요."
echo "- 차량 값: 브라우저에서 http://127.0.0.1:8088/ (Gear Selection, ACCEL/BRAKE, 공조·시트·와이퍼·창문)"
echo "- 폰 앱이 등록 화면이면 \"엄마 · MAXI로 등록\"을 누르세요."
