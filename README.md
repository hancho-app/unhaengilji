# 운행일지

차가 계기판 적산거리를 직접 읽어 국세청 서식 「업무용승용차 운행기록부」의 운행 내역을 자동으로 채우는 앱입니다. 운전자는 정차 중 버튼 한 번으로 출·퇴근용, 일반 업무용, 개인 중 하나를 고릅니다.

Pleos Connect(Android Automotive OS, API 34)용 차량 앱이며 외부 서버 없이 차 안에서만 동작합니다.

## APK 위치

`apk/윤성은_운행일지_release.apk`

## 시연 영상

`video/윤성은_운행일지_시연.mp4` (에뮬레이터 화면 녹화, 소리 없이 자막으로 설명)

## 실행 방법

### 준비

- Android Studio 와 Pleos Connect 에뮬레이터 v2.1 (API 34, 2560×1440)
- JDK 17 (소스에서 빌드할 때)

### APK 설치

에뮬레이터를 켠 뒤 저장소 루트에서 실행합니다.

```bash
adb install -r -g apk/윤성은_운행일지_release.apk
```

앱 목록에서 「운행일지」를 엽니다. 처음 열면 수집 동의 화면이 나오고, 「동의하고 시작」을 누르면 설정 화면으로 갑니다. 차종과 자동차등록번호를 적고 저장합니다. 연 관련비용과 사용자 등록은 선택입니다.

### 소스에서 빌드

```bash
./gradlew :app:testDebugUnitTest   # 단위 시험
./gradlew :app:assembleDebug       # 앱
./gradlew :app:assembleRelease     # keystore.properties 가 없으면 debug 키로 서명
```

## 테스트 환경·설정

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect 에뮬레이터 v2.1, API 34, 2560×1440 |
| SDK | `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:NaviHelper:3.0.5` |
| 빌드 | compileSdk 35, minSdk 28, Kotlin 2.0.0, AGP 8.8.2, Gradle 8.10.2 |
| 권한 | `CAR_DRIVE`, `CAR_INFO`, `CAR_SEATS`, `NAVI_ROUTE` |

### 운행 한 건 재현

에뮬레이터 가상 콘솔에서 기어와 적산거리를 바꿉니다.

1. 기어를 Drive 로 바꿉니다. 출발 때의 적산거리가 「주행 전 계기판의 거리」가 됩니다. 주행 중에는 플랫폼이 앱 화면을 막습니다.
2. 적산거리를 늘립니다.
3. 기어를 Park 로 바꿉니다. 앱이 화면에 있으면 정차 화면이 바로 열립니다.
4. 용도 버튼을 누르면 운행기록부 표에 한 행이 들어갑니다.

### 에뮬레이터에서 확인되지 않은 것

- 내비가 화면 앞에 있으면 정차 화면이 뜨지 않습니다. 이때는 앱을 열면 미분류 운행부터 표시합니다.
- 에뮬레이터의 내비 지도가 표시되지 않아 목적지·즐겨찾기 대조는 실행해 보지 못했습니다.
- 차대번호는 에뮬레이터에서 받지 못해 설정 화면에 「차에서 받지 못함」으로 표시됩니다.

## 오픈소스

| 이름 | 쓰는 곳 | 라이선스 |
|---|---|---|
| ZXing core 3.5.3 | QR 코드 만들기 | Apache-2.0 |
| Pretendard | 시연 영상과 소개 자료 그림의 글꼴 | SIL OFL 1.1 |
