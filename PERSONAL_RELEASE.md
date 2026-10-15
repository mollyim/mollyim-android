# Memoly 개인 서명 APK 배포

Memoly의 설치 패키지는 `im.memoly.app`, 기본 표시 이름은 `Memoly`입니다.
공식 Molly 및 이전 개인 Molly(`im.molly.app`)와 별도로 설치됩니다.
기존 앱의 데이터를 자동으로 이어받지 않으며, 암호화 백업·복원으로 이전합니다.
같은 전화번호 계정을 양쪽 앱에서 동시에 주 기기로 사용할 수 있다는 뜻은 아닙니다.

`prodStoreRelease`를 배포하며 website 변형을 포함해 공식 Molly 자동 업데이트는 꺼져 있습니다.
Memoly 업데이트에는 같은 패키지 ID와 같은 개인 서명 키, 증가하는 versionCode를 사용합니다.

## 처음 한 번 설정

현재 등록된 개인 서명 키와 다음 Repository Secrets를 그대로 사용합니다.

| Secret | 값 |
|---|---|
| `SECRET_KEYSTORE` | 개인 keystore의 Base64 값 |
| `SECRET_KEYSTORE_ALIAS` | 키 별칭 |
| `SECRET_KEYSTORE_PASSWORD` | keystore와 키의 공통 비밀번호 |

키와 비밀번호는 저장소 밖에 안전하게 백업합니다. Base64는 암호화가 아닙니다.
새 환경에서 키를 만드는 경우 Android Studio의 Generate Signed Bundle / APK에서
30년 유효기간의 키를 만들고 위 Secret을 등록합니다. 키를 바꾸면 기존 Memoly를 업데이트할 수 없습니다.
워크플로의 `EXPECTED_CERT_SHA256`는 현재 개인 인증서에 고정되어 있어 다른 키를 거부합니다.
인증서 확인값은 비밀이 아니며, 키 교체가 필요하면 업데이트 호환성부터 별도로 검토해야 합니다.

fork Actions를 활성화합니다. 기본 브랜치에 workflow가 있어야 Run workflow 버튼이 표시됩니다.
직접 태그를 push하는 경로는 해당 태그의 workflow를 사용합니다.

## 새 버전 만들기

1. 검증할 커밋을 push합니다.
2. `app/build.gradle.kts`의 versionCode가 이전 Memoly보다 높도록 합니다.
   계산식은 `canonicalVersionCode * 100 + mollyRevision + currentHotfixVersion`입니다.
   첫 Memoly 소스의 코드는 `171906`입니다. 태그 이름만 바꾸면 이 값은 증가하지 않습니다.
3. **Tag commit for Memoly release**에서 브랜치와 새 태그를 선택하거나,
   해당 커밋에 `v8.19.2-4-memoly.1` 형식의 새 태그를 만들어 push합니다.
   기존 `personal.*` 태그와 APK는 보존합니다.
4. **Memoly signed APK release**가 검증·빌드·서명·에뮬레이터 설치를 수행합니다.
5. 성공하면 초안 Release에 `Memoly-<태그>.apk`와 `SHA256SUMS`가 첨부됩니다.
   기본값은 초안이며 `publish`를 명시한 수동 실행만 자동 공개합니다.

기존 태그 재시도는 Memoly signed APK release의 수동 실행에서 같은 태그를 지정합니다.
공개한 Release는 이 workflow로 교체하지 않습니다.

## 릴리스 검사

- unsigned 잡에서 전체 prodStoreDebug 앱 테스트와 모든 공통 모듈 check, release lint를 실행합니다.
- Gradle을 한 번 실행해 unsigned release APK를 조립합니다. 이 잡에는 서명 Secret이 없습니다.
- 같은 실행의 artifact ID·SHA-256·소스 커밋·태그·변형을 확인한 뒤 별도 잡에서 서명합니다.
- 서명 잡은 Gradle과 Gradle 캐시를 사용하지 않습니다. 정렬 후 서명하고, 서명 뒤 APK를 수정하지 않습니다.
- 고정된 개인 인증서, `im.memoly.app`, `Memoly` 기본 이름, release 플래그,
  provider authority, 앱 전용 권한, 실제 클래스 namespace와 런처를 검사합니다.
- 최근 **공개된 Memoly**가 있으면 패키지·인증서가 같고 versionCode가 증가했는지 검사합니다.
  Molly `personal.*`는 비교 대상이 아닙니다. 초안·사용자 설치 버전은 이 비교에 포함되지 않습니다.
- Android 15 에뮬레이터에 실제 설치하고 런처로 실행한 뒤 같은 버전 재설치를 확인합니다.
  이는 더 높은 versionCode의 향후 업데이트 데이터 보존 검증과는 별개입니다.
- 임시 개인 키는 성공·실패 시 삭제합니다. configuration cache는 사용하지 않습니다.
- 공개 후 재현 빌드는 Docker에서 같은 소스와 Memoly 변형을 독립적으로 재빌드합니다.

JDK는 Docker와 같은 Temurin `21.0.11+10`, SDK/NDK는 버전 카탈로그를 사용합니다.
CI 구성과 캐시 정책은 [CI.md](CI.md)에 있습니다.

## 기존 Molly에서 이전

기존 앱에서 로컬 백업을 만들고 백업 파일과 암호를 별도로 보관합니다.
Memoly를 별도로 설치한 뒤 기존 복원 절차로 메시지·첨부파일·설정을 확인합니다.
이전 Molly 설정 파일명은 Memoly의 현재 secure preferences로 매핑합니다.
기기별 PIN·생체·Keystore 키는 복사하지 않으므로 새 앱에서 잠금을 설정합니다.
복원과 계정 사용을 확인하기 전에는 기존 앱을 삭제하지 않습니다.

## 아직 필요한 기기 검증

삼성 Android 17 설치, 기존 앱과 공존, 암호화 백업 복원, 실제 Keystore·생체 인증,
FCM 토큰의 서버 등록 및 백그라운드 메시지·통화 수신, 통화 인증·PiP는 실기기 확인이 필요합니다.
FCM은 upstream Firebase 프로젝트 설정에 따라 새 ID·개인 인증서에서 동작이 달라질 수 있습니다.
자체 Firebase 클라이언트 설정만 바꾸면 Signal 서버 푸시가 연결된다고 가정하지 않습니다.
필요하면 기존 WebSocket·UnifiedPush 방식의 사용 조건을 함께 확인합니다.

## SDK 없는 로컬 검증

```sh
for test in .github/scripts/test-*.py; do python3 "$test" || exit; done
for script in .github/scripts/*.sh; do bash -n "$script" || exit; done
git diff --check
```

이 검사는 거부·분기 경로의 회귀 테스트입니다. 실제 APK 빌드·서명·기기 검증 결과는 Actions와 검증 기록에서 확인합니다.
