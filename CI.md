# Memoly CI 실행 구조

## 일반 검사

| 이벤트·변경 | 실행 범위 |
|---|---|
| 같은 저장소에서 열린 PR이 있는 개발 브랜치 push | push의 무거운 잡 생략, PR 검사 실행 |
| 일반 PR 소스 변경 | 대표 `prodStoreDebug` 앱 전체 테스트·lint·조립 및 모든 공통 모듈 check |
| Gradle·Manifest·SDK/의존성·workflow·flavor 전용 소스 변경 | 기존 전체 `build`와 대표 검사 집계 작업 |
| 기본 브랜치 또는 PR 없는 브랜치 push | 전체 검사 |
| 일요일 18:20 UTC 예약 실행 | 기본 브랜치 전체 검사 |
| 수동 실행 | full 기본, representative 선택 가능 |
| 루트 Markdown 및 docs만 바꾼 PR | 파이프라인 스크립트 검사, Run tests 성공 상태 유지 |

GitHub API 실패·빈 파일 목록·3,000개 이상 변경 목록은 전체 검사로 처리합니다.
rename은 이전 파일명도 검사합니다. 전체 검사에도 `ciCheck`를 포함해 집계 작업의 연결을 검증합니다.
소스 변경의 대표 검사는 화면 잠금 일부 테스트로 축소하지 않습니다.
Website/staging 회귀는 설정 변경, 기본 브랜치·예약·수동의 전체 검사에서 확인합니다.

PR concurrency는 PR 번호와 이벤트로 구분합니다. 새 커밋은 같은 PR의 이전 검사를 취소합니다.
push 검사 생략이 PR 검사를 취소하지 않으며, 태그별 release와 재현 빌드는 취소하지 않습니다.
이미 예전 workflow로 시작한 실행에는 이 설정이 소급되지 않습니다.

## 공통 모듈 보존

root `ciModulesCheck`는 앱을 제외한 모든 subproject의 `check` 태스크에 의존합니다.
새 라이브러리도 check를 제공하면 자동으로 포함되므로 앱만 검사하는 누락을 방지합니다.
`ciCheck`는 이에 전체 prodStoreDebug 앱 테스트·lint·assemble을 더합니다.
`ciReleaseCheck`는 공통 check·전체 prodStoreDebug 앱 테스트·prodStoreRelease lint·assemble을 묶습니다.
각 실행은 실제 JUnit 개수를 모듈·변형별 Actions summary에 남깁니다.

## 캐시와 메모리

`.github/actions/setup-build`에서 고정된 JDK·Android 도구와 `gradle.properties.ci`를 적용합니다.
configuration cache·daemon·Gradle 병렬 실행은 끄고 build cache는 켭니다.
JVM 6GB, worker 2개, Kotlin in-process를 공통으로 사용합니다.
메모리 사용량이 검증되기 전에는 병렬도를 높이지 않습니다.

Gradle User Home은 setup-gradle만 관리합니다.
기본 브랜치와 같은 저장소의 PR은 쓰기 가능하며, fork PR과 unsigned release는 읽기 전용입니다.
PR 캐시는 해당 PR 범위입니다. 태그의 release가 그 PR 캐시를 읽을 수 있다는 뜻은 아닙니다.
이 workflow가 기본 브랜치에 병합되고 성공 실행이 있어야 기본 브랜치 캐시가 채워집니다.
캐시가 없는 첫 실행의 속도 개선은 보장하지 않습니다.

키를 다루는 sign 잡은 setup-gradle을 실행하지 않고, configuration cache도 만들지 않습니다.

## release

`태그 → unsigned 빌드·검증 → 별도 서명 → 에뮬레이터 설치 → 초안 Release` 순서입니다.
unsigned 잡은 Gradle을 한 번 실행합니다. 서명은 같은 실행에서 생성한 artifact ID와
잡 출력의 SHA-256 및 commit/tag/variant/run 메타데이터를 확인한 뒤 진행합니다.
PR artifact를 받아 서명하지 않습니다. PR과 release는 소스 SHA·버전·변형·신뢰 범위가 다를 수 있어
두 실행의 검증을 무조건 동일하게 취급하지 않습니다.

서명 키는 sign 잡의 서명 단계에만 전달됩니다. APK는 zipalign 후 apksigner로 서명하며 재빌드하지 않습니다.
설치 잡은 Android 15 에뮬레이터에서 PackageManager의 실제 실패 코드를 기록합니다.
통과 전에는 GitHub Release에 APK를 업로드하지 않습니다. 서명 artifact는 앞 단계에서 보관됩니다.

공개한 Memoly Release의 재현성 검사는 Docker에서 독립적으로 빌드합니다.
APK의 내용은 서명 관련 파일을 제외하고 비교하며, 서명된 artifact 재사용으로 대체하지 않습니다.

## 측정 및 제한

코드·설정과 workflow 검증, 실제 CI 성공, 실제 기기 검증을 구분해 기록합니다.
캐시 생성·복원 로그와 task FROM-CACHE/UP-TO-DATE, 실제 JUnit 목록, 잡 실행 시간을 확인합니다.
반복 실행은 같은 소스와 같은 검사 프로필끼리 비교합니다. 실행 범위가 다른 잡의 시간으로
속도 개선률을 계산하지 않습니다. Android 17 삼성 기기와 실제 계정 푸시·통화는 별도 검증입니다.
