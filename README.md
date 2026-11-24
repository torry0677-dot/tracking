# 우성트래킹 (WoosungTracking)

산행을 GPS로 기록하고, 네이버 지도에 경로를 그리고, 사진을 경로 위에 남기고, 링크로 공유하는 안드로이드 앱입니다.

## 1차 버전 기능

- **산행 기록**: 거리, 시간(일시정지 제외), 현재 고도, 속도, 오른 높이(누적 상승), 최고·최저 고도
- 화면을 꺼도 계속 기록됩니다 (알림창에 진행 상황 표시)
- **네이버 지도**: 지형도 + 등산로 레이어 위에 실시간 경로, 출발/도착 표시
- **사진**: 기록 중 ‘사진’ 버튼으로 찍으면 찍은 위치에 핀이 꽂힙니다. 핀을 누르면 사진이 보입니다.
- **산행 상세**: 경로 지도, 통계, 고도 그래프, 사진 모음, 이름 바꾸기, 삭제
- **링크로 공유**: 카카오톡·문자 등으로 링크를 보내면, 받는 사람은 앱 없이 웹에서 경로와 기록을 볼 수 있습니다.
  - 경로와 요약이 링크 안에 압축되어 들어가므로 서버가 필요 없습니다. 사진은 링크에 포함되지 않습니다.

기술: Kotlin, Jetpack Compose, Room, Google Play 위치 서비스, 네이버 지도 Android SDK 3.24

## 네이버 지도 키 발급 (필수)

1. https://console.ncloud.com 에 로그인 (네이버 클라우드 플랫폼 가입, 결제수단 등록 필요. 지도 표시는 무료 사용량 안에서 쓸 수 있습니다)
2. **Services > Application Services > Maps > Application** 에서 **Application 등록**
3. API 선택에서 **Dynamic Map** 을 꼭 체크 (빠지면 429 오류)
4. 서비스 환경 등록
   - **Android 앱 패키지 이름**: `com.woosungtracking`
   - (공유 페이지에도 네이버 지도를 쓰려면) **Web 서비스 URL**: `https://torry0677-dot.github.io`
5. 등록한 Application 을 눌러 **Client ID** 를 복사

키는 저장소에 올리지 않습니다. 아래 두 곳 중 쓰는 곳에만 넣으세요.

- **GitHub Actions로 APK 만들 때**: 저장소 **Settings > Secrets and variables > Actions > New repository secret**, 이름 `NAVER_CLIENT_ID`, 값에 Client ID
- **내 컴퓨터(Android Studio)에서 빌드할 때**: `local.properties.example` 을 `local.properties` 로 복사하고 `NAVER_CLIENT_ID=` 뒤에 Client ID 입력

## 휴대폰에 설치하기

### 방법 A: 컴퓨터 없이 (GitHub Actions)
1. 위 방법으로 `NAVER_CLIENT_ID` 시크릿 등록
2. 저장소 **Actions > Android APK > Run workflow** 실행 (또는 코드가 올라가면 자동 실행)
3. 끝난 실행을 열고 아래 **Artifacts** 의 `woosungtracking-debug-apk` 를 휴대폰에서 받기 (zip 안에 apk)
4. apk 를 열어 설치 (처음엔 ‘출처를 알 수 없는 앱 설치 허용’을 켜야 합니다)

> 테스트용(debug) 빌드라 새 버전을 설치할 때 “앱이 설치되지 않았습니다”가 나오면 기존 앱을 지우고 설치하세요.

### 방법 B: Android Studio
프로젝트를 열고 `local.properties` 에 키를 넣은 뒤, 휴대폰을 USB로 연결(개발자 옵션 > USB 디버깅)하고 ▶ Run.

## 공유 링크 페이지 켜기 (GitHub Pages)

`docs/` 폴더가 공유 링크를 여는 웹 페이지입니다. main 에 병합한 뒤 한 번만 설정하면 됩니다.

- 저장소 **Settings > Pages > Build and deployment**: Source `Deploy from a branch`, Branch `main` / `/docs` 저장
- 주소는 `https://torry0677-dot.github.io/tracking/` 이 됩니다 (앱의 기본 공유 주소)
- `docs/config.js` 의 `naverKeyId` 에 Client ID 를 넣으면 네이버 지도로, 비워 두면 OpenStreetMap 으로 표시됩니다.

## 다음 단계 (2차)
AI 코스 추천, 자동 산행 일지, 안전 알림
