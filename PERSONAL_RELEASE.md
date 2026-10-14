# 개인 서명 APK를 GitHub Actions로 배포하기

이 fork는 `prodStoreRelease`를 빌드합니다. 공식 Molly 자동 업데이트는 꺼져 있으며,
패키지는 기존과 같은 `im.molly.app`입니다. 개인 서명이므로 공식 Molly 위에 덮어쓰기
설치할 수 없습니다. 처음에는 백업·복원으로 이전해야 합니다.

## 처음 한 번 설정하기

1. Android Studio의 **Build → Generate Signed Bundle / APK → APK → Create new**에서
   개인 keystore를 만듭니다. 저장소 밖에 저장하고 유효기간은 30년으로 지정합니다.
   현재 빌드 설정은 keystore 비밀번호와 키 비밀번호가 같아야 합니다.
2. keystore와 비밀번호를 각각 안전하게 백업합니다. Git에 커밋하지 않습니다.
3. keystore를 Base64로 변환하고 fork의 **Settings → Secrets and variables → Actions**에
   다음 Repository secrets를 등록합니다. Base64는 암호화가 아니므로 변환한 파일도
   keystore처럼 취급합니다.

   | Secret | 값 |
   |---|---|
   | `SECRET_KEYSTORE` | keystore 파일의 Base64 값 |
   | `SECRET_KEYSTORE_ALIAS` | 키 별칭, 예: `molly-personal` |
   | `SECRET_KEYSTORE_PASSWORD` | keystore와 키의 공통 비밀번호 |

   Linux에서는 다음 명령으로 임시 파일을 만들 수 있습니다. 경로는 실제 경로로 바꿉니다.

   ```sh
   umask 077
   base64 -w 0 /absolute/path/molly-personal.jks > /tmp/molly-personal.jks.base64
   ```

   내용을 Secret 입력창에 넣은 후 임시 파일을 삭제합니다. 채팅이나 issue에 붙여넣지 않습니다.
4. fork의 Actions를 활성화합니다. 이 워크플로가 기본 브랜치에 있어야 **Run workflow**
   버튼이 표시됩니다. PR로 올린 경우 먼저 기본 브랜치에 병합합니다.

## 새 버전 만들기

1. PIN 기능을 포함한 커밋이 GitHub에 올라와 있는지 확인합니다.
2. 이전 개인 배포판보다 `versionCode`가 높아야 합니다. 현재 계산식은
   `canonicalVersionCode * 100 + mollyRevision + currentHotfixVersion`입니다.
   같은 upstream 버전에서 다시 배포할 때는 `app/build.gradle.kts`의
   `currentHotfixVersion`을 올리고 커밋합니다. 태그 이름만 바꿔서는 코드가 증가하지 않습니다.
3. **Actions → Tag commit for personal release → Run workflow**를 선택합니다.
4. 빌드할 브랜치를 고르고, 새 태그를 입력합니다. 첫 예시는 `v8.19.2-4-personal.1`입니다.
   기존 태그는 덮어쓰지 않습니다. `publish`를 선택하면 검증 후 자동 공개하며,
   기본값은 초안 Release입니다.
5. 성공하면 **Releases**의 해당 버전에 서명 APK와 `SHA256SUMS`가 첨부됩니다.
   테스트 보고서는 Actions artifact에도 저장됩니다. 초안은 직접 검토한 뒤 공개할 수 있습니다.

이미 만든 태그의 빌드가 실패했다면 **Personal signed APK release → Run workflow**에서
같은 태그를 지정하여 재시도합니다. 공개된 Release는 이 워크플로로 교체할 수 없습니다.
직접 `v*-personal.*` 태그를 push해도 초안 배포가 실행됩니다. 공식 upstream 태그는
개인 배포를 시작하지 않습니다.

## 배포 전 자동 확인

- 서명 Secret 세 개가 모두 있어야 진행합니다. unsigned APK를 배포하지 않습니다.
- 고정된 JDK 21과 프로젝트에 명시된 Android SDK/NDK 버전을 설치합니다.
- 화면 잠금 테스트를 실행한 뒤 개인 키로 release APK를 서명합니다.
- APK 서명, 실제 서명 인증서, `im.molly.app` 패키지, debuggable 여부를 확인합니다.
- 최근 공개된 개인 Release가 있으면 인증서가 같고 `versionCode`가 증가했는지 확인합니다.
  초안·로컬 설치 버전과의 비교는 하지 않으므로 그 경우에는 직접 버전 증가를 확인합니다.
- APK와 SHA-256 체크섬만 배포합니다. 임시 keystore는 항상 삭제하고 Gradle의
  configuration cache는 사용하지 않습니다.
- 실제 기기의 Keystore·생체 인증·통화·PiP 검증은 별도로 필요합니다.

## 기존 공식 Molly에서 이전

기존 앱에서 로컬 백업을 만들고 백업 파일·암호를 별도로 보관합니다. 암호 확인 기능을
사용하고, 기존 앱을 삭제하기 전에 별도 기기/프로필에서 개인 release APK에 복원해 봅니다.
인증·설정은 일부 다시 구성해야 할 수 있습니다. 기존 앱 삭제나 재등록은 복원 가능성을
확인한 뒤 진행합니다. 이후 개인 배포판끼리의 업데이트에는 같은 keystore를 계속 씁니다.

## 로컬 CI 구성 확인

```sh
python3 .github/scripts/test-verify-personal-apk.py
bash -n .github/scripts/verify-personal-apk.sh
git diff --check
```

이 확인은 업그레이드 거절 조건의 모의 테스트입니다. GitHub runner에서 실제 release
빌드·서명이 성공했다는 뜻은 아닙니다. 실제 배포에는 위 세 Secret 등록이 필요합니다.
