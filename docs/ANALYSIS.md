# 분석 노트와 패치 지점

기준 버전: Play 북 `2026.9.4.1 (386871)`, split APK 4개(base, arm64_v8a, ko, xhdpi). APKEditor 1.4.9로 병합했습니다.
아래 클래스·메서드 이름은 이 버전의 난독화 이름이며, 패치는 이 이름을 쓰지 않습니다.

## 1. GmsCore support

재서명한 앱은 순정 Play 서비스의 서명 검사(`GoogleCertificatesRslt: not allowed`)를 통과하지 못합니다.
계정, 토큰, 계정 선택기 경로만 GmsCore로 보내고 나머지 Play 서비스 API는 건드리지 않습니다.

### Manifest

| 변경 | 이유 |
|---|---|
| `GET_ACCOUNTS`의 `maxSdkVersion="22"` 제거 | Android 13에서 권한이 요청되지 않아 GmsCore가 계정을 돌려주지 않음 |
| `<queries>`에 `app.revanced.android.gms` 패키지와 `…auth.accounts` provider 추가 | 패키지 가시성 |
| `app.revanced.android.gms.SPOOFED_PACKAGE_NAME` / `…SIGNATURE` meta-data | GmsCore가 원래 앱으로 위장해 OAuth 수행 |

### 바이트코드

| Fingerprint | 이 버전의 위치 | 찾는 방법 | 변경 |
|---|---|---|---|
| `GoogleAuthUtilClinitFingerprint` | `amhk.<clinit>` | `"com.google.work"`, `"cn.google"`, `"…auth.GetToken"`, `"GoogleAuthUtil"` | 지원 계정 타입 배열 `com.google`를 `app.revanced`로 바꾸고, GetToken `ComponentName`의 패키지를 `app.revanced.android.gms`로 바꿈(클래스 이름은 유지) |
| `GoogleAuthUtilGetAccountIdFingerprint` | `amhk.b(Context,String)` | `"accountName must be provided"`, `"^^_account_id_^^"` | `Account(name, type)`의 type |
| `GoogleAuthUtilGetAccountsFingerprint` | `amhk.h(Context)` | `"get_accounts"`, `"…gms.auth.accounts"` | provider authority, `call("get_accounts", type)`의 type |
| `GoogleAuthUtilTokenAccountTypeCheckFingerprint` | `amjf.b` | `"Account type "`, `" is not supported."` | `Account.type.equals(...)` 비교값 |
| `AccountPickerIntentFingerprint` | `aney.a` | `CHOOSE_ACCOUNT`, `CHOOSE_ACCOUNT_USERTILE` | `Intent.setPackage(...)` 대상 (GmsCore의 `AccountPickerActivity`가 같은 action을 받음) |
| `GoogleAccountTypeValidatorFingerprint` | `avem.a(String)Z` | 4가지 Google 계정 타입 문자열 | 메서드 앞에 `app.revanced`면 true를 반환하는 코드 삽입. 원래 검사(`com.google.work`, `cn.google`, `__logged_out_type`)는 그대로 둠. 수동 패치는 이 셋을 지워버렸음 |
| `AccountsUpdateListenerFingerprint` | `atxm.a` | `OnAccountsUpdateListener` 필드, `String[]` 인자 API 호출 | 리스너가 감시할 계정 타입 |
| `AddAccountFingerprint` | `oyj.f` | `"introMessage"`, `AccountManager.addAccount` | addAccount type |
| `OneGoogleAddAccountClickFingerprint` | `assd.onClick` | `"ACCOUNT_MANAGER"`, `"ADD_ACCOUNT_ACTIVITY"` | addAccount type |
| `BaseBooksActivityOnActivityResultFingerprint` | `pir.onActivityResult` | `"authAccount"`, `new Account` | 선택 결과 계정 타입 |
| `BaseBooksActivityOnResumeFingerprint` | `pir.onResume` | `"GMSCore check: unresolvable error %s"`, `"login_hint"` | 계정 선택기 허용 타입. `super.onResume()` 뒤에 GET_ACCOUNTS 확인 삽입. 권한이 없으면 프로세스당 한 번 요청하고, 그 onResume의 계정 처리는 건너뜀(권한 창 위에 계정 선택기가 뜨지 않게) |
| `BaseBooksActivityAccountFromIntentFingerprint` | `pir.v(Intent)` | `"authAccount"`, `"email"`, `Account.<init>` | AccountData에서 만든 계정 타입 |
| `AppSingletonGetAccountComponentFingerprint` | `AppSingleton.getAccountComponent(Account)` | 난독화되지 않은 이름 | 맨 앞에서 `(email, com.google)`을 `(email, app.revanced)`로 바꿈. 계정 컴포넌트 캐시 키가 Account 전체여서 생기는 `multiple DataStores active for the same file` 크래시를 막음 |

각 메서드 안에서 바꿀 리터럴은 정확히 1개여야 하고, 그 값을 처음 읽는 명령이 위 표의 사용처
(`Account.<init>`의 2번째 인자, `Intent.setPackage`, `filled-new-array` 등)와 맞아야 합니다.
조건이 맞지 않으면 `PatchException`으로 멈춥니다.

치환하지 않는 곳:
`"<<default account>>"`를 쓰는 `GetServiceRequest` 계열(`anke`, `anlf`)은 순정 Play 서비스로 가는 요청이고,
`amhk.i`(features로 계정 조회)와 그 람다 `amhe`의 `"com.google"`도 그대로 둡니다. Play 북은 `features=["service_uca"]`로 요청합니다. 이걸 GmsCore 타입으로 바꾸면 GmsCore가 그 기능으로 계정을 걸러 빈 목록을 돌려주고, 앱이 로그인 화면에서 넘어가지 못합니다. `com.google`이면 이 호출이 실패하고, 앱은 위의 계정 provider 경로로 넘어갑니다. 수동 성공본도 이 상태였습니다.
나머지 `Account(name, "com.google")` 생성은 대부분 `AppSingleton` 정규화를 거칩니다.
이 부분은 선택 패치인 *GmsCore account type (extended)* 에서 일괄 변경할 수 있습니다.

### 저장된 계정 (`oyw`)

앱은 고른 계정 이름을 SharedPreferences `account`에 저장하고, 다음 실행 때 `Account(이름, "com.google")`로 복원합니다(`oyw.<init>`).
저장된 계정을 계정 목록과 맞춰 보는 `oxx.a`는 **이름과 타입**을 둘 다 비교합니다. 그래서 GmsCore 계정(`app.revanced`)과는 한 번도 맞지 않았고, 실행할 때마다 계정 선택 창이 떴습니다.
복원 타입도 GmsCore 타입으로 바꿉니다(`SavedAccountFingerprint`).

## 설정 화면 항목

Play 북 설정 화면은 Compose로 되어 있습니다. 구성은 다음과 같습니다.
- 정적 트리 `ajft.a`: `CategoryNode`/`ItemNode`이고, 클래스 이름이 난독화되지 않았습니다. 노드는 enum `ajfr` 값을 가리킵니다. enum 값의 이름(`READING_CATEGORY`, `ABOUT_PLAY_BOOKS` 등)은 남아 있습니다.
- Dagger가 만든 `Map<ajfr, 항목>`: 화면마다 따로 받습니다. `ajfx`(항목 레지스트리, `ajfy.a()`에서 생성), `ajhu`(목록 모델), `ajhr`(화면)가 각각 `beya.a()`로 새 Map을 받습니다.
  세 곳 모두 `MapsKt.getValue`로 항목을 꺼내므로, Map에 없는 id가 트리에 있으면 `NoSuchElementException`으로 앱이 종료됩니다.

"사용자 글꼴" 항목은 이렇게 넣습니다.
1. Map을 읽는 클래스의 생성자 맨 앞에서 `SettingsHook.registerFontItem(Map)`으로 Map 인자를 바꿉니다.
   - Map을 읽는 클래스는 이렇게 찾습니다. `getValue(Map, Object)` 호출 바로 뒤에 설정 항목 타입(`ajfq`와 그 하위 인터페이스 `ajfl`, `ajfn`)으로 캐스팅하는 클래스입니다.
     항목 타입과 `getValue`는 레지스트리 클래스(`ajfx`)에서 읽어 옵니다. 이 버전에서는 `ajfx`, `ajhu`, `ajhr` 세 곳입니다.
   - 처음에는 `ajfy.a()` 한 곳만 바꿨습니다. 그래서 Ebook reading 화면(`ajhu`/`ajhr`의 Map)에서 새 id를 찾지 못해 앱이 종료됐습니다.
   - 트리 루트는 패치가 `SettingsHook.treeRoot()` 본문을 `sget-object ajft->a`로 바꿔서 넘깁니다.
   - 트리와 Map 어디에도 쓰이지 않는 enum 값을 하나 고릅니다. 이 버전에서는 `GENERAL_HEADER`이고, 앱 코드 어디서도 참조하지 않습니다.
   - `READING_CATEGORY` 노드의 자식 목록 끝에 그 값의 `ItemNode`를 추가합니다.
   - Map에는 그 값으로 "Google Play 북 정보" 행 클래스의 새 인스턴스를 넣습니다.
2. 정보 행의 Compose 모델 함수에 훅을 넣습니다(`AboutSettingsItemFingerprint`, `resourceLiteral(about_play_books_settings_title)`로 찾음).
   - 제목과 부제를 바꿀 수 있게 합니다.
   - 클릭 람다는 맨 앞에서 `SettingsHook.onClick`을 확인합니다.
   - 새로 만든 인스턴스일 때만 "사용자 글꼴" 제목, 현재 글꼴 이름 부제, 글꼴 설정 화면 열기로 동작합니다.

이렇게 하면 Compose UI 코드를 새로 만들지 않고 기존 행 모양을 그대로 씁니다.

## 2. 리더 렌더링 경로

EPUB 리플로우 본문은 **WebView**가 그립니다.

- `zqu`: 화면에 보이지 않는 paginator WebView입니다. 문자열 리소스 `reader_html`을 `loadDataWithBaseURL("http://<volume>.localhost/", …)`로 띄우고 `assets/compiled.js`를 불러옵니다.
- `zsb`: 페이지 spread WebView입니다. `spread_template.html`과 `spread.js`를 씁니다.
- 두 WebView 모두 WebViewClient `yyl`(그리고 그 하위 클래스 `zrz`)을 씁니다.
  `yyl.a(WebView,String)`(`shouldInterceptRequest`)는 다음을 처리합니다.
  - `…/assets/<js>` 요청에는 APK assets를 돌려줍니다.
  - `…/fonts/<file>` 요청에는 `assets/fonts/<file>`을 `font/<ext>`로 돌려줍니다.
- `compiled.js`의 글꼴 처리:
  - `wj` 표에 `literata`, `Merriweather`, `Vollkorn`, `OFLGoudyStMTT`의 `@font-face { src: url("/fonts/…") }` 선언이 있습니다.
  - 리더에서 글꼴을 고르면(`fontFamilyOverride`) `wj[x]`와 `* { font-family: "x" !important; }`를 주입합니다.
  - 고르지 않으면(출판사 기본) `wj.literata`와 `body { font-family: literata; }`만 주입합니다.
  - `serif`는 `literata`로 매핑됩니다.

### Custom reader font

`yyl.a` 맨 앞에 `ReaderFontPatch.interceptRequest(WebView, String)` 호출을 넣었습니다. 반환값이 null이 아니면 그 응답을 씁니다.

1. `/fonts/<file>` 요청은 대체 대상 family(전체, 또는 literata만)이면 사용자 글꼴 파일로 응답합니다.
   파일 이름의 bold/italic에 맞는 변형이 있으면 그것을 쓰고, 없으면 regular를 씁니다.
2. `/assets/compiled.js` 요청은 원본을 읽어 다음처럼 고친 뒤 응답합니다(설정별로 캐시).
   - 엔진에는 글꼴 강제 기능이 있습니다. 리더에서 글꼴 `Z`를 고르면 `* { font-family: "Z" !important; }`를 넣고, 출판사 CSS의 모든 `font-family` 앞에 `Z`를 붙이고, 다른 이름의 `@font-face`는 지웁니다(`Pi`). 그래서 별도 이름의 글꼴을 주입하면 지워지거나 `Z`에 밀립니다.
   - 대신 글꼴 설정 생성자(`Dj`)의 `this.Z=` 대입을 고칩니다. 강제 적용이면 항상 `"literata"`, 모든 글꼴 대체면 무엇을 고르든 `"literata"`로 바꿉니다. `literata` 파일은 1번에서 사용자 글꼴로 응답하므로, 레이아웃과 표시 모두 엔진 자체 경로로 사용자 글꼴을 씁니다.
   - 대체 대상 family의 bold/italic `@font-face` 중 해당 사용자 파일이 없는 것은 지웁니다. 그러면 브라우저가 regular에서 굵게/기울임을 합성합니다.
     엔진 JS에 박힌 표(`wj`)뿐 아니라, 앱이 런타임에 넘기는 `gpb-literata` 굵게/기울임 face(출판사 기본일 때 쓰임, `adsj`)를 만드는 코드에도 같은 조건을 넣었습니다.
     Chromium에서 측정한 결과: 굵게 선언이 보통 굵기 파일을 가리키면 굵은 글씨의 잉크량이 1.00배로 그대로이고, 선언을 지우면 1.37배로 합성됩니다.
   - 굵은 글씨 외곽선(기본 켜짐, 굵게 파일이 없을 때): Android 17 폰의 WebView는 RIDIBatang 같은 웹 글꼴의 굵게를 합성하지 않았습니다. 데스크톱 Chromium에서는 같은 CSS로 1.47배 굵어졌습니다. 그래서 굵게 합성(`font-synthesis`)을 끄고 외곽선을 그립니다.
     - 엔진의 규칙별 스타일 처리(`Pi`, `a=a.style;if(!a)return f;` 뒤)에 훅을 넣습니다. `font-weight`가 600 이상이면 `-webkit-text-stroke-width: 0.025em`, 미만이면 0으로 둡니다.
     - 기본이 굵은 태그(`b`, `strong`, `h1`–`h6`, `th`, `dt`)와 인라인 bold에는 `:where()` 규칙을 씁니다. 우선순위가 0이라 출판사 규칙이 이깁니다.
     - 0.025em은 Chromium 합성 굵게와 비슷한 두께입니다(1.45배 대 1.47배).
     - 굵은 글꼴 계열: 출판사가 `font-weight` 대신 별도 굵은 글꼴(예: `XxxBold`)로 굵게를 표현하면, 엔진이 그 글꼴을 사용자 글꼴로 바꾸면서 굵기 정보가 사라집니다. 그래서 `@font-face`를 처리할 때(`Pi` 시작) 글꼴 이름과 파일명, 굵기를 기록합니다. 이름이나 파일명이 bold/black/heavy/-B 등이거나 굵게 face만 있는 글꼴을 쓰는 규칙에도 외곽선을 줍니다.
     - 굵기를 글꼴 이름에만 담는 책도 있습니다. 실측한 KoPub 책은 `KOPUSMjL`/`KOPUSMjM`/`KOPUSMjB`(바탕 Light/Medium/Bold)와 `KOPUSGo*`(돋움)를 쓰고, `font-weight`는 비어 있거나 `normal`이었습니다.
       그래서 글꼴 이름이나 파일명에서 굵기를 읽습니다. Light/Medium/Bold/Black 같은 단어, 그리고 소문자·숫자 뒤의 끝 글자 코드(T/L/R/M/SB/B/EB/H)를 봅니다.
       읽은 굵기는 외곽선 두께로 바꿉니다: 400 이하는 0, 700은 0.025em, 그 사이와 위는 비례.
       외곽선으로는 Light를 Regular보다 가늘게 만들 수 없습니다.
     - 같은 이름 아래 굵기별 버전을 여러 개 두는 책도 있습니다. 예: `명조` 보통=`KoPubWorldBatangLight`, `명조` 굵게=`KoPubWorldBatangBold`.
       그래서 글꼴마다 버전 목록(선언 굵기 `d`, 파일의 실제 굵기 `a`)을 기억합니다. 규칙이 요청한 굵기(없으면 보통)에 대해 브라우저처럼 가장 가까운 버전을 고르고, 그 버전의 실제 굵기를 씁니다.
       굵게를 요청했는데 굵은 버전이 없으면, 원래 브라우저가 합성했을 굵게로 봅니다.
       처음 구현은 글꼴당 굵기를 하나만 기억해서, 나중에 나온 Bold가 덮어쓰는 바람에 본문 전체가 굵게 칠해졌습니다.
     - 진단: 이 과정에서 책이 쓰는 `@font-face`와 규칙의 글꼴을 `bridge.logD`로 남깁니다. `adb shell setprop log.tag.BooksJS DEBUG` 후 `adb logcat -s BooksJS`로 볼 수 있습니다.
   - 고정폭 유지를 켜면 주입 규칙의 `*` 선택자를 `*:not(pre):not(code):not(kbd):not(samp):not(tt):not(pre *):not(code *)`로 바꿉니다.

   고칠 문자열을 찾지 못하면(엔진이 바뀐 경우) 그 단계만 건너뜁니다. 이때도 1번의 파일 교체는 동작합니다.
   처리 결과는 logcat 태그 `BooksPiko`로 남습니다(`Reader engine rewritten: …`, `Serving custom font …`).
3. WebView는 프로세스가 살아 있는 동안 엔진과 글꼴을 캐시합니다. 그래서 글꼴 설정 화면의 "Play 북 다시 시작" 버튼이 프로세스를 끝내고 앱을 다시 엽니다.

글꼴은 `files/books_piko_fonts/`에, 설정은 SharedPreferences `books_piko_font`에 저장합니다.
TTF, OTF, TTC, WOFF, WOFF2를 파일 시그니처로 확인합니다.
Piko Twitter의 방식(SAF로 파일 선택, 앱 내부 저장소로 복사, 런타임 적용)을 따랐습니다.

## 3. 검증

검증은 이 저장소를 만든 샌드박스에서 했습니다. 이 샌드박스에서는 GitHub Packages, Google Maven, JitPack에 접근할 수 없었습니다.
그래서 Morphe patcher 1.15.1, Morphe smali 포크, ARSCLib을 소스에서 빌드한 하네스를 썼습니다.
apkzlib과 서명 코드만 스텁으로 바꿨습니다.

| 검사 | 결과 |
|---|---|
| fingerprint 14개 각각의 매칭 수 (`matchAll`) | 모두 정확히 1개이며, 위 표의 메서드와 일치 |
| 원본 APK에 GmsCore support와 Custom reader font 적용 | 두 패치 모두 성공 |
| 원본·수동 성공본·패치본을 baksmali로 풀어 클래스 비교 | 수동본이 바꾼 9개 클래스를 모두 바꿈. 추가로 바뀐 클래스는 글꼴 훅을 넣은 `yyl`뿐이고 나머지는 동일 |
| 수동 성공본과 직접 diff | `amhk`, `amjf`, `aney`, `assd`, `atxm`, `oyj`는 완전히 동일. `pir`은 훅 1줄 추가. `avem`, `AppSingleton`은 위에서 설명한 개선 버전 |
| Manifest | 수동본의 변경을 모두 포함하고 글꼴 Activity와 런처 별칭이 추가됨 |
| `compiled.js` 재작성 (3가지 설정) | 결과 JS가 `node --check` 통과. 의도한 부분만 바뀜 |
| extension Java | javac와 dx로 dex 변환 성공. 패치 시 20개 클래스가 병합됨 |

기기 실측: Android 17 폰에서 로그인과 사용자 글꼴 적용을 확인했습니다(테스트 키로 서명한 APK). 패치 번들 빌드는 GitHub Actions에서 확인합니다.

실측하면서 고친 점:
- `amhk.i`/`amhe`의 계정 타입은 바꾸면 안 됩니다(위 "치환하지 않는 곳" 참고).
- GET_ACCOUNTS가 없으면 수동본도 로그인 화면에서 막힙니다. 권한 창이 계정 선택기에 가려지지 않도록, 권한을 받기 전에는 onResume의 계정 처리를 건너뜁니다.
- 글꼴 파일만 바꾸면 시스템 글꼴이나 출판사 기본을 쓸 때 적용되지 않습니다. 엔진의 글꼴 선택값을 `literata`로 고정합니다.
