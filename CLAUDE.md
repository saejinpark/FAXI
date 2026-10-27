# FAXI

FAXI(Family + Taxi)는 가족 차를 가족 택시로 바꾸는 역할놀이 앱이다. 현대자동차그룹 차량용 앱 개발 공모전(Pleos Connect) 출품작.
운전하는 가족은 기사, 옆에 탄 가족은 손님. 요금은 차량 데이터로 1원 단위까지 매기고, 앱은 장부와 확인만 맡는다(실제 결제 없음).
기획서: https://claude.ai/artifact/R8hHPvNjpL3JH8ikucYy55 (Claude Docs. 기능 판단이 필요하면 먼저 읽는다)

## 개발자
- iOS(SwiftUI) 경력, Android·Kotlin은 처음. 설명은 SwiftUI에 빗대어 주면 이해가 빠르다.
- 한국어로 대화한다.

## 기술 스택
- Kotlin + Jetpack Compose, Gradle Kotlin DSL, minSdk 28. 내비게이션·DI 라이브러리는 필요해질 때까지 넣지 않는다.
- 모듈 넷(git 저장소, 작업은 브랜치에서):
  - `:app` 차 앱(Pleos Connect), 패키지 `com.geppetto.faxi`
  - `:phone` 폰 앱(가족 모두의 폰), 패키지 `com.geppetto.faxi.phone`
  - `:core` 두 앱이 같이 쓰는 순수 로직(요금 엔진 `fare`, 탑승 인증 `auth`, QR `qr`, 손님 한마디 `message`). 화면·안드로이드 기능에 기대지 않는다.
  - `:design` 두 앱이 같이 쓰는 디자인 시스템 코드: `ui/theme`(색·글자·간격 토큰, `FaxiTheme`), `ui/components`의 `Buttons.kt`·`Icons.kt`·`Cards.kt`, 앱 아이콘, 창 바탕 테마(`Theme.Faxi.Base`). 가족 모델(`Member` 등)에 기대지 않는다. 모델이 필요한 표시(`Avatar(member)`, `gradeTone`)는 `:app`의 `MemberBadges.kt`.
- 빌드 확인: `./gradlew assembleDebug` (네 모듈 모두), 테스트: `./gradlew testDebugUnitTest`. 셸에 Java가 없으면 Android Studio JDK를 쓴다:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- 실행: Pleos Connect Emulator(AVD는 Cold boot), UI 확인은 갤럭시 폴드 실기기. 폰·폴드에는 Pleos 차량 서비스가 없어서 차량 값이 오지 않고 시연 운행(`FakeTrip`)으로 대신한다.
  - **차 에뮬레이터는 AVD `Pleos_Connect_SDK`를 쓴다**(시스템 이미지 `system-images/android-34/pleos_car_emulator`, Android 14, Pleos Vehicle Service가 들어 있다). 예전 AVD `Pleos_Connect_v2.1.0`은 이름만 Pleos이고 구글 일반 Automotive 이미지(Android 15)라 Pleos SDK가 붙지 않는다.
  - 차량 값 바꾸기(Virtual Console): `adb -s emulator-5554 forward tcp:8088 tcp:8088` → 브라우저 `http://127.0.0.1:8088/`. Gear Selection(P/R/N/D)과 ACCEL/BRAKE 페달로 주행을 흉내 낸다(속도가 올라간다). Odometer 카드는 1km 단위이고 주행 시뮬레이션에 따라 움직이지 않는다. Pleos 이미지는 user 빌드라 `cmd car_service inject-vhal-event`는 막혀 있다.
  - 차 + 폰 같이 시험: `Pleos_Connect_SDK`(차)과 `Medium_Phone_API_37.0`(폰)을 함께 켠다. 폰 앱에서 시연 가족으로 등록 → 폰의 4자리를 차에 입력.
  - 차 에뮬레이터는 사용자 10으로 돈다: 데이터 초기화는 `adb shell pm clear --user 10 com.geppetto.faxi`.

## 구조
- HODOS에서는 `data/Speaker.kt`와 매니페스트 TTS `<queries>`만 가져왔다. 디자인과 화면은 FAXI용으로 새로 만들었다.
- `MainActivity.kt` `FaxiApp`의 장부와 `AndroidSpeaker`를 `AppShell`에 넘긴다. 처음 켤 때 속도 권한(CAR_SPEED, 차에서는 "위치 정보"로 묻는다)을 받는다. 하차 안내 음성은 `store.onArrival`. `demo = true`면 운행 중 화면에 시연용 하차 버튼이 나온다.
- 차량 값: `data/VehicleFeed.kt`(FaxiApp이 켠다) → `store.onVehicle(VehicleReading)`.
  - 누적 주행거리(km)·기어: Pleos Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2`(`Odometer.registerDistanceDriven`, `Gear.registerGearSelection`, 권한 `pleos.car.permission.CAR_DRIVE`). SDK에는 속도 도메인이 없다. 공식 문서의 도메인 목록에 Gear가 빠져 있지만 SDK(AAR)에는 있다.
  - 속도(m/s): Pleos FAQ 권장대로 Android Car API `CarPropertyManager`의 `PERF_VEHICLE_SPEED`(클러스터와 같은 값). `useLibrary("android.car")` + manifest `uses-library android.car required=false`(폰·폴드에도 깔리게). 권한 `android.car.permission.CAR_SPEED`는 dangerous라 실행 중에 묻는다. API 34 이미지라 `registerCallback`(35부터 subscribePropertyEvents).
  - 옵션 요금(SDK, 칸마다 따로 등록해서 하나가 막혀도 나머지는 받는다):
    - 냉난방: HVAC 전원·에어컨은 **실내 전체 구역**(`SeatArea(1열 좌·우, 2열 좌·중·우)` = areaId 117)으로 등록해야 값이 온다(운전석만 등록하면 안 옴, 에뮬레이터 로그로 확인). 설정 온도·실내 온도는 운전석. 판정 `hvacMode`: 꺼짐 → 꺼짐, 에어컨 → 냉방, 에어컨 없이 설정 온도 > 실내 온도 → 난방(실내 온도를 모르면 난방 아님). 권한 CAR_CLIMATE.
    - 엉따: `Seat.registerHvacSeatTemperature` **조수석**(ROW_1_RIGHT, 손님 자리) 값 > 0. 음수는 통풍(요금 없음), 운전석 열선도 요금 없음. 권한 CAR_SEATS.
    - 와이퍼: `Wiper.registerWiperState` 앞유리 ON. 창문: `Window.registerWindowPosition` 1·2열 네 창 중 하나라도 > 0%. 권한 CAR_WINDOWS.
    - 옵션 값이 하나도 안 오면(폰·폴드) `CabinState.Demo`(냉방·엉따 켜짐)를 그대로 둔다. 하나라도 오면 안 온 칸은 꺼짐.
  - 콜백 스레드는 문서에 없어서 모두 메인으로 옮긴다. 차가 아니거나 서비스가 없으면 아무 값도 오지 않는다(그때는 시연 운행).
  - 운행 거리 = 주행거리계 차이와 속도 적분(앞 속도 × 지난 시간) 중 큰 쪽. 계기판은 단위가 굵고(에뮬레이터는 시뮬레이션 주행에 안 따라 움직임) 적분은 잘게 센다.
  - 하차: 운행 중에 움직였다가(기어 D·R·N 또는 1km/h 이상) P에 들어가면 자동 하차 → 도착 화면 + 음성. 움직이기 전 P는 하차가 아니다.
  - 운행 중이 아닌데 기어가 D면 `ParkFirstScreen`("주행 중이에요 · 주차하면 열려요")으로 탑승·장부·기사를 닫는다.
  - 주행 중 화면 노출(DO): MainActivity에 `<meta-data android:name="distractionOptimized" android:value="true"/>`. 없으면 Pleos가 D에서 "주행 중에는 사용할 수 없습니다"로 앱 전체를 가린다. Pleos 문서에는 DO 선언 방법이 없어 Android 표준을 썼고, 에뮬레이터 홈은 받아들였지만 CarPackageManagerService가 "not installed from permitted sources"(스토어 설치 아님) 경고를 남긴다. 실제 차·심사에서 확인 필요.
  - 테스트: `app VehicleRideTest`(적분 거리요금, 계기판 우선, 대기·급정거, 움직이기 전 P, 운행 아닐 때 D, 옵션 분 단위 요금, 옵션 값 없을 때 기본값, 공조 판정).
  - Virtual Console 카드: Gear Selection, ACCEL/BRAKE, HVAC Power, Air Conditioning, Target Temperature, Seat Temperature(Driver/Passenger 고르고 H1~H3), Windshield Wiper Switch, Window Position(창 고르고 %).
- 화면 흐름 (탭은 운행·장부·기사 셋뿐. 화면 글자는 "운행", "주행 중"은 차가 움직이는 상태를 말할 때만 쓴다):
  - 운행 탭(제목 "운행 시작") → 미터기 켜기 → 운행 중(전체 화면) → 하차 → 도착 화면(전체 화면, 영수증 공개 + 후기) → 닫으면 탑승 탭.
  - 장부 탭 → 빚 한 줄 누르기 → 두 사람 사이 빚 상세(정산 요청·확인, 둘 사이 운행, 기록, 탕감) → 운행 누르기 → 지난 영수증.
  - "지금 보이는 영수증이 어느 운행 것인지" 늘 분명해야 한다. 영수증만 따로 보여 주는 탭을 다시 만들지 않는다.
- `ui/shell/AppShell.kt` 운행 중(`store.rideActive`)이면 운행 중 화면만, 운행 중이 아닌데 기어가 Driving이면 "주차하면 열려요"만, `store.arrival`이 있으면 도착 화면만, 그 외에는 왼쪽 사이드바 + 탭 화면. 기사가 확인할 정산 요청이 있으면 장부 탭에 "확인 N건". 탭은 `rememberSaveable`, 내비게이션 라이브러리 없음, 전환 즉시.
- `ui/boarding/BoardingScreen.kt` 1 기사 → 2 손님 → 3 탑승 코드 숫자판. 손님을 고르면 밀린 돈 독촉(화면 + 음성), 기사가 그 손님에게 진 빚이 있으면 "먼저 퉁쳐요" 안내.
- `ui/driving/DrivingScreen.kt` 2×2로 화면을 꽉 채운다: 자격증 | 손님 한마디 / 오늘의 손님(캐릭터, 출발 시각, "미터기 작동 중 · 요금은 내릴 때", "아빠 택시 N번째 탑승") | 요금 붙는 옵션(냉방·엉따·와이퍼·창문 켜짐/꺼짐).
  - 금액은 운행 중 화면에 아예 없다(감춘 요금 칸도 뺐다). 하차 때 도착 화면에서 한 번에 공개한다.
  - 실시간 미터기 숫자, 흐르는 진동 파형, 경과 시간은 넣지 않는다(주행 중 움직임 금지). 볼거리는 하차 뒤 도착 화면의 정지 그래프로 보여 준다.
  - 손님 한마디 칸: 손님이 폰으로 보낸 마지막 한마디(정해진 문장 + 아이콘, `display` 한 줄)를 띄운다. 새로 올 때만 바뀌고 깜빡임·소리 없음. 온 상태는 accent 테두리로.
  - 옵션 칸은 상태가 바뀔 때만 바뀐다(차 표시등과 같은 성격). 지금은 `store.cabin` 가짜 값, Vehicle SDK 연결 때 바꾼다.
  - 시연용 하차 버튼은 오른쪽 아래 구석에 작게.
- `ui/receipt/ArrivalScreen.kt` 도착 화면과 지난 영수증 상세. 닫기/확인 버튼은 제목 줄 오른쪽(카드 바닥에 두면 화면 밖으로 밀린다).
- `ui/receipt/ReceiptViews.kt` 영수증 카드(머리 오른쪽 QR, 맨 위에 이번에 갚을 돈, 지난 영수증은 "그때 청구한 돈"), 후기 폼, `RideComfortChart`(오늘의 승차감: 운행 동안 흔들림 한 줄 + 급정거 점. `Receipt.shake`/`hardBrakes`, 지금은 가짜 데이터).
- `ui/ledger/LedgerScreen.kt` 장부 홈(확인 기다리는 정산 → 갚을 돈 목록 → 월 정산서 → 기록) + 안쪽 화면 전환 상태. `DebtDetailScreen.kt` 두 사람 사이 상세. 탕감은 한 번 더 확인받는다.
- `ui/roster/RosterScreen.kt` 기사 탭: 명단(자격증 카드, 평생 장부) → 카드 누르기 → `MemberDetailScreen.kt` 기사 상세(받을 돈/갚을 돈, 평생 장부, 최근 운행, 받은 후기, 캐릭터 고르기) → 빚 상세·영수증(장부 탭과 같은 화면 재사용).
  - 기사 상세는 차 화면이라 가족 모두가 본다. 나만 보는 정보는 폰 앱 몫이다.
  - 사진 대신 캐릭터(`Avatars`, 이모지). 폰 → 차 사진 전송 통로가 정해지면 `Member.photoFile`을 쓴다.
  - 평균 별점은 저장하지 않고 받은 후기로 계산한다(`store.driverRating`).
- 빚 방향은 화살표 대신 말로 쓴다: `owesPhrase()` → "엄마가 아빠에게". 조사는 `data/Korean.kt`의 `ga()`.
- `data/FaxiModels.kt` 가족·등급·영수증·빚·정산 요청 모델. `data/FaxiStore.kt` 화면이 보는 유일한 창구(지금은 가짜 데이터). 상태는 `mutableStateOf`라 바뀌면 화면이 다시 그려진다. 하차 때 장부 반영도 여기서 한다:
  - 밀린 돈 이월 + 연체 이자, 기사가 손님에게 진 빚과 자동 상계(영수증에 "상계" 줄), 예비 기사 손님은 청구 없이 평생 장부로.
  - 정산 확인·분쟁·탕감 기록. 두 사람 사이 빚은 한 줄만 둔다.
- 저장: `FaxiApp`(Application)이 `FaxiStore`를 하나 들고 있어서 주간/야간 전환으로 Activity가 다시 만들어져도 장부가 남는다. 값이 바뀔 때마다 `onChanged` → `FaxiStorage`가 `filesDir/faxi-ledger.json`에 통째로 저장(AtomicFile, 백그라운드 스레드). 저장 파일이 없을 때만 시연 데이터로 시작한다.
  - 저장 형식은 `data/Snapshot.kt`의 `FaxiSnapshot`(kotlinx.serialization). 필드를 추가할 땐 기본값을 줘서 예전 파일도 읽히게 한다.
  - 운행 중 손님과 닫지 않은 도착 화면도 저장해서, 앱이 꺼졌다 켜져도 이어진다.
  - 빚은 `since`(시작한 날)를 저장하고 연체 일수는 `store.daysOf(debt)`로 오늘 기준 계산한다. 일수를 숫자로 저장하지 않는다.
  - 시연 데이터를 다시 보고 싶으면 앱 데이터를 지운다: `adb shell pm clear com.geppetto.faxi`
- `core/.../fare/` 요금 엔진(`:core` 모듈). 영수증 줄 `FareLine`도 여기 있다.
  - `Tariff.kt` 요금표(기획서 값) + `VehicleSample`(시각, 누적 주행거리 m, 속도, 냉난방, 엉따, 와이퍼, 창문). 차량에서는 Vehicle SDK 값으로 채운다. 단위는 SDK 문서를 확인하고 맞춘다.
  - `FareMeter.kt` 탑승 순간 값으로 켜고 `update(sample)`로 쌓는다. `lines()`가 영수증 줄.
    - 구간(앞 값 → 뒤 값)은 앞 값의 상태로 센다: 옵션 시간, 정차, 할증 모두.
    - 정차 = 1km/h 미만, 급정거 = 감속 3m/s² 이상(한 번 밟는 동안 한 번), 할증은 그 순간 조건(기본요금은 탑승 시각), 남는 거리·초·분은 버림.
    - 거리·대기 100원은 누적값에서 매번 다시 세어 실수 오차가 쌓이지 않게 한다.
  - `FakeTrip.kt` `TripBuilder`(1초 간격 가짜 차량 값)와 `planExample`(기획서 예시 운행). 차량 값이 없을 때(폰·폴드, 운행 중 앱 재시작) 하차 시연은 이 운행을 엔진에 돌려 영수증을 만든다.
  - 급정거는 감속 3m/s² 이상이 되는 순간 한 번 세고, 다시 속도를 올리거나 멈출 때까지는 같은 한 번이다(차에서는 속도·주행거리 값이 따로 와서 같은 속도 구간이 감속 중간에 낀다).
- `core/src/test/.../FareMeterTest.kt` 기획서 영수증 예시(15,250원)를 줄 단위로, 거리·대기·심야·우천·급정거·옵션·경유지 규칙을 검사한다. 요금표를 바꾸면 꼭 돌린다.
- `core/.../auth/` 탑승 인증.
  - `Totp` RFC 6238(HMAC-SHA1, 30초, 4자리), 앞뒤 30초까지 통과. `Base32`(엄격하게 디코드). 테스트는 RFC 예시 값으로 검증한다.
  - `FaxiInvite` 초대 링크 `faxi://join?f=&m=&n=&c=&s=`(차 화면 QR에 담을 내용). 비밀 열쇠가 차 → 폰으로 가는 유일한 길.
  - `DemoFamily` 시연 가족 고정 열쇠. 차 앱 시연 데이터와 폰 앱 "시연 가족으로 등록"이 같은 열쇠를 써서 QR 없이도 인증을 시연한다. 누구나 아는 열쇠라 실제 가족에게는 쓰지 않는다.
- 차 앱 인증: `Member.secret`(Base32)을 장부에 저장, `store.verifyBoardingCode(손님, 코드)`. 열쇠가 없는 예전 장부는 불러올 때 채운다.
- `core/.../qr/` QR(zxing, 인터넷·구글 서비스 없이).
  - `QrCodec` 만들기(차 화면)·읽기(폰 카메라 밝기 값). 기본 오류 정정 M.
  - `ReceiptTicket` 영수증 QR 내용: 영수증 + 그 순간 손님의 받을 돈·갚을 돈. `faxi://receipt?d=`(이름표 없는 배열 JSON → Deflate → Base64). 영수증 줄은 `FareCodes` 기호로 줄여 싣는다(대기요금/신호 7회 → W/7).
  - **QR 크기 규칙(실측)**: 77칸짜리는 차 스크린샷에서 주변에 따라 못 읽었다. 영수증 QR은 69칸(버전 13) 이하를 `QrTest`가 지킨다. 필드를 늘리면 이 테스트가 먼저 깨진다. 새 값은 배열 끝에만 붙인다(자리를 바꾸면 예전 QR을 못 읽는다).
  - 차 화면에서는 1dp ≈ 1px 안팎이라 작은 QR은 못 읽는다. 영수증 QR은 카드 안에 작게, 눌러서 화면 높이만큼 크게 띄워 찍는다. 초대 QR은 240dp.
- 차 앱 QR: `ui/components/QrImage.kt`(밝은 종이 + 4칸 여백, 야간에도 밝게). 기사 상세 "폰 연결" 카드의 초대 QR은 눌러야 보이고(비밀 열쇠), 화면을 떠나면 숨는다. 디버그 빌드는 도착 때 영수증 링크를 `FaxiReceipt` 로그로 남긴다(에뮬레이터 폰으로 시험용, 비밀 없음).
- 손님 한마디(폰 → 차, 같은 Wi-Fi). 서버 없이 차 안에서 끝난다.
  - `core/.../message/RideMessage.kt` 정해진 문장 6개(`RideMessage`, 자유 글 없음: 운전자가 힐끗 보고 끝나게), 한 줄 형식 `MessageEnvelope`(`FAXI1|가족|사람|코드|보낸시각|서명`, 서명 = 그 사람 TOTP 열쇠로 HMAC-SHA256 앞 16바이트), 답 `MessageReply`, 포트·NSD 이름 `MessageWire`. 문장은 끝에만 붙인다(코드가 차·폰 약속).
  - 차: `data/MessageServer.kt`(FaxiApp이 켠다) TCP 47800 + NSD `_faxi._tcp`("FAXI-가족"). 받은 줄은 메인 스레드에서 `store.receiveMessage`가 확인: 우리 가족·그 사람 서명·±60초, 지금 손님만(NotRiding), 같은 통 두 번 금지, 한 사람 10초에 한 번(TooSoon). `store.passengerMessage`는 저장하지 않고 운행이 바뀌면 지운다.
  - 폰: `MessageSender.kt` NSD로 차를 찾고(3초), 디버그 빌드는 못 찾으면 10.0.2.2로. 홈의 "기사님께 한마디" 카드(2열 타일, 결과 줄은 자리를 미리 잡음).
  - Android 17(API 37)은 같은 Wi-Fi 기기와 통신에 `ACCESS_LOCAL_NETWORK`(근처 기기) 런타임 권한이 필요하다. 폰은 처음 보낼 때 묻는다. 차 앱도 선언만 해 두었다(지금 차 에뮬레이터는 API 35라 필요 없음, 차가 37이 되면 요청 흐름을 넣는다).
  - Pleos 문서(시작하기·개발 가이드·FAQ)에는 앱의 Wi-Fi/소켓 제한이 적혀 있지 않다. 실제 차에서 핫스팟으로 되는지는 아직 확인 못 했다.
  - 에뮬레이터 두 대로 시험: `adb -s emulator-5554 forward tcp:47800 tcp:47800`(차) → 폰 디버그 빌드가 10.0.2.2:47800으로 보낸다. (에뮬레이터 둘 사이에서 NSD 검색도 되는 것을 확인했다.) 테스트: `core RideMessageTest`, `app RideMessageStoreTest`.
- `phone/` 폰 앱: 등록(초대 QR 찍기, 링크 붙여넣기, 시연 가족) → 탑승 코드(30초마다, 남은 시간 막대) + 기사님께 한마디 + 영수증 QR 찍기 + 내 정산 현황(마지막 영수증 기준 사본) + 내 영수증(눌러서 항목 펼치기).
  - QR 찍기: `QrScanner.kt`(CameraX 미리보기 + :core QrCodec). `faxi://` 링크는 딥 링크로도 열린다(같은 `handleLink`). 초대는 확인 창을 거쳐 등록, 영수증은 내 가족·내 것만 저장.
  - 등록 정보와 영수증은 SharedPreferences(백업 끔). 폰은 차 장부를 바꾸지 못한다.
  - 에뮬레이터 카메라로는 차 화면을 못 찍는다. 시험은 딥 링크로: `adb shell "am start -a android.intent.action.VIEW -d '<링크>' com.geppetto.faxi.phone"`
  - 폰은 손님 화면이라 차 앱의 주행 중 움직임 금지 규칙을 적용하지 않는다. 색·글자·버튼·카드는 차 앱과 같은 `:design` 것을 쓴다(Material 기본 글자 크기를 쓰지 않는다). 폰 화면 가장자리는 `space6`, 좁으니 금액 줄은 항목 쪽이 줄을 바꾼다.
- `app/src/test/.../FaxiStoreTest.kt` 하차·상계·평생 장부·정산·탕감 단위 테스트. 장부 규칙을 바꾸면 `./gradlew testDebugUnitTest`로 확인한다.
- `data/Speaker.kt` 음성 창구. `Speaker.create`: 차면 `PleosSpeaker`, 폰·폴드는 `AndroidSpeaker`(기기 TTS).
  - `PleosSpeaker`: Pleos TextToSpeech SDK `ai.pleos.playground:TextToSpeech:3.3.1`(권한 `pleos.car.permission.TTS_SERVICE`), **ON_DEVICE 모드**(차 안 엔진: 네트워크·앱 등록 없음, 이름·금액이 차 밖으로 안 나감). 새 말은 하던 말을 멈추고 읽는다.
  - 로캘은 `Locale.KOREA`(ko-KR). `Locale.KOREAN`은 엔진에 "ko-"로 가서 unsupported locale로 실패한다(에뮬레이터 로그로 확인).
  - 초기화 실패나 엔진·언어 미지원 오류면 기기 TTS로 바꾸고 방금 문장을 다시 읽는다. SDK AAR이 INTERNET 권한을 같이 선언한다(ON_DEVICE라 쓰지 않음).
- `ui/theme/`(`:design` 모듈) 디자인 토큰. 색은 `Faxi.colors.*`, 글자는 `Faxi.type.*`, 간격·크기는 `FaxiDimens.*`로만 쓴다. 하드코딩한 색·크기를 새로 만들지 않는다. 테마 진입점은 `FaxiTheme`(야간 기본, dynamic color 안 씀).
  - 색: FAXI 디자인 시스템(https://claude.ai/artifact/13q7cCn7wsigv23nJYGwV2)의 tokens.json과 `ui/theme/Color.kt`는 같은 값이다. 한쪽을 고치면 다른 쪽도 고친다. 폰 앱도 같은 `FaxiTheme`을 쓰므로 값은 한 곳뿐이다.
  - 무드는 산호 `accent`(버튼 면) + 밤 남색 `surface`. `accent`는 면 색이라 글자로 쓰지 않는다. 글자 강조는 `accentInk`. 글자 색은 bg·surface·surfaceRaised 위에서 모두 4.5:1 이상.
  - `danger`는 산호와 밝기가 비슷하다. 늘 단어나 경고 아이콘과 함께.
  - 가족 색 `familyPaxi/Maxi/Kixi/Naxi`(`Faxi.colors.family(호출명)`): 그 사람을 가리키는 표시(자격증 왼쪽 띠, 아바타 테두리)에만. 버튼·배경에는 쓰지 않는다.
  - QR은 `qrPaper` 위 `qrInk`(테마와 상관없이 밝게).
  - 금액은 `Faxi.type.amount` / `amountLarge`(숫자 폭 고정 tnum).
  - 모든 글자 스타일은 `localeList = ko-KR` + 어절 단위 줄바꿈(`WordBreak.Phrase`). 기기 언어가 영어인 에뮬레이터에서도 "차 화/면"처럼 글자 중간에서 끊지 않는다. 새 스타일을 만들 때도 `Faxi.type`에서 copy한다.
- `ui/components/Layout.kt` `FaxiPage`(제목 줄 + 본문). 본문은 `row(a, b)` / `full(a)`로 줄 단위로 쌓는다. 칸(Cell)은 받은 Modifier를 카드 맨 바깥에 붙이고, 카드 안 버튼 앞에는 `Spacer(Modifier.weight(1f))`를 둬서 짝꿍 카드와 버튼 줄을 맞춘다.
- `ui/components/Buttons.kt` `FaxiPrimaryButton`(화면당 하나), `FaxiSecondaryButton`, `FaxiIconButton`, `SelectTile`(고르면 accent 굵은 테두리 + 체크).
- `ui/components/Cards.kt` `FaxiCard`, `SectionTitle`, `StatusTag`(단어로 상태 표시), `Avatar`/`AvatarCircle`(캐릭터 또는 호출명 첫 글자), `MoneyRow`, `BigWon`.
- `ui/components/LicenseCard.kt` `DriverLicenseCard`: 신분증 모양(맨 위 "FAXI 기사 자격증", 왼쪽 가족 색 띠, 3:4 사진 칸, 성명·등록·운행 칸). 위에 가득 찬 띠나 아래 통계 줄을 두면 티켓처럼 보이니 피한다. `large`(운행 중 화면)는 칸 글자 24sp 이상.
- `ui/components/Icons.kt` 아이콘 라이브러리 없이 Canvas로 28×28 격자, 선 3단위. 모두 정지 그림.
- `ui/components/Previews.kt` `@FaxiPreviews`(차량 1920dp + 폴드 900dp)와 `PreviewFrame`. 화면 파일마다 아래에 둔다.
- 화면에 나중에 생기는 줄(안내 문구 등)이 버튼을 밀어내지 않게 자리를 미리 잡아 둔다. 버튼이 손가락 밑에서 움직이면 안 된다.

## 아직 가짜인 것
- 폰 카메라로 차 화면 QR 찍기: 코드는 있지만 실기기로 찍어 보지 못했다(에뮬레이터는 차 화면 스크린샷 해독과 딥 링크로만 확인).
- 차량 값: 주행거리·속도·기어·냉난방·엉따·와이퍼·창문은 진짜(Pleos 에뮬레이터 Virtual Console로 확인). 승차감 그래프 모양은 가짜(급정거 점 개수만 진짜). 출발지·도착지("우리집 → 이마트 성수점")도 가짜(NaviHelper 전).
- 운행 중 요금 엔진은 저장하지 않는다. 운행 중에 앱이 다시 켜지면 그 운행은 시연 운행으로 계산된다.
- 손님 한마디: 에뮬레이터 두 대(adb forward)로만 주고받아 봤다. 실제 차 핫스팟·실기기 폰에서는 아직이다.

## 앱 아이콘
- 아이콘 전체(산호색 #F2845F)가 거대한 F다. 남색(#1F2B42) 두 칸은 F에 파낸 빈 곳이고 오른쪽 끝에서 잘린다.
  - 위 칸 = 택시 지붕 표시등, 안에 A·X·I. 첫 글자 자리는 비어 있다(빠진 F는 아이콘 자체).
  - 아래 칸 = 차 지붕. 위 칸보다 왼쪽으로 한 계단 나와 F 기둥이 계단처럼 보인다.
  - 모서리는 둥글게, 글자는 둥근 끝 선으로(시스템 글꼴 아님). 산호 글자/남색 대비 5.56:1.
- `design/src/main/res/drawable/ic_launcher_foreground.xml`(남색 칸 + 글자), `ic_launcher_background.xml`(산호 = F), `ic_launcher_monochrome.xml`(테마 아이콘: F만 남기고 칸은 뚫음), `mipmap-anydpi/ic_launcher*.xml`. `:design`에 한 벌만 있고 차 앱·폰 앱이 같이 쓴다.
- 108dp 격자 그대로 그린다. 보이는 영역(가운데 72dp, 18~90) 안에서 F 기둥·윗막대·가운데 막대가 읽히게 놓았다. 위치를 바꾸면 원 모양 마스크(차 런처)로 꼭 확인한다.
- 앱 안(사이드바 맨 위, 자격증·영수증 머리)에서는 아이콘 그림이 아니라 글자 "FAXI"(`accentInk`)를 쓴다. 아이콘은 런처용이다.
- 아이콘을 바꾼 뒤 차 런처에 옛 아이콘이 보이면 캐시다: `adb shell am force-stop com.android.car.carlauncher`.
- minSdk 28이라 적응형 아이콘만 쓴다. 예전 비트맵 아이콘은 없다.

## Pleos 디자인 가이드 (반드시 지킨다)
- **명암 대비 4.5:1 이상**: 글자와 배경 모두. 새 색 조합은 야간·주간 테마 둘 다 확인한다. 상태는 색만으로 전달하지 않고 아이콘이나 단어를 함께 쓴다.
- **터치 타깃 76dp 이상**(`FaxiDimens.targetMin`), 버튼 높이 88dp(`controlHeight`), 타깃 간격 16dp 이상.
- **주행 중 애니메이션 금지**: `animate*`, `AnimatedVisibility`, `Crossfade`, 자동 스크롤, 움직이는 숫자(미터기 카운트업 등) 모두 쓰지 않는다. 화면 전환도 즉시 바꾼다.
- **2분할 레이아웃 680dp 기준**: 콘텐츠 폭 680dp 이상이면 2열(`TwoColumnMin`), 미만이면 1열. 같은 줄의 두 칸은 높이를 맞추고(IntrinsicSize.Min), 페이지 전체가 한 번에 스크롤된다. 3열은 쓰지 않는다.
- 최소 글자 18sp, 주행 중 읽을 정보는 24sp 이상. 문단 3줄 이하.
- 한 화면에 accent(주 버튼)는 하나. 둘이 필요하면 하나는 보조 버튼.

## FAXI 제품 규칙
- 운전자는 주행 중 아무것도 만지지 않는다. 탑승·영수증·후기·정산은 주차(P) 상태에서만 연다.
- 주행 중 차 화면은 고정된 기사 자격증 카드, 손님 한마디, "미터기 작동 중" 한 줄, 옵션 상태만. 금액은 보이지 않고 하차 때 한 번에 공개한다.
- 손님 한마디는 정해진 문장만(자유 글 금지), 차는 읽어 주지 않는다(주행 중 말하지 않기).
- 차는 주행 중 말하지 않는다. 말하는 때: 탑승 인사, 하차 요금 안내, 독촉, (주차 중) 후기 낭독.
- 금액은 1원 단위, 반올림 없음, "약" 붙이지 않음. 연체 이자는 하루 0.1%, 원금을 넘지 않는다.
- 미터기는 다른 가족의 폰 인증(TOTP 4자리)이 있어야 켜진다. 서버 없이 차가 장부의 주인이고 가족 데이터는 차 밖으로 나가지 않는다.
- 면허증 사진·면허번호는 받지 않는다. 정식 기사 등록은 자기 선언 하나. 카드는 실제 면허증을 흉내 내지 않는다.
- 용어는 "운임"보다 "가족 장부", "정산".
- 계기판(클러스터)은 안전 정보 영역이라 건드리지 않는다. 시도하는 코드도 넣지 않는다.

## 사용할 Pleos SDK (모두 읽기 전용)
- Vehicle SDK: Odometer(거리요금)·Gear(하차·주행 판별)·HVAC(냉난방)·Seat(엉따)·Wiper(우천 할증)·Window(환기) 연결됨. 속도는 SDK에 없어 Android Car API. Brake 페달은 아직(급정거는 속도 변화로 센다)
- TextToSpeech SDK: 연결됨(ON_DEVICE). 탑승 인사("손님, 어디로 모실까요?"), 하차 요금 안내, 독촉. 후기 낭독은 아직
- NaviHelper SDK: 목적지·경유지(경유지 추가 요금)
- SDK 문서: https://document.pleos.ai/docs/pleos-only/getting-started
- 저장소: maven("https://nexus-playground.pleos.ai/repository/maven-releases/") (공식 문서 "Vehicle SDK 가져오기", settings.gradle.kts에 등록됨). SDK 실제 API는 문서보다 AAR(javap)이 정확하다.
- Pleos SDK는 새 플랫폼이라 API를 추측하지 말고, 공식 문서를 확인하거나 개발자에게 문서를 요청한다.
