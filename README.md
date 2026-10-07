# Books Piko

Google Play 북(`com.google.android.apps.books`)을 위한 [Morphe](https://github.com/MorpheApp) 패치 번들입니다.
[Piko](https://github.com/crimera/piko)(Twitter/X·Instagram)와 같은 구조로 만들었습니다.

- **루팅 없이** 재서명한 Play 북에서 Google 로그인, Books OAuth, 라이브러리 사용 (ReVanced GmsCore 경유)
- 책 본문(EPUB 리플로우)에 **사용자 TTF/OTF 글꼴 적용**

고정된 smali 줄 번호는 쓰지 않습니다. 모든 패치 지점은 문자열·시그니처·opcode fingerprint로 찾고,
치환할 리터럴의 개수와 사용처(예: `Account.<init>`의 type 인자)를 확인한 뒤에만 바꿉니다.
Play 북이 업데이트돼 코드 의미가 바뀌면 엉뚱한 곳을 고치지 않고 패치가 실패합니다.

## 패치 목록

| 패치 | 기본값 | 설명 |
|---|---|---|
| GmsCore support | 켜짐 | 계정 조회, 토큰(GetToken), 계정 선택기만 GmsCore로 보냅니다. clearcut, phenotype 같은 나머지 Play 서비스 API는 설치된 Play 서비스를 그대로 씁니다. |
| Custom reader font | 켜짐 | 리더 WebView의 글꼴 요청을 사용자 글꼴로 바꿉니다. 기기에서 글꼴을 고르는 "Books 글꼴" 화면이 추가됩니다. |
| GmsCore account type (extended) | 꺼짐 | 실험 기능입니다. 앱 안에 남아 있는 `Account(name, "com.google")` 생성(업로드, 컬렉션, 고객센터 페이지, 백그라운드 동기화)도 GmsCore 계정 타입으로 바꿉니다. |

### GmsCore support 옵션

- `gmsCoreVendorGroupId` (기본값 `app.revanced`): 사용할 GmsCore의 패키지는 `<vendor>.android.gms`, 계정 타입은 `<vendor>`입니다.
- `spoofedPackageSignature` (기본값 `38918a453d07199354f8b19af05ec6562ced5788`): GmsCore가 Google OAuth에 보고하는 서명입니다.
  Android 13에서 쓰이는 v3.1 서명자 `bd32424203e0…`를 넣으면 `UNREGISTERED_ON_API_CONSOLE` 오류가 납니다.

### Custom reader font 옵션

- `fontSettingsLauncherShortcut` (기본값 켜짐): 런처에 "Books 글꼴" 아이콘을 추가합니다.
- `bundledFontPath` (선택): 패치할 때 APK에 넣을 TTF/OTF 파일 경로입니다. 기기에서 글꼴을 고르기 전까지 이 글꼴을 씁니다.

## 사용 방법

1. Play 북 split APK(APKM/APKS)를 준비합니다. 검증한 버전은 `2026.9.4.1 (386871)`입니다.
2. 이 저장소의 GitHub Actions 산출물(`books-piko-patches`)에서 `.mpp` 패치 번들을 받습니다.
   직접 빌드하려면 `read:packages` 권한이 있는 GitHub 토큰이 필요합니다.
   ```bash
   GITHUB_ACTOR=<사용자명> GITHUB_TOKEN=<토큰> ./gradlew buildAndroid
   # 결과: patches/build/libs/patches-<version>.mpp
   ```
3. Morphe Manager(사용자 패치 소스) 또는 [Morphe Desktop/CLI](https://github.com/MorpheApp/morphe-cli)로 패치합니다.
   ```bash
   java -jar morphe-desktop-*-all.jar patch -p patches-<version>.mpp play-books.apkm
   ```
4. 기존 Play 북을 지우고 패치한 APK를 설치합니다. 서명이 바뀌므로 덮어쓰기는 되지 않습니다.
5. [ReVanced GmsCore](https://github.com/ReVanced/GmsCore)를 설치하고 GmsCore에서 Google 계정에 로그인합니다.
6. Play 북을 처음 실행하면 **연락처** 권한(GET_ACCOUNTS)을 요청합니다. 허용하세요. 이 권한이 없으면 GmsCore가 계정을 넘겨주지 않아 로그인 화면에서 넘어가지 못합니다.
   창을 놓쳤다면 설정 → 애플리케이션 → Play 북 → 권한 → 연락처에서 허용하세요.

NPatch 같은 도구를 함께 쓸 때는 전체 **MicroG/GMS redirect를 꺼야** 합니다. 필요한 경로는 이 패치가 직접 리디렉션합니다.
전체 리디렉션을 켜면 GmsCore에 없거나 동작이 다른 서비스까지 넘어가서 초기화가 깨집니다.

## 사용자 글꼴

- 런처의 **Books 글꼴**에서 기본(Regular) 글꼴을 고릅니다. 굵게/기울임/굵은 기울임 글꼴은 선택 사항입니다.
  따로 넣지 않으면 기본 글꼴에서 자동으로 합성됩니다.
- 파일 관리자에서 글꼴 파일을 **Play 북으로 열기/공유**해도 기본 글꼴로 설치됩니다.
- 설정 항목:
  - **모든 리더 글꼴 대체**: Literata, Merriweather, Vollkorn, Goudy를 모두 사용자 글꼴로 바꿉니다. 끄면 기본 글꼴(Literata)만 바꿉니다.
  - **출판사 지정 글꼴 대신 강제 적용**: 리더에서 글꼴을 따로 고르지 않아도(출판사 기본) 사용자 글꼴을 씁니다.
  - **코드/고정폭 글꼴 유지**: `pre`, `code`, `kbd`, `samp`, `tt`는 강제 적용에서 뺍니다.
- 변경 사항은 책을 다시 열 때 적용됩니다.

동작 방식은 [docs/ANALYSIS.md](docs/ANALYSIS.md)를 보세요.

## 새 Play 북 버전에 대응하기

1. 새 APKM으로 패치를 실행합니다. 실패한 패치는 fingerprint 이름과 이유
   (예: `Expected 1 occurrence(s) of "com.google" in Lpir;->onResume but found 2`)를 출력합니다.
2. [docs/ANALYSIS.md](docs/ANALYSIS.md)의 패치 지점 표를 보고 새 버전에서 같은 의미의 코드를 찾아 fingerprint를 고칩니다.
3. `patches/.../shared/Constants.kt`의 `COMPATIBILITY_PLAY_BOOKS`에 버전을 추가합니다.

## 주의

- Google Play 북 APK와 디컴파일 결과는 Google 저작물입니다. 공개 저장소나 패치 번들에 넣지 마세요.
- OAuth 토큰, 비밀번호, 세션 쿠키가 들어 있는 로그를 공유하지 마세요.

## 라이선스

GPLv3. Morphe와 Piko의 구조를 참고했습니다.
