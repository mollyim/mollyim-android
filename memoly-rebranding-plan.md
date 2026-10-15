# Memoly 이름 및 패키지 ID 전환 계획

작성일: 2026-10-15. 상태: 이름·ID·백업 호환 및 CI 구조 구현 완료, 실제 CI·APK 검증 진행 중.

## 목표와 기준값

사용자가 지정한 표시 이름은 **Memoly**다. 패키지 ID의 계획값은 **`im.memoly.app`**으로 정한다. 소유자 이름은 필수가 아니며, 이 ID의 다른 배포자 사용 여부나 앱스토어 등록 가능성은 확인하지 않았다. 공개 배포 전에는 충돌 여부를 별도로 확인한다.

| 항목 | 현재 | 전환 후 |
|---|---|---|
| 기본 표시 이름 | Molly | Memoly |
| 설치 패키지 ID | `im.molly.app` | `im.memoly.app` |
| staging 패키지 ID | `im.molly.app.staging` | `im.memoly.app.staging` |
| `baseAppFileName` | Molly | Memoly |
| Gradle namespace | `org.thoughtcrime.securesms` | 유지 |
| Java/Kotlin 클래스 패키지 | 기존 `org.thoughtcrime…`, `im.molly…` 등 | 유지 |
| 개인 release 서명 | 현재 개인 키 | 같은 키와 GitHub Secrets 유지 |
| 주 배포 변형 | `prodStoreRelease` | 유지 |

Android는 다른 패키지 ID를 별도 앱으로 취급한다. 공식 Molly 및 기존 개인 Molly와 함께 설치할 수 있는 구성을 만든다. 로컬 데이터·권한·Keystore·앱 잠금 설정은 새 앱에서 독립적으로 시작한다. 같은 전화번호의 Signal 계정을 양쪽 앱에서 동시에 주 기기로 사용할 수 있다는 의미는 아니다.

현재 설치 실패의 실제 PackageManager 오류 코드는 확보하지 못했다. 이번 전환은 앱 식별자를 분리하는 작업이며, 알려진 증상이 반드시 해결된다는 뜻은 아니다.

## 코드에서 확인한 변경 지점

| 위치 | 확인 결과와 작업 |
|---|---|
| `app/gradle.properties:2` | `baseAppTitle`, `baseAppFileName`, `basePackageId`가 기본값이다. 세 항목을 변경한다. |
| `app/build.gradle.kts:42` | `CI_APP_TITLE`, `CI_APP_FILENAME`, `CI_PACKAGE_ID`가 `-PCI`에서 기본값을 덮는다. 일반 CI와 release의 최종값이 일치하도록 확인한다. |
| `app/build.gradle.kts:169` | `applicationId`, 생성 리소스 `app_name`, `package_name`이 위 값으로 연결된다. staging은 `.staging`을 추가한다. |
| `app/src/main/AndroidManifest.xml` | 앱 전용 권한과 네 provider authority는 `${applicationId}`를 사용한다. 라이브러리가 추가한 provider도 최종 manifest에서 확인한다. |
| `app/src/main/res/xml/authenticator.xml`, `syncadapter.xml` | 계정 타입은 `@string/package_name`을 사용한다. 연락처 동기화 코드의 `BuildConfig.APPLICATION_ID`와 일치해야 한다. |
| `AppIconPreset.kt:30` | 아이콘 alias의 클래스 이름은 `org.thoughtcrime.securesms`다. 새 설치 ID와 클래스 이름을 구분해 유지한다. |
| `PassphraseRequiredActivity.java:95` | 현재 화면과 목적 화면의 클래스 이름을 비교한다. 새 ID에서도 DB 암호·PIN 복원·등록 진입이 반복되지 않는지 확인한다. |
| `FullBackupImporter.java:315` | 현재 앱 및 Signal 설정 파일명만 기본 설정으로 매핑한다. 이전 Molly 파일명에 대한 명시적 호환 매핑이 필요하다. |
| `app/src/main/res/values/firebase_messaging.xml` | upstream Firebase 설정을 사용한다. 새 패키지와 개인 인증서의 실제 FCM 동작은 검증 전이다. |
| `.github/scripts/verify-personal-apk.sh:32` | `im.molly.app`을 고정 검사한다. Memoly ID를 엄격히 검사하도록 변경하고 거부 경로 테스트도 수정한다. |
| `.github/workflows/release.yml:158` | 이전 `*-personal.*` 배포를 선택한다. 새 앱의 업데이트 검사 대상을 별도 릴리스 계열로 분리한다. |
| `PERSONAL_RELEASE.md` | 기존 패키지·덮어쓰기·이전 안내를 Memoly 기준으로 갱신한다. |

소스의 `im.molly.app` 문자열 중에는 import와 클래스 패키지명이 있다. 설치 ID의 참조와 구분해서 다룬다. `SIGNAL_PACKAGE_NAME`은 백업 호환 등에 쓰이므로 역할을 유지한다. Signal 서비스 주소·링크 규격도 통신 호환성을 유지한다.

## 구현 순서

### 1. 기본 이름과 설치 식별자

1. 기본값을 `Memoly`, `Memoly`, `im.memoly.app`으로 변경한다.
2. Gradle namespace와 소스 디렉터리를 유지한다. 이렇게 하면 기존 `R`, `BuildConfig`, JNI 및 클래스 참조를 보존하고 upstream 병합 범위를 줄일 수 있다.
3. 최종 manifest의 Activity·Service·Receiver 클래스가 기존 실제 클래스를 가리키는지 확인한다. 특히 런처 alias와 아이콘 변경을 확인한다.
4. 기본 앱 이름이 Memoly로 보이도록 리소스를 정리한다. XML 분석에서 `values-af`, `values-hi`, `values-pt`에 활성 `app_name=Signal`이 발견됐으므로 함께 처리한다. 한국어·영어 및 해당 언어의 최종 리소스를 확인한다. 사용자가 선택하는 위장 아이콘의 별도 이름은 해당 기능의 동작에 맞춰 유지한다.
5. `baseAppFileName`의 백업 파일 접두어 변경과 실제 release APK 첨부 파일명을 구분한다. 첨부 파일명도 Memoly로 표시하려면 workflow의 복사·체크섬·업로드 경로를 함께 바꾼다.

### 2. 앱 내부 연결과 기존 정책 보존

1. provider authority, 앱 전용 signature 권한, 계정 타입이 모두 새 ID에 연결되도록 최종 APK를 검사한다.
2. 파일 공유, 아바타·첨부파일 열기, 연락처 링크, 알림 PendingIntent, 단축키, Telecom PhoneAccount 및 UnifiedPush 등록의 대상 패키지를 확인한다.
3. `sgnl`, `signal.me`, `signal.group`, 기기 연결 링크의 기존 규격을 유지한다. 여러 앱 설치 시 앱 선택과 Android의 링크 연결 설정을 확인한다. 해당 도메인의 자동 연결을 새 ID에서 보장하지 않는다.
4. PIN·생체 인증의 공통 접근 정책을 유지한다. 즉시 잠금, 기존 알림 미리보기, 인증 없는 거절·종료, 인증이 필요한 통화 응답을 회귀 검증한다.
5. Keystore 별칭은 앱별 저장 영역 안에서 구분되므로 이름 때문에 일괄 변경하지 않는다. 새 앱은 PIN을 새로 설정하며 이전 앱의 키나 PIN 검증값을 복사하지 않는다.

### 3. 백업을 이용한 데이터 이전

1. 이전 설정 파일명 `im.molly.app`, `im.molly.app_preferences`를 importer에서 새 secure preferences로 연결한다. staging을 지원하는 경로에는 이전 staging 이름을 환경에 맞게 대응시킨다.
2. 기존 Signal 호환 파일명과 새 Memoly 파일명도 계속 처리한다. 임의 파일명을 모두 기본 설정으로 합치지 않고 확인한 이름만 허용한다.
3. 계정 정보·일반 설정·첨부파일 및 백업에 포함된 이전 authority URI의 처리 여부를 점검한다. 이미 처리하는 경로는 유지하고 누락된 경로만 보완한다.
4. 새 앱의 기기 전용 PIN·생체 상태는 복원하지 않는다. 백업 복원 직후 키가 없는 잠금 상태가 생기지 않는지 확인한다. 기존 앱의 손상된 PIN을 자동 해제하는 동작은 추가하지 않는다.
5. 앱 데이터 디렉터리 직접 복사 대신 암호화 백업 복원을 안내한다. 백업 암호를 확보하고 메시지·미디어·설정 복원을 확인한 후 기존 앱 정리 여부를 사용자가 결정한다.

### 4. 푸시 알림과 업데이트

**FCM은 핵심 미확인 항목이다.** Firebase의 Android 앱 등록은 패키지 ID에 연결되며, API 키의 앱 제한이 있을 수 있다. 저장소만으로 upstream Firebase 프로젝트의 허용 범위를 확인할 수 없다.

- 새 APK에서 FCM 토큰 생성뿐 아니라 서버 등록, 백그라운드 메시지 수신, 통화 수신까지 확인한다. 토큰 생성만으로 완료 처리하지 않는다.
- 실패하면 기존 WebSocket 경로로 등록·상시 수신이 가능한지 확인한다. 알림 방식 변경이 필요하면 배터리·백그라운드 동작 차이를 기록하고 사용자에게 선택 근거를 제시한다.
- 자체 Firebase 프로젝트의 클라이언트 설정만 넣어서 Signal 서버가 해당 프로젝트로 푸시를 보낼 수 있다고 가정하지 않는다. 서버 측 지원 여부 확인이 필요하다.
- `prodStoreRelease`의 `MANAGE_MOLLY_UPDATES=false`를 유지한다. website 변형을 개인 패키지로 빌드할 때도 공식 Molly 업데이트 경로가 켜지지 않도록 처리한다.
- 이번 범위의 업데이트는 GitHub에서 다음 Memoly APK를 내려받아 설치하는 방식으로 정한다. 같은 패키지 ID·개인 키와 증가하는 versionCode를 유지한다.

### 5. CI와 첫 Memoly 릴리스

1. APK 검증기는 `im.memoly.app`만 허용하도록 엄격히 검사한다. 공식 Molly·staging·다른 ID 및 다른 서명은 계속 거부한다. 검사값을 새 APK에서 그대로 가져와 자기 자신과 비교하는 방식은 사용하지 않는다.
2. 첫 Memoly 태그는 `v8.19.2-4-memoly.1`을 기준으로 한다. workflow의 태그 트리거·입력 예시·이전 배포 검색·release 제목과 설명을 함께 갱신한다. 기존 `personal.*` 태그와 APK는 보존한다.
3. 이전 APK는 `*-memoly.*` 계열에서 선택한다. 선택한 APK도 패키지·서명을 검증하고 versionCode 증가를 요구한다. 첫 Memoly 빌드는 비교 대상이 없다는 사실을 명시한다. 기존 Molly APK와 업데이트 호환성을 주장하지 않는다.
4. 현재 소스 기준 첫 Memoly versionCode는 `171906`을 제안한다 (`currentHotfixVersion=2`). 향후 upstream 버전 변경 시 현재 계산식에 맞춰 다시 확인한다.
5. 현재 개인 서명 키와 Secret 세 개를 재사용한다. 태그는 새 커밋에 생성하며 기존 태그를 이동하지 않는다. 검증된 APK와 체크섬을 draft release에 올린다.
6. release lint, 관련 단위 테스트, APK 검증기 테스트와 서명 검사를 모두 유지한다. CI 파이프라인 개선을 함께 구현한다. 상세 구성은 [CI.md](CI.md)에 기록한다.

## 검증과 완료 기준

| 검증 | 완료 조건 |
|---|---|
| 최종 APK 식별자 | package=`im.memoly.app`, 기본 label=`Memoly`, 기존 실제 클래스명, 새 권한·authority·계정 타입 |
| 산출물 기본 조건 | 개인 인증서 일치, debuggable/testOnly 아님, 독립 설치에 필요한 완전한 APK, SHA-256 제공 |
| 빌드 회귀 | `prodStoreDebug` 컴파일·관련 테스트와 `prodStoreRelease` lint·서명 빌드 통과 |
| 신규 설치 | 사용자의 Android 17 폴드에서 실제 설치·첫 실행 성공. 실패하면 `INSTALL_FAILED_*`와 함께 기록 |
| 공존 | 공식 Molly 또는 이전 개인 Molly가 있는 별도 시험 환경에서 각 앱 실행·파일 공유·연락처·알림이 서로 혼동되지 않음 |
| 업데이트 | 새 ID와 같은 개인 키로 더 높은 versionCode의 두 번째 APK를 설치해 데이터·PIN·설정 유지 확인 |
| 등록·복원 | 최초 등록, DB 암호 화면, Signal PIN 복원 진입에 반복 이동 없음. 기존 암호화 백업의 메시지·미디어·설정 복원 확인 |
| 인증 | PIN·생체 정책, 즉시 잠금·재시작·통화·알림 진입에서 기존 접근 제한 유지 |
| 외부 진입 | 아이콘 변경, 공유·열기, 그룹·사용자·기기 연결 링크가 올바른 앱·화면으로 연결됨 |
| 메시지·통화 | 선택한 알림 방식으로 앱 이탈·화면 꺼짐 뒤 메시지와 통화 수신 확인 |

파일명 매핑과 CI의 패키지·이전 릴리스 선택에는 회귀 테스트를 둔다. 단순 문자열 교체 자체를 반복 검사하는 테스트는 추가하지 않는다. APK 내부 정적 검사는 실기기 설치·푸시·통화 검증과 구분해 기록한다.

실기기 검증 전에는 상태를 “빌드 및 정적 검사 통과, 기기 검증 대기”로 표시한다. 설치와 기본 수신·인증까지 확인한 뒤 사용 가능한 첫 Memoly 빌드로 판정한다.

## 작업량 예상

핵심 범위는 약 10~16개 파일이다. 이름·ID 기본값, 로케일 리소스, 백업 호환, APK 검증 및 테스트, workflow, 배포 문서가 포함된다. 컴파일 후 발견되는 패키지 참조와 푸시 대응에 따라 늘어날 수 있다.

설정 및 CI 변경은 비교적 작지만, 백업 복원·실기기 수신 검증이 완료 시간을 좌우한다. Firebase 프로젝트 설정 변경이나 별도 푸시 인프라가 필요하면 추가 작업으로 분리한다. 연결된 Android 기기가 없는 현재 환경에서는 실기기 성공을 확정할 수 없다.

## 근거

- 조사 기준 커밋: `06a38790cf918eeb597fd6b6e9265047592c36c0`.
- [Android: application ID와 namespace](https://developer.android.com/build/configure-app-module) — 설치 식별자와 코드 namespace는 독립적으로 설정할 수 있다.
- [Firebase: Android 앱 등록](https://firebase.google.com/docs/android/setup) — Firebase Android 앱 등록의 패키지 이름은 등록 후 변경할 수 없다.
- [Firebase: API 키 관리](https://firebase.google.com/docs/projects/api-keys) — 앱 제한 설정을 별도로 확인해야 한다.
