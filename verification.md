# Molly 접근 잠금 검증

기준: 2026-10-14, `feature/molly-pin-screen-lock`.

## 확정 정책

- 생체와 PIN에 공통 접근 정책을 적용한다.
- 알림의 발신자·본문 미리보기는 기존 설정을 유지한다.
- 잠긴 상태에서는 전화 거절·통화 종료만 인증 없이 허용한다.
- 기존 자동 5초/60초 정책은 즉시로 이전하고 명시적으로 선택한 시간은 유지한다.
- 모드 변경에도 선택한 시간을 유지한다. 인증 오류·등록 해제·기록 누락으로 잠금을 끄지 않는다.
- 데이터베이스 인증과 앱 인증은 독립적이다. 진행 중 통화·자동 수신 서비스는 유지한다.

## 자동 검증

- 순수 JVM 회귀 테스트: 32개 통과. 잠금 시간 경계, Activity 없이 실행되는 작업의 만료, 늦은 요청, 잠금 OFF 작업 유지, 최초 설정 중 앱 이탈, PIN 검증·오입력 제한·시간 이전을 포함한다.
- Android 프로젝트 최종 Kotlin·Java 컴파일 및 대상 단위 테스트: BUILD SUCCESSFUL. 총 43개 테스트(화면 노출 경계 테스트 3개 포함), 실패·오류 0개. QR 자동 처리 보강 후 최종 재검증도 통과했다.
- 화면 Robolectric 테스트: 3개 통과. 윈도우 연결 전 가림, 시스템 잠금 표시 플래그와 앱 잠금의 분리, 원래 숨겨진 본문 보존을 검증한다.
- 저장소 Robolectric 테스트: 8개 통과. 기록 누락·손상, 모드 유지, 권위 있는 기록과 보조 기록의 우선순위, 실제 이전 기록의 시간 변환을 검증한다.
- XML 파싱·중복 리소스·PIN 화면의 영어/한국어 리소스 참조 검사 통과.
- `git diff --check` 통과.

프로젝트 검증 환경은 JDK 21, Gradle 9.4.1, Android SDK 36.1이다. 이 작업 호스트에서는 `/tmp/molly-toolchain`에 준비한 JDK와 SDK를 사용한다. 메모리 사용량을 제한하기 위해 다음 명령으로 실행했다.

```sh
ANDROID_HOME=/tmp/molly-toolchain/android-sdk \
  /tmp/molly-toolchain/usr/lib/jvm/java-21-openjdk/bin/java -XX:-UsePerfData \
  -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
  :app:compileProdWebsiteDebugKotlin :app:compileProdWebsiteDebugJavaWithJavac \
  :app:testProdWebsiteDebugUnitTest --tests 'org.thoughtcrime.securesms.screenlock.*' \
  '-Dorg.gradle.jvmargs=-Xmx6g -XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:hashCode=3' \
  -Pkotlin.compiler.execution.strategy=in-process --max-workers=2 \
  --no-parallel --no-daemon --console=plain
```

## 기기 검증 필요

연결된 Android 기기·에뮬레이터가 없어 다음 항목은 아직 실행하지 않았다. 자동 테스트의 통과를 실제 Keystore·통화·화면 노출 검증으로 해석하지 않는다.

| 시나리오 | 기대 결과 |
|---|---|
| 생체/PIN 각각 앱 시작, 프로세스 종료 후 재진입 | 선택한 인증 요구, 본문 선행 노출 없음 |
| 즉시/5초 설정에서 홈·외부 앱·화면 꺼짐 후 복귀 | 같은 시간 정책 적용, 경계에서 인증 요구 |
| 회전·내부 화면 전환·멀티윈도 | 불필요한 인증이나 다른 창의 입력 차단 잔류 없음 |
| 잠긴 상태에서 수신 전화 화면·알림 응답·Telecom 응답 | 인증 성공 후 같은 전화가 여전히 수신 중일 때만 수락 |
| 인증 중 상대가 통화를 종료하거나 다른 전화가 도착 | 이전 응답 요청 폐기 |
| 잠긴 상태에서 알림 거절·종료, CallStyle 미지원 기기 | 인증 없이 거절·종료 가능 |
| 진행 중 음성·영상 통화에서 앱 잠금 | 통화 유지, Molly 화면·조작과 PiP 노출 보호 |
| 카메라·갤러리·QR·위치·로그·등록 화면에서 앱 복귀 | 공통 인증 요구; 다른 모듈 화면은 인증 게이트로 보호. QR 카메라·자동 처리는 잠금 중 중단 |
| 이전 알림·웨어러블 답장·읽음 요청이 잠금 후 도착 | 메시지 전송·읽음 처리 없음; 미리보기 설정은 유지 |
| 생체 등록 제거·하드웨어 사용 불가 | 잠금 유지; 허용된 기기 인증 대체 경로 확인 |
| DB 암호 입력 화면의 로그·지원 메뉴 | 앱 인증 필요; DB 인증만으로 앱 잠금을 해제하지 않음 |
| 최초 PIN 저장·PIN 변경 중 홈·뒤로·회전 | 완료되지 않은 설정 요청 폐기, 이전 자격 증명 유지 |
| PIN 오입력 후 앱 재시작·기기 재부팅 | 실패 제한 유지 |
| PIN Keystore 키·기록 소실, 백업·기기 이전 | 기존 기기의 소실은 잠금 유지, 새 기기에는 기기 전용 상태가 복원되지 않음 |

실제 통화·메시지 테스트는 사용자가 지정한 테스트 계정과 상대를 사용해야 한다.

## 디버그 APK

- `:app:assembleProdWebsiteDebug`: BUILD SUCCESSFUL.
- Android SDK apksigner 서명 검증 통과.
- 경로: `app/build/outputs/apk/prodWebsite/debug/app-prod-website-debug.apk`.
- 패키지: `im.molly.app`, 디버그 서명. 기존 설치 앱과 서명이 다르면 업데이트 설치할 수 없으므로 별도 테스트 기기·프로필에서 검증한다.
- SHA-256: `365c0b4722adca7e8303b63e1921ff48e059f9d5e4519aa1af7153ec0d7d0246`.
