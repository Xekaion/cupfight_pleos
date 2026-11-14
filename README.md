# 컵파이트

## 앱 개요

정차 중 만난 두 차량이 함께 달리는 구간에서 가상 물잔의 물 잔량을 겨루고, 완주 보상으로 차량 아바타를 꾸미는 Pleos Connect용 프로토타입입니다.

현재 대결은 한 기기에서 두 차량의 모의 입력을 재생합니다. 실제 이용자 간 매칭, 공통 도로 구간 판별, 서버 동기화와 장착 정보 공유는 후속 개발 범위입니다.

## 실행 파일

| 파일 | 용도 |
| --- | --- |
| [release APK](apk/컵스_컵파이트_release.apk) | 서명된 배포 빌드. 로드뷰, 상점, 차고와 미리보기를 확인할 수 있습니다. 디버그 전용 시뮬레이션 메뉴는 표시하지 않습니다. |
| [debug APK](apk/컵스_컵파이트_debug.apk) | 일반 Android에서 전체 모의 대결 흐름을 확인하는 시연 빌드입니다. Pleos 차량 모드에서는 차량 신호 시험 화면을 표시합니다. |

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
& $adb install -r 'apk/컵스_컵파이트_debug.apk'
& $adb shell am start -n io.autohmg.cupfight/.MainActivity
```

두 APK는 서명키가 다릅니다. 서로 바꿔 설치할 때 서명 충돌이 발생하면 별도의 테스트 기기를 사용해 기존 기록을 보존할 수 있습니다.

## 모의 대결 시연

일반 Android에서 debug APK를 실행합니다.

1. **시뮬레이션 → 주행부터 대결까지**를 선택합니다. 두 차량이 달리다 정차하면 매칭 화면이 나타납니다.
2. 상대의 수락을 확인한 뒤 **수락**을 누릅니다. 내 물잔 하나가 전체 화면에 표시되고, 모의 가감속과 코너링에 따라 물이 움직입니다.
3. 공통 구간 종료 후 모의 정차 상태가 되면 남은 물, 승패와 세이프 브레이크 적용 횟수를 확인합니다. 완주 기록을 저장하면 물방울 30개를 받습니다.
4. **상점**에서 장식을 구매하고 **차고**에서 장착합니다. 24종의 도장·부품·움직이는 장식을 제공합니다.

**시뮬레이션 → 두 차량 정차**에서는 제안과 양측 수락을 직접 시험할 수 있습니다. 매칭 화면의 **시뮬레이션**에서 안전 제동, 일반 급제동, 경로 분기 시나리오를 선택합니다. **물잔 미리보기**와 **다시 보기**는 보상과 기록에 영향을 주지 않습니다.

대결 종료 조건은 공통 구간 종료 또는 경로 이탈입니다. 신호대기는 종료 조건이 아닙니다. 시연 입력의 재생 시간은 실제 서비스의 대결 제한 시간이 아닙니다.

## Pleos Connect 테스트 환경

| 항목 | 환경 |
| --- | --- |
| 공식 이미지 | Pleos Connect 2.1.0, Android API 34, x86_64 |
| 공식 에뮬레이터 화면 | 2560×1440, density 160 |
| 일반 Android 시연 | Android API 34, 1280×800, density 160 |
| 현재 앱 버전 | 0.9.4, versionCode 15 |

- [공식 에뮬레이터 설치 안내](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Virtual Console 설정 안내](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)

공식 이미지를 설치하고 SDK 약관에 동의한 환경에서 APK를 설치합니다. Virtual Console 연결은 다음과 같습니다.

```powershell
adb forward tcp:8088 tcp:8088
```

브라우저에서 `http://127.0.0.1:8088/`을 열어 콘솔 세션을 유지하고 차량 입력을 변경합니다. 앱의 **차속 접근 허용**에서 권한을 허용해야 합니다.

차량 모드는 P단을 필수 조건으로 두지 않습니다. 차속 절댓값 0.01m/s 이하가 1초 동안 연속 수신되고 차량 OS가 화면 사용을 허용할 때 조작합니다. 출발하거나 차속 신호가 0.5초 넘게 갱신되지 않으면 열린 창과 화면을 제한합니다. OS가 D단 정차 중에도 사용을 제한하면 해당 제한을 따릅니다.

최신 빌드의 공식 Pleos 환경에서 D단 정차 조작과 주행 재개 시 화면 전환은 검증이 남아 있습니다. 전방충돌 경고의 실제 수신도 미검증입니다. 경고 기반 감점 보호는 모의 입력으로 확인했으며, 실제 적용에는 전용 경고 속성과 승인된 ADAS 권한이 필요합니다. ABS나 일반 경고를 전방충돌 경고로 대체하지 않습니다.

## 소스 빌드

JDK 17과 Android SDK Platform 34가 필요합니다. Android Studio에서 프로젝트를 열어 SDK 경로를 설정하거나 `ANDROID_HOME`을 지정합니다. 로컬 SDK 경로 파일은 저장소에 포함하지 않습니다.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Gradle 8.14.3과 Android Gradle Plugin 8.13.2를 사용합니다. minSdk는 28이며 compileSdk와 targetSdk는 34입니다.

release 빌드는 다음 환경변수로 본인의 서명키를 지정한 뒤 실행합니다. 비밀번호와 키 파일은 Git에 올리지 않습니다.

- `CUPFIGHT_RELEASE_STORE_FILE`: 키 저장소 경로
- `CUPFIGHT_RELEASE_STORE_PASSWORD`: 키 저장소 비밀번호
- `CUPFIGHT_RELEASE_KEY_ALIAS`: 서명키 별칭
- `CUPFIGHT_RELEASE_KEY_PASSWORD`: 서명키 비밀번호

```powershell
.\gradlew.bat :app:assembleRelease :app:lintRelease
```

키 환경변수가 없으면 release 산출물은 서명되지 않습니다. `apk/`의 release 파일은 개발자 서명키로 서명한 APK입니다.

## 구현과 검증 자료

- [유튜브용 일반 시연 영상](docs/demo-0.9.4/cupfight-youtube-demo-0.9.4.mp4)과 [세로 Shorts 영상](docs/demo-0.9.4/cupfight-shorts-0.9.4.mp4)을 제공합니다. 두 편 모두 0.9.4의 같은 모의 대결 화면을 편집한 41.5초 영상이며, 편집용 배경음을 추가했습니다.
- [0.9.4 매칭·대결 시연 영상](docs/demo-0.9.4/cupfight-demo-0.9.4.mp4): 일반 Android 에뮬레이터에서 현재 debug APK를 직접 조작해 녹화했습니다. 주행·정차, 전체 화면 매칭, 양측 수락, 물잔 경기, 결과·보상과 상점 미리보기를 담았습니다. 상대 차량과 주행 데이터는 모의 입력이며 실제 이용자 간 연결이나 공식 Pleos 시연이 아닙니다. [화면 및 촬영 정보](docs/demo-0.9.4/README.md)를 함께 제공합니다.
- [현재 로드뷰 실행 화면](docs/verification-0.9.4/traffic-grounded.png)
- [로드뷰 접지·방향 검사](docs/verification-0.9.4/RoadContactSmoke.java): 확대 범위, 차선과 거리별 400개 조건을 확인했습니다.
- [정차 판별 검사](docs/verification-0.9.2/VehicleStopGateSmoke.java): 신호 유효성, 전진·후진, 정차 확인 및 신호대기 중 대결 유지를 확인했습니다.
- [물 표현 영상](docs/verification-0.9.1/water-60fps.mp4): 0.9.1의 물리·렌더러로 각각 출력한 300프레임을 담은 5초 영상입니다. 실시간 화면 녹화나 고정 60fps 측정 결과가 아닙니다.
- [표시 성능 측정](docs/verification-0.9.1/frame-timing.json): 0.9.1 일반 Android 개발 환경의 측정입니다. 최신 공식 Pleos 성능으로 간주하지 않습니다.

물 잔량 채점은 가속도 변화에 따른 규칙 계산입니다. 물결·물줄기·물방울은 3D 형상과 간이 물리 모형으로 표현하며, 평균 수위는 채점 기록을 따릅니다. AI 운전 판정이나 실제 물 손실량 측정 기능은 아닙니다.

## 에셋과 모델

차량 모델의 편집용 GLB는 `artwork/cupfight_car.glb`에, 앱용 OBJ와 MTL은 `app/src/main/assets/`에 있습니다. [모델 생성 스크립트](tools/generate_cupfight_car.py)로 갱신할 수 있습니다.

일부 UI 아이콘·배경·상점 이미지는 이미지 생성 도구로 제작했습니다. 생성 기록은 [UI 에셋 문서](docs/ui-art-0.7.md)와 [꾸미기 에셋 문서](docs/cosmetics-art-0.8.md)에 있습니다. 현재 물잔과 물의 움직임은 코드로 그립니다.
