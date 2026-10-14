# SimpleX Chat 화면 잠금 분석

분석일: 2026-10-13  
대상: `simplex-chat/simplex-chat`의 `stable` 브랜치, 커밋 `479548ee53ffb73db73841e77acbeee5a78dbbd5`  
분석 범위: Android와 공통 Kotlin 코드 중심, iOS 저장 방식 보조 확인. 소스 분석이며 실행 시간 측정이나 보안 감사는 수행하지 않았다.

## 결론

SimpleX의 자체 화면 잠금은 데이터베이스 암호를 검증하는 방식이 아니다. 별도의 앱 전용 숫자 PIN을 기기 보안 저장 기능으로 암호화해 저장한 뒤, 잠금 해제 시 복호화한 PIN과 입력한 PIN을 비교한다. 이 경로에는 Argon2id 같은 암호 기반 고비용 키 파생이 없다. 따라서 Molly의 약 3초 데이터베이스 암호 검증과 비교하면 수 초의 의도적인 계산 지연이 없는 구조다. 실제 사용자 체감 시간은 별도 측정이 필요하다.

## 1. 설정과 인증 방식

- 화면 잠금 활성화 여부는 `performLA`, 방식은 `laMode`, 대기 시간은 `laLockDelay`로 각각 저장한다.
- 방식은 시스템 인증(`SYSTEM`)과 앱 전용 PIN(`PASSCODE`)이다. Android 기본 방식은 시스템 인증이다.
- 시스템 인증은 Android BiometricPrompt로 생체 인증 또는 기기 자격 증명을 허용한다. OS 버전에 따라 허용하는 생체 인증 강도가 다르다.
- 앱 PIN은 숫자 4~16자리이며, 두 번 입력해 일치하는지 확인한 뒤 저장한다.
- 잠금 비활성화, 잠금 방식 변경, 기존 PIN 변경 시 현재 인증을 요구하는 흐름이 있다.
- 데이터베이스 암호와 앱 PIN은 서로 다른 키 별칭과 설정값으로 관리한다.

근거: [설정 기본값](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/model/SimpleXAPI.kt#L117), [인증 방식](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/usersettings/PrivacySettings.kt#L38), [Android 인증](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/androidMain/kotlin/chat/simplex/common/views/helpers/LocalAuthentication.android.kt#L18), [최소 길이](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/localauth/PasscodeView.kt#L54), [숫자 입력과 최대 길이](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/localauth/PasswordEntry.kt#L116), [PIN 확인·저장](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/localauth/SetAppPasscodeView.kt#L38), [방식 변경 인증](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/usersettings/PrivacySettings.kt#L500).

## 2. Android에서 PIN을 저장하고 검증하는 방법

1. 앱 PIN 저장에는 `appPassword` 별칭을 사용한다. DB 암호용 별칭·저장값과 구분된다.
2. Android Keystore에 AES 키를 생성한다.
3. 이 키로 PIN 문자열을 AES-GCM으로 암호화한다.
4. 암호문과 초기화 벡터(IV)는 Base64로 변환해 설정에 저장한다.
5. 인증 시 Keystore 키로 암호문을 복호화해 PIN 문자열을 얻는다.
6. 사용자가 입력한 문자열과 일반 문자열 동등 비교를 수행한다.

이는 PIN의 단방향 해시만 보관하는 방식이 아니라, 기기 Keystore 키로 복구할 수 있는 암호화 저장 방식이다. 파일에는 PIN 평문을 저장하지 않지만 인증 과정의 앱 메모리에는 PIN 문자열이 존재한다.

Keystore 키 생성 코드에는 사용자 인증 필수 설정이나 StrongBox 필수 설정이 없다. 따라서 Keystore 키를 사용할 때마다 기기 비밀번호를 요구하는 구조가 아니다. 이 소스만으로 모든 기기에서 키가 하드웨어에 보관된다고 단정할 수는 없다.

근거: [키 별칭과 암호화 저장](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/helpers/DatabaseUtils.kt#L12), [AES 키 생성](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/androidMain/kotlin/chat/simplex/common/platform/Cryptor.android.kt#L71), [PIN 복호화](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/androidMain/kotlin/chat/simplex/common/platform/Cryptor.android.kt#L20), [인증 요청에 PIN 전달](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/helpers/LocalAuthentication.kt#L41), [입력값 비교](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/localauth/LocalAuthView.kt#L26).

## 3. 잠금 시점과 즉시 잠금

- 기본 유예 시간은 30초다.
- UI 선택지는 즉시(0초), 10초, 30초, 1분, 3분, 10분이다.
- `MainActivity.onPause()`와 `onStop()`에서 앱이 가려진 시각을 기록한다.
- `onResume()`에서 이전 시각과 현재 단조 시간의 차이를 계산한다.
- 설정 시간을 지났다면 인증 상태를 미승인으로 바꾸고 인증 화면을 띄운다.
- 별도의 백그라운드 타이머가 만료되면 데이터베이스 키를 지우는 구조가 아니라, 복귀할 때 인증 필요 여부를 판정한다.
- `AppLock`은 Activity와 별도의 singleton으로 인증 상태를 유지한다. 알림으로 Activity가 재생성될 때 중복 인증을 줄이기 위한 구조다.
- 프로세스의 최초 인증 상태와 숨김 시각은 미설정 상태이므로, 잠금이 활성화되어 있으면 시작 시 인증이 필요하다.
- 0초라도 화면 회전에는 별도 예외가 있다. 회전 처리 중 잠금이 재발생하지 않도록 숨김 시각을 3초 뒤로 이동한다.
- 진행 중인 통화 화면에 대한 인증 예외도 있다. “즉시”가 모든 화면 전환에서 반드시 PIN을 요구한다는 의미는 아니다.

근거: [대기 시간 선택지](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/usersettings/PrivacySettings.kt#L471), [Android 생명주기](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/android/src/main/java/chat/simplex/app/MainActivity.kt#L70), [인증 상태와 시간 비교](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/AppLock.kt#L269), [회전 예외](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/App.kt#L262), [통화 화면 예외](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/App.kt#L210).

## 4. 데이터베이스·알림·통화와의 관계

일반 PIN 인증의 성공·실패는 화면 접근을 위한 인증 상태를 변경한다. 이 경로에는 데이터베이스 키 삭제나 채팅 엔진 종료가 없다. 따라서 일반 화면 잠금 자체는 Molly의 데이터베이스 전체 잠금과 같은 메시지 수신·통화 중단을 요구하지 않는다. 실제 백그라운드 수신은 OS와 앱의 수신 설정에 따른다.

별도 기능인 self-destruct PIN을 켜면, 지정한 다른 PIN 입력으로 데이터 삭제·재시작을 실행할 수 있다. 이는 일반 앱 PIN 잠금과 구분해야 한다. 앱 PIN과 self-destruct PIN이 같지 않도록 설정 시 검사한다.

근거: [일반 인증 결과 처리](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/AppLock.kt#L158), [일반 PIN과 self-destruct 분기](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/localauth/LocalAuthView.kt#L26), [PIN 중복 방지](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/localauth/SetAppPasscodeView.kt#L54).

## 5. Molly에 참고할 때 주의할 동작

### 인증 수단이 없으면 잠금을 끄는 분기

`AppLock.runAuthenticate()`는 인증 결과가 `Unavailable`이면 인증 승인 상태로 바꾸고 화면 잠금을 비활성화한다. 앱 PIN 조회가 null이면 인증 helper가 이 결과를 반환한다. Keystore 키가 없을 때 복호화 함수도 null을 반환하는 경로가 있다. 실제 예외에 따라 복호화에서 오류가 다시 발생하는 경우도 있으므로 모든 암호화 오류가 잠금 해제로 이어진다고 단정할 수는 없다.

복구 편의성을 위한 것으로 보이는 이 처리는 Molly에서 그대로 채택하기보다, 잠금 상태를 유지하고 복구 경로를 안내하는 정책을 정하는 것이 적절하다. 이 항목은 소스의 동작 관찰이며, 실제 공격 가능성을 검증한 결과가 아니다.

근거: [Unavailable 시 해제·비활성화](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/AppLock.kt#L171), [PIN 부재 결과](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/helpers/LocalAuthentication.kt#L48), [Keystore 키 부재](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/androidMain/kotlin/chat/simplex/common/platform/Cryptor.android.kt#L20).

### 앱 PIN의 시도 횟수 제한

살펴본 앱 PIN 입력·비교·재인증 경로에서는 실패 횟수에 따른 대기 증가나 잠금 제한을 찾지 못했다. 오입력은 오류 결과와 알림으로 처리된다. OS가 관리하는 생체 인증의 제한과 별도 앱 PIN 입력 제한은 구분해야 한다.

Molly의 앱 PIN 구현에는 오입력 제한 정책과 앱 재시작 시 해당 제한의 유지 여부를 명시하는 것이 좋다.

근거: [PIN 오입력 처리](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/localauth/LocalAuthView.kt#L35), [인증 오류 처리](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/AppLock.kt#L165).

## 6. iOS에서의 차이

iOS도 앱 PIN을 조회해 입력값과 비교한다. 저장은 Android AES-GCM 설정 저장 대신 iOS Keychain을 이용한다. 접근 정책은 `AfterFirstUnlockThisDeviceOnly`다. 이를 “암호를 평문 파일에 저장한다”거나 “매번 기기 비밀번호를 요구한다”고 해석해서는 안 된다.

근거: [iOS Keychain](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/ios/SimpleXChat/KeyChain.swift#L12), [iOS PIN 비교](https://github.com/simplex-chat/simplex-chat/blob/479548ee53ffb73db73841e77acbeee5a78dbbd5/apps/ios/Shared/Views/LocalAuth/LocalAuthView.swift#L31).

## 7. Molly 계획에 대한 판단

| 항목 | SimpleX 앱 PIN | Molly 기존 DB 암호 재검증 |
|---|---|---|
| 인증 비밀 | 별도 앱 PIN | 데이터베이스 암호 구문 |
| 저장·검증 | OS 보호 키로 저장한 PIN을 복호화·비교 | Argon2id로 키를 파생해 마스터 키 복호화 |
| 의도적인 계산 비용 | 해당 경로에 고비용 KDF 없음 | 기기에서 약 3초를 목표로 파라미터 선택 |
| 화면 잠금 중 DB | 일반 앱 잠금은 DB 종료를 요구하지 않음 | 검증만 재사용하면 DB 유지 가능, 기존 전체 잠금 재사용 시 키 삭제·서비스 종료 |
| OS 비밀번호와 분리 | 앱 PIN 모드에서 가능 | 가능 |
| 변경 범위 | 새 앱 PIN과 인증 UI·상태·복구 정책 | 기존 검증 재사용과 인증 UI·상태·암호화 설정 의존성 |

Molly에서 빠른 전용 화면 잠금을 원한다면 SimpleX처럼 **별도의 앱 PIN과 화면 잠금 상태를 두고 DB 암호화 정책은 독립적으로 유지하는 설계**가 참고할 만하다. 앞선 계획의 “데이터베이스 키를 지워 잠금” 부분을 반드시 따를 필요는 없다.

단, 속도를 위해 기존 DB 암호 구문을 그대로 복호화 가능한 앱 PIN 저장소에 복제하는 것은 권하지 않는다. 원래는 암호를 알아야 열 수 있던 DB 키의 보호를, 기기에서 저장된 암호를 복구할 수 있는 경로로 바꾸기 때문이다. 같은 암호 문자열을 양쪽에 쓰는 경우에도 이 영향을 고려해야 한다.

현재 fork의 `plan.md`는 이번 분석으로 변경하지 않았다. 전용 PIN 방식과 기존 DB 암호 검증 재사용 방식의 제품 동작 차이를 결정한 뒤 계획을 갱신할 수 있다.

