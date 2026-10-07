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
| `app.bookspiko.GMSCORE_PACKAGE` / `…ACCOUNT_TYPE` meta-data | extension이 읽음 |

### 바이트코드

| Fingerprint | 이 버전의 위치 | 찾는 방법 | 변경 |
|---|---|---|---|
| `GoogleAuthUtilClinitFingerprint` | `amhk.<clinit>` | `"com.google.work"`, `"cn.google"`, `"…auth.GetToken"`, `"GoogleAuthUtil"` | 지원 계정 타입 배열 `com.google`를 `app.revanced`로 바꾸고, GetToken `ComponentName`의 패키지를 `app.revanced.android.gms`로 바꿈(클래스 이름은 유지) |
| `GoogleAuthUtilGetAccountIdFingerprint` | `amhk.b(Context,String)` | `"accountName must be provided"`, `"^^_account_id_^^"` | `Account(name, type)`의 type |
| `GoogleAuthUtilGetAccountsFingerprint` | `amhk.h(Context)` | `"get_accounts"`, `"…gms.auth.accounts"` | provider authority, `call("get_accounts", type)`의 type |
| `GoogleAuthUtilGetAccountsWithFeaturesFingerprint` | `amhk.i(Context,String[])`에서 생성자 `amhe.<init>`으로 이동 | `Activity.getComponentName()`에 이어 `<init>([String;String;L;J;J)` | 람다가 담는 계정 타입 (수동 패치에는 없음. GetToken이 GmsCore로 가므로 함께 바꿔야 일관됨) |
| `GoogleAuthUtilTokenAccountTypeCheckFingerprint` | `amjf.b` | `"Account type "`, `" is not supported."` | `Account.type.equals(...)` 비교값 |
| `AccountPickerIntentFingerprint` | `aney.a` | `CHOOSE_ACCOUNT`, `CHOOSE_ACCOUNT_USERTILE` | `Intent.setPackage(...)` 대상 (GmsCore의 `AccountPickerActivity`가 같은 action을 받음) |
| `GoogleAccountTypeValidatorFingerprint` | `avem.a(String)Z` | 4가지 Google 계정 타입 문자열 | 메서드 앞에 `app.revanced`면 true를 반환하는 코드 삽입. 원래 검사(`com.google.work`, `cn.google`, `__logged_out_type`)는 그대로 둠. 수동 패치는 이 셋을 지워버렸음 |
| `AccountsUpdateListenerFingerprint` | `atxm.a` | `OnAccountsUpdateListener` 필드, `String[]` 인자 API 호출 | 리스너가 감시할 계정 타입 |
| `AddAccountFingerprint` | `oyj.f` | `"introMessage"`, `AccountManager.addAccount` | addAccount type |
| `OneGoogleAddAccountClickFingerprint` | `assd.onClick` | `"ACCOUNT_MANAGER"`, `"ADD_ACCOUNT_ACTIVITY"` | addAccount type |
| `BaseBooksActivityOnActivityResultFingerprint` | `pir.onActivityResult` | `"authAccount"`, `new Account` | 선택 결과 계정 타입 |
| `BaseBooksActivityOnResumeFingerprint` | `pir.onResume` | `"GMSCore check: unresolvable error %s"`, `"login_hint"` | 계정 선택기 허용 타입. `super.onResume()` 뒤에 GET_ACCOUNTS 요청과 GmsCore 설치·동결 확인 호출 삽입 |
| `BaseBooksActivityAccountFromIntentFingerprint` | `pir.v(Intent)` | `"authAccount"`, `"email"`, `Account.<init>` | AccountData에서 만든 계정 타입 |
| `AppSingletonGetAccountComponentFingerprint` | `AppSingleton.getAccountComponent(Account)` | 난독화되지 않은 이름 | 맨 앞에서 `(email, com.google)`을 `(email, app.revanced)`로 바꿈. 계정 컴포넌트 캐시 키가 Account 전체여서 생기는 `multiple DataStores active for the same file` 크래시를 막음 |

각 메서드 안에서 바꿀 리터럴은 정확히 1개여야 하고, 그 값을 처음 읽는 명령이 위 표의 사용처
(`Account.<init>`의 2번째 인자, `Intent.setPackage`, `filled-new-array` 등)와 맞아야 합니다.
조건이 맞지 않으면 `PatchException`으로 멈춥니다.

치환하지 않는 곳:
`"<<default account>>"`를 쓰는 `GetServiceRequest` 계열(`anke`, `anlf`)은 순정 Play 서비스로 가는 요청이고,
`amhk.i`의 `annf.h("com.google")`는 null 검사일 뿐입니다.
나머지 `Account(name, "com.google")` 생성은 대부분 `AppSingleton` 정규화를 거칩니다.
이 부분은 선택 패치인 *GmsCore account type (extended)* 에서 일괄 변경할 수 있습니다.

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
   - 대체 대상 family의 bold/italic `@font-face` 중 해당 사용자 파일이 없는 것은 지웁니다. 그러면 브라우저가 regular에서 굵게/기울임을 합성합니다.
   - 강제 적용을 켜면 `body { font-family: literata; }`를 `<selector> { font-family: literata !important; }`로 바꿉니다.
   - 고정폭 유지를 켜면 `*` 선택자를 `*:not(pre):not(code):not(kbd):not(samp):not(tt):not(pre *):not(code *)`로 바꿉니다.

   고칠 문자열을 찾지 못하면(엔진이 바뀐 경우) 그 단계만 건너뜁니다. 1번의 글꼴 교체는 그대로 동작합니다.

글꼴은 `files/books_piko_fonts/`에, 설정은 SharedPreferences `books_piko_font`에 저장합니다.
TTF, OTF, TTC, WOFF, WOFF2를 파일 시그니처로 확인합니다.
Piko Twitter의 방식(SAF로 파일 선택, 앱 내부 저장소로 복사, 런타임 적용)을 따랐습니다.

## 3. 검증

검증은 이 저장소를 만든 샌드박스에서 했습니다. 이 샌드박스에서는 GitHub Packages, Google Maven, JitPack에 접근할 수 없었습니다.
그래서 Morphe patcher 1.15.1, Morphe smali 포크, ARSCLib을 소스에서 빌드한 하네스를 썼습니다.
apkzlib과 서명 코드만 스텁으로 바꿨습니다.

| 검사 | 결과 |
|---|---|
| fingerprint 15개 각각의 매칭 수 (`matchAll`) | 모두 정확히 1개이며, 위 표의 메서드와 일치 |
| 원본 APK에 GmsCore support와 Custom reader font 적용 | 두 패치 모두 성공 |
| 원본·수동 성공본·패치본을 baksmali로 풀어 클래스 비교 | 수동본이 바꾼 9개 클래스를 모두 바꿈. 추가로 바뀐 클래스는 의도한 `amhe`, `yyl`뿐이고 나머지는 동일 |
| 수동 성공본과 직접 diff | `amhk`, `amjf`, `aney`, `assd`, `atxm`, `oyj`는 완전히 동일. `pir`은 훅 1줄 추가. `avem`, `AppSingleton`은 위에서 설명한 개선 버전 |
| Manifest | 수동본의 변경을 모두 포함하고 글꼴 Activity와 런처 별칭이 추가됨 |
| `compiled.js` 재작성 (3가지 설정) | 결과 JS가 `node --check` 통과. 의도한 부분만 바뀜 |
| extension Java | javac와 dx로 dex 변환 성공. 패치 시 20개 클래스가 병합됨 |

기기 실측(로그인, 라이브러리 진입, 글꼴 표시)은 아직 하지 않았습니다. 패치 번들 빌드는 GitHub Actions에서 확인합니다.
