# TODOs

2026-11-10 전체 점검에서 **고치지 않고 남긴 이슈**를 작업 단위로 쪼갠 목록이다.
사람과 코딩 에이전트(모델 무관)가 항목 하나를 골라 그대로 진행할 수 있게 썼다.

- 줄 번호는 2026-11-10 작업 트리 기준이다. 어긋나면 **심볼 이름으로 다시 찾는다.**
- 끝낸 항목은 `[ ]` → `[x]` 로 바꾸고, 같은 커밋에 포함한다.
- 새로 발견한 이슈는 같은 형식으로 해당 묶음 끝에 추가한다.

## 0. 작업 규칙 (모든 항목 공통)

1. 먼저 루트 `AGENTS.md` 를 읽는다. 프론트 작업이면 `frontend/AGENTS.md` 도 읽는다.
2. **항목 하나 = 브랜치 하나 = PR 하나.** 여러 항목을 한 PR 에 섞지 않는다. `main` 에 직접 커밋하지 않는다.
3. **`main` 머지는 곧 운영 배포다** (`.github/workflows/deploy.yml`). 백엔드가 재시작되고 전 직원 세션이 끊긴다.
4. 표시의 뜻:
   - `[결정 필요]` — 정책·설계를 사용자가 정해야 한다. **정해지기 전에는 구현하지 않는다.** 선택지를 정리해 질문만 한다.
   - `[운영 영향]` — 배포 스크립트·워크플로·컨테이너 구성을 바꾼다. 사용자 승인 없이 머지하지 않는다.
   - `선행:` — 적힌 항목이 먼저 머지돼 있어야 한다.
5. 버그 수정은 **실패하는 테스트를 먼저** 쓴다. 테스트가 빨간 것을 확인한 뒤 고친다.
6. API 를 바꾸면 순서는 `openapi.yml` → 백엔드 → `pnpm --dir frontend gen:api` → 프론트. `frontend/src/api/schema.d.ts` 는 손으로 고치지 않는다.
7. 시간은 주입된 `Clock` 으로만 읽는다. `LocalDate.now()` 같은 무인자 호출을 새로 만들지 않는다. 테스트는 고정 시각을 쓴다.
8. 이미 적용된 Flyway 마이그레이션(`V1`~`V16`)은 고치지 않는다. 스키마 변경은 `V17` 부터 새 파일로 한다.
9. 이 저장소의 코드 취향:
   - boolean 플래그 인자를 만들지 않는다. 이름 있는 팩토리/메서드로 나눈다.
   - 프로덕션 코드 주석은 최소로 한다. "왜"는 테스트 이름과 테스트 본문에 남긴다.
   - 커밋·PR 본문에 `@Transactional` 같은 애너테이션을 쓸 때는 반드시 백틱으로 감싼다 (GitHub 멘션 방지).
10. `.env` 내용, 비밀번호, 서버 주소, 계정 ID 를 코드·문서·커밋에 옮겨 적지 않는다.
11. 마지막 검증 (실행하지 못한 것은 "실행 못 함"이라고 보고한다. 스킵된 테스트를 통과로 보고하지 않는다):

    ```bash
    ./gradlew check
    ```

    ```bash
    pnpm --dir frontend lint
    ```

    ```bash
    pnpm --dir frontend build
    ```

    Gradle 은 Java 21 이 필요하다. 셸 기본 JDK 가 다르면 `JAVA_HOME` 을 Java 21 로 지정한다.
    `*MySqlIntegrationTest` 는 Docker 가 없으면 스킵된다.

## 권장 순서

1. **A (연차 타임존)** → **B (퇴사자 차단)**: 지금 운영에서 틀리게 동작하는 두 가지.
2. **C (로그인·세션)**, **F (배포 안전성)**.
3. 나머지는 독립적이라 순서 무관. 각 항목의 `선행:` 만 지킨다.

---

## A. 연차의 "오늘"을 직원 타임존 기준으로

**문제.** `domain/annual_leave/AnnualLeave.java` 35·44·48행이 `LocalDate.now()` 를 쓴다.
운영 컨테이너에는 TZ 설정이 없어 JVM 기본 시간대가 UTC 다.
그래서 한국 시간 00:00~08:59 에는 "오늘"이 어제로 계산된다.
과거 날짜 연차가 통과하고, 팀의 사전 신청 일수 기준이 하루 어긋나고, 잔여 연차 목록에 오늘 연차가 섞인다.

**합의된 방향.** "오늘"은 직원 타임존 기준이다. 도메인은 `LocalDate today` 를 인자로 받는다.
서비스가 `Clock` 을 주입받아 `LocalDate.now(clock.withZone(employee.getZoneId()))` 로 계산한다
(`service/commute/CommuteHistoryService.java` 가 이미 쓰는 방식).

각 단계는 빌드가 깨지지 않게 "새 메서드 추가 → 호출부 전환 → 옛 메서드 삭제" 순서로 나눴다.

### [ ] A-1 · `AnnualLeave` 에 `today` 를 받는 메서드 추가
- 파일: `src/main/java/com/company/officecommute/domain/annual_leave/AnnualLeave.java`,
  `src/test/java/com/company/officecommute/domain/annual_leave/AnnualLeaveTest.java`
- 할 일:
  1. 정적 팩토리 `AnnualLeave.enroll(Long employeeId, LocalDate wantedDate, LocalDate today)` 추가. `wantedDate.isBefore(today)` 면 `AnnualLeavePastDateException`.
  2. `isNotEnoughForEnroll(int annualLeaveCriteria, LocalDate today)` 추가.
  3. `isRemain(LocalDate today)` 추가.
  4. 기존 생성자와 무인자 버전은 **이 단계에서는 그대로 둔다.**
- 테스트: 고정 날짜(예: `LocalDate.of(2026, 3, 10)`)로 경계 3개 — 어제(거부), 오늘(허용), 기준일 하루 전(기준 미달).
- 완료 기준: 새 테스트 통과, 기존 테스트 변경 없음.
- 검증: `./gradlew test --tests '*AnnualLeaveTest'`

### [ ] A-2 · `today` 를 도메인 호출 사슬로 전달
- 선행: A-1
- 파일: `domain/annual_leave/AnnualLeaves.java`(34행 근처), `domain/team/Team.java`(64·68행 `isNotEnoughCriteria`),
  `domain/annual_leave/AnnualLeaveEnrollment.java`, `domain/employee/Employee.java`(215행 `enrollAnnualLeave`)
- 할 일: 위 메서드마다 `LocalDate today` 를 받는 오버로드를 추가하고, 안에서는 A-1 의 새 메서드만 부른다.
  `Employee.enrollAnnualLeave(wantedDates, existingAnnualLeaves, today)` 는 `AnnualLeave.enroll(...)` 팩토리를 쓴다.
- 완료 기준: `today` 오버로드 경로 안에 `LocalDate.now()` 호출이 하나도 없다. 기존 오버로드는 아직 남아 있다.
- 검증: `./gradlew test --tests '*annual_leave*' --tests '*TeamTest' --tests '*EmployeeTest'`

### [ ] A-3 · `AnnualLeaveService` 가 `Clock` 으로 `today` 계산
- 선행: A-2
- 파일: `service/annual_leave/AnnualLeaveService.java`, `src/test/java/.../service/annual_leave/AnnualLeaveServiceTest.java`
- 할 일:
  1. 생성자에 `Clock clock` 추가.
  2. `enrollAnnualLeave`: `LocalDate today = LocalDate.now(clock.withZone(employee.getZoneId()))` 를 계산해 A-2 의 오버로드로 넘긴다.
  3. `getRemainingAnnualLeaves`: 직원을 조회해 같은 방식으로 `today` 를 구하고 `isRemain(today)` 로 필터한다. 직원이 없으면 `EmployeeNotFoundException`.
- 테스트(먼저 작성): 시계를 `2026-03-09T15:30:00Z`(= 한국 3월 10일 00:30)로 고정한다.
  타임존 `Asia/Seoul` 직원이 3월 9일 연차를 신청하면 `AnnualLeavePastDateException`, 3월 10일은 통과.
- 완료 기준: 새 테스트가 수정 전에는 실패하고 수정 후 통과한다.
- 검증: `./gradlew test --tests '*AnnualLeaveService*'`

### [ ] A-4 · 연차·팀 테스트의 `LocalDate.now()` 를 고정 날짜로 전환
- 선행: A-3
- 파일(파일당 커밋 하나):
  `domain/annual_leave/AnnualLeaveTest.java`(5곳), `AnnualLeavesTest.java`(5곳), `AnnualLeaveEnrollmentTest.java`(4곳),
  `service/annual_leave/AnnualLeaveServiceTest.java`(3곳), `domain/team/TeamTest.java`(2곳) — 모두 `src/test/java/com/company/officecommute/` 아래.
- 할 일: `LocalDate.now().plusDays(n)` 픽스처를 고정 `today` + `today.plusDays(n)` 로 바꾸고, `today` 를 받는 오버로드를 호출한다.
- 완료 기준: 위 5개 파일에서 `grep -n 'LocalDate.now()'` 결과가 0건.

### [ ] A-5 · `today` 없는 옛 메서드·생성자 검증 제거
- 선행: A-4
- 파일: A-1, A-2 에서 건드린 프로덕션 파일 전부
- 할 일: 무인자 `isRemain()`, `isNotEnoughForEnroll(int)`, `today` 없는 `isNotEnoughCriteria`·`enroll`·`enrollAnnualLeave` 오버로드를 삭제한다.
  `AnnualLeave` 공개 생성자의 `LocalDate.now()` 검사를 지운다 (검증은 `enroll` 팩토리가 담당).
- 완료 기준:

  ```bash
  grep -rn 'LocalDate.now()' src/main/java
  ```

  결과가 0건이고 `./gradlew check` 통과.

### [ ] A-6 · `[결정 필요]` 직원 등록의 `@Past` / `@PastOrPresent` 기준 시간대
- 파일: `dto/employee/request/EmployeeSaveRequest.java` 24·28행
- 문제: Bean Validation 이 JVM 기본 시간대(운영 UTC)로 "오늘"을 본다. 한국 시간 09:00 전에는 오늘 입사일이 "미래"로 거부된다.
- 선택지:
  - (a) Bean Validation `ClockProvider` 빈을 등록해 `Asia/Seoul` 고정.
  - (b) 애너테이션을 지우고 서비스에서 요청의 `timezone` 기준으로 검증.
  - (c) 컨테이너에 `TZ=Asia/Seoul` 지정 — `[운영 영향]`, JVM 기본 시간대에 기대는 다른 코드에 영향이 없는지 확인이 필요하다.
- 결정 후: 고정 시계로 경계 테스트를 추가한다 (`EmployeeControllerTest` 는 실제 시계를 쓰므로 방식에 맞는 위치를 고른다).

---

## B. 퇴사일이 지나면 계정 차단

**문제.** `service/employee/EmployeeService.java` 91행 주석과 `openapi.yml` 은 퇴사일 지정을 "계정 비활성화"라고 설명한다.
하지만 `workEndDate` 를 읽는 곳은 초과근무 집계 쿼리(`EmployeeRepository.java` 30행)뿐이다.
로그인(`EmployeeService.authenticate`), `auth/AuthInterceptor.java`, `GET /api/auth/me` 어디도 확인하지 않는다.
퇴사자가 계속 로그인하고 출근을 찍을 수 있다. 프론트에는 퇴사 처리 화면도 없다.

### [ ] B-0 · `[결정 필요]` 차단 정책
정해야 할 것:
1. 차단 시작 시점 — 제안: 퇴사일 **다음 날**부터 (퇴사일 당일은 근무일). 기준 시간대는 직원 타임존.
2. 차단 범위 — 제안: 새 로그인 거부 + 이미 로그인된 세션도 다음 요청부터 401.
3. 로그인 거부 메시지 — 제안: 일반 로그인 실패와 같은 문구 (C-1 과 일관).
4. 퇴사일을 과거 날짜로 지정하는 것을 허용할지 (현재는 입사일 이전만 거부).

### [ ] B-1 · `Employee.isRetiredAsOf(LocalDate today)` 추가
- 선행: B-0
- 파일: `domain/employee/Employee.java`, `src/test/java/.../domain/employee/EmployeeTest.java`
- 할 일: `workEndDate != null && workEndDate.isBefore(today)` (B-0 의 1번 결정에 맞춘다).
- 테스트: 퇴사일 없음 / 퇴사일 = 오늘 / 퇴사일 = 어제.
- 메모: `EmployeeBuilder.withWorkEndDate` 는 미사용이라 2026-11-10 에 지웠다. 필요하면 다시 추가하거나 `changeWorkEndDate` 를 쓴다.

### [ ] B-2 · 퇴사자 로그인 거부
- 선행: B-1
- 파일: `service/employee/EmployeeService.java`(144행 `authenticate`), `EmployeeServiceTest`, `controller/auth/AuthControllerTest`
- 할 일: 서비스에 `Clock` 이 없으면 주입한다. 비밀번호 확인 **뒤에** `isRetiredAsOf(today)` 면 `AuthenticationFailedException`.
- 완료 기준: 고정 시계 테스트 — 퇴사일 다음 날 로그인 401 `UNAUTHORIZED`, 퇴사일 당일 로그인 성공.

### [ ] B-3 · 로그인된 퇴사자의 요청 차단
- 선행: B-1
- 파일: `repository/employee/EmployeeRepository.java`(59행 `findRoleById`), `auth/AuthInterceptor.java`,
  `src/test/java/.../auth/SessionRoleFixture.java`
- 할 일: 인터셉터가 요청마다 이미 DB 를 한 번 읽는다 (`findRoleById`). 이 조회를 역할·퇴사일·타임존을 함께 돌려주는 프로젝션으로 바꾼다 (쿼리 수는 그대로 1회).
  퇴사자면 `AuthenticationFailedException`. `AuthInterceptor` 에 `Clock` 을 넘기도록 `config/WebConfig.java` 를 고친다.
- 주의: `SessionRoleFixture.stubSessionRoles` 가 `findRoleById` 를 스텁한다. 컨트롤러 테스트 8개가 이것에 의존하므로 픽스처를 같이 고친다.
- 테스트: 인터셉터 전용 테스트 클래스는 없다. 컨트롤러 테스트(예: `TeamControllerTest`)에 사례를 추가한다.
- 완료 기준: 세션이 있는 퇴사자가 `GET /api/team` 을 부르면 401.

### [ ] B-4 · `GET /api/auth/me` 도 퇴사자에게 401
- 선행: B-1
- 파일: `controller/auth/AuthController.java`(40행), `EmployeeService.getCurrentUser`
- 배경: `/api/auth/**` 는 `WebConfig.java` 45행에서 인터셉터 제외라 B-3 이 적용되지 않는다.
- 할 일: `getCurrentUser` 에서 퇴사자면 `AuthenticationFailedException`. C-4 와 같은 메서드를 건드리므로 함께 해도 된다.

### [ ] B-5 · `openapi.yml` 설명 정리
- 선행: B-2, B-3, B-4
- 할 일: 로그인·`/api/auth/me`·퇴사일 지정 API 설명에 "퇴사일 다음 날부터 401" 을 명시한다. 그 뒤 `pnpm --dir frontend gen:api`.

### [ ] B-6 · 프론트 훅 `useChangeRetirement`
- 파일: `frontend/src/hooks/useEmployees.ts`
- 할 일: `PUT /api/employee/{employeeId}/retirement` (본문 `{ workEndDate: string | null }`) 호출 훅.
  같은 파일의 `useAssignCorrectionApprover` 를 그대로 본뜬다. 성공 시 같은 파일의 `KEY` 쿼리를 무효화한다.

### [ ] B-7 · 직원 목록에 퇴사일 표시
- 파일: `frontend/src/pages/EmployeesPage.tsx`(141~150행 표 머리)
- 할 일: "퇴사일" 열을 추가한다. 응답에서 `workEndDate` 는 **없으면 필드가 생략**된다 (`null` 이 아니다). 없으면 `—`.
  `TableStateRow` 의 `colSpan` 을 열 수에 맞춘다.

### [ ] B-8 · 퇴사 처리·취소 UI
- 선행: B-6, B-7
- 파일: `frontend/src/pages/EmployeesPage.tsx`
- 할 일: 행마다 "퇴사 처리" 버튼 → 날짜 입력 모달 → B-6 훅 호출. 이미 퇴사일이 있으면 "퇴사 취소"(`null` 전송).
  오류 처리: `INVALID_RETIREMENT_DATE`(400)는 날짜 입력 칸에, `PENDING_CORRECTION_EXISTS`(409)는 알림으로.
- 검증: 브라우저에서 MANAGER 로 지정·취소·오류 두 가지를 확인한다. MEMBER 는 `/employees` 에 접근할 수 없어야 한다.

---

## C. 로그인·세션

### [ ] C-1 · 로그인 실패 메시지 통일
- 파일: `service/employee/EmployeeService.java` 144~149행, 관련 테스트
- 문제: 없는 이메일은 "존재하지 않는 이메일입니다.", 틀린 비밀번호는 "비밀번호가 일치하지 않습니다." 를 돌려준다. 가입된 이메일을 외부에서 알아낼 수 있다.
- 할 일: 두 경우 모두 같은 문구(예: "이메일 또는 비밀번호가 올바르지 않습니다.")로 통일한다. 옛 문구를 단언하는 테스트를 고친다.
- 완료 기준: 두 경우의 응답 본문이 바이트 단위로 같다는 테스트가 있다.

### [ ] C-2 · 없는 이메일에도 해시 비교를 수행
- 선행: C-1
- 할 일: 이메일이 없을 때도 더미 해시에 `passwordEncoder.matches` 를 한 번 실행해 응답 시간 차이를 줄인다.
- 완료 기준: 없는 이메일 로그인 시 `passwordEncoder.matches` 가 1회 호출됨을 검증하는 단위 테스트.

### [ ] C-3 · `[운영 영향]` nginx 로그인 요청 제한
- 파일: `deploy/nginx.conf`(15·36행의 `location /api/` 두 곳), `deploy/nginx.local.conf`
- 할 일: `limit_req_zone` 을 파일 최상단에 선언하고 (`conf.d` 파일은 `http` 블록 안에 포함된다),
  `location = /api/auth/login` 블록을 두 `server` 에 각각 추가해 `limit_req` 를 건다. 기존 `/api/` 블록의 `proxy_*` 설정을 그대로 복사한다.
- 확인할 것: 사무실이 공용 IP 하나로 나가면 직원 전체가 한 키로 묶인다. 한도는 사용자와 정한다.
- 검증: 로컬 풀스택(`docker-compose.yml --profile full`)에서 연속 로그인 실패 시 429 가 나오는지 본다.

### [ ] C-4 · `/api/auth/me` — 세션의 직원이 DB 에 없으면 401
- 파일: `EmployeeService.getCurrentUser`(137행 근처), `controller/auth/AuthControllerTest`
- 문제: 지금은 `EmployeeNotFoundException` → 404 다. 프론트(`frontend/src/auth/AuthContext.tsx` 23행)는 401 만 로그아웃으로 본다.
- 할 일: 이 경로에서는 `AuthenticationFailedException` 을 던진다.
  `AuthControllerTest` 에 쓰이지 않는 `EmployeeRepository` 목·스텁이 있으니 같이 정리한다.

### [ ] C-5 · 프론트 — 사용 중 세션이 만료되면 로그인 화면으로
- 파일: `frontend/src/main.tsx` 18~24행(`refreshRoleOnForbidden`)
- 문제: 부팅 때만 `/api/auth/me` 를 확인한다. 사용 중 401 이 나면 오류 알림만 뜨고 화면은 그대로다 (배포 때마다 전원 세션이 끊기므로 자주 발생).
- 할 일: 같은 `onError` 에서 `ApiError` 이고 `status === 401` 이면 `queryClient.setQueryData(['auth', 'me'], null)`.
  라우트 가드가 로그인 화면으로 보낸다. 로그인 요청 자체의 401(비밀번호 틀림)에는 영향이 없는지 확인한다.
- 검증: 로그인 → 백엔드 재시작 → 아무 버튼 클릭 → `/login` 이동, 재로그인 후 원래 화면 복귀.

### [ ] C-6 · `[결정 필요]` 비밀번호 변경 API
- 배경: 비밀번호를 바꾸는 수단이 없다. 관리자 해시는 Flyway placeholder(`V11`)로 한 번만 주입되고 재실행되지 않는다.
- 정할 것: 본인 변경만 / 관리자 초기화 포함, 변경 후 다른 세션 처리, 비밀번호 규칙.
- 결정 후 쪼갤 단위:
  1. `openapi.yml` 에 `PUT /api/auth/password` 추가 + `gen:api`
  2. `Employee.changePassword` 도메인 메서드 + 단위 테스트
  3. 서비스·컨트롤러 + 컨트롤러 테스트 (현재 비밀번호 불일치 시 실패)
  4. 프론트 훅 + 화면

### [ ] C-7 · `AuthContext` 의 `ready` 상태 제거
- 파일: `frontend/src/auth/AuthContext.tsx` 29·38·62행
- 할 일: `ready` state 를 지우고 `isLoading: meQuery.isLoading` 으로 바꾼다 (초기 로딩 때만 true 인 값이라 동작이 같다).
- 검증: 새로고침 시 로그인 화면이 깜빡이지 않는지, 로그인·로그아웃이 그대로인지 브라우저로 확인.

---

## D. 초과근무·보고서

### [ ] D-1 · 초과근무 화면에 휴일근로 열 표시
- 파일: `frontend/src/pages/OvertimePage.tsx` 70~96행
- 문제: API 는 `overTimeMinutes`, `holidayWithin8HoursMinutes`, `holidayExceeding8HoursMinutes` 를 주는데 화면은 첫 번째만 보여 준다.
  휴일에만 일한 직원은 "0시간"으로 보인다. 엑셀 보고서에는 세 값이 모두 들어간다.
- 할 일: 열 두 개를 추가하고 `TableStateRow` 의 `colSpan` 을 3 → 5 로 바꾼다.
  열 이름은 `service/overtime/OverTimeReportService.java` 의 엑셀 머리글과 같은 용어를 쓴다.
- 검증: 휴일 근무 데이터가 있는 달을 브라우저에서 확인한다.

### [ ] D-2 · `[결정 필요]` 끝나지 않은 달의 수동 발송 요청
- 파일: `controller/overtime/OverTimeController.java` 63~67행, `service/report/OverTimeReportDispatchService.java` 141행
- 문제: `POST /api/overtime/report/dispatch?yearMonth=` 가 미래 달을 포함한 아무 달이나 받아 `FAILED: MONTH_NOT_CLOSED` 발송 이력을 만든다.
- 제안: 아직 끝나지 않은 달은 이력을 만들지 않고 409 로 거절한다 (기존 코드 `CLOSING_MONTH_NOT_ENDED` 재사용 여부를 결정).
- 결정 후 순서: `openapi.yml` → 서비스 가드 + 고정 시계 테스트 → `gen:api`.

### [ ] D-3 · `dispatchAndDescribe` 의 500 가능성 조사
- 파일: `service/report/OverTimeReportDispatchService.java` 141~148행
- 문제: `dispatch(target)` 이 이력을 만들지 못한 채 돌아오면 `IllegalStateException` → 500 `UNEXPECTED_DOMAIN_VIOLATION`.
- 할 일(조사만): `dispatch` 안에서 이력 없이 반환되는 경로가 있는지 추적하고 (선점 실패, 동시 요청 등),
  재현 테스트를 쓸 수 있으면 쓴다. 결과를 이 항목 아래에 적는다. 수정은 별도 항목으로 만든다.

---

## E. 중복 판정·조회

### [ ] E-1 · 팀 등록 — 모든 DB 오류를 "중복"으로 처리하지 않기
- 파일: `domain/team/Team.java`(20행), `service/team/TeamService.java`(38~40행), `TeamServiceTest`
- 문제: `catch (DataIntegrityViolationException)` 가 원인과 무관하게 `TeamAlreadyExistsException` 을 던진다. `AGENTS.md` 규칙 위반이다.
- 할 일:
  1. `Team` 의 `@Column(unique = true)` 를 `@Table(uniqueConstraints = @UniqueConstraint(name = "uk_team_name", columnNames = "name"))` 로 바꾼다.
     이름은 `V2__team_constraints.sql` 과 같아서 **마이그레이션이 필요 없다.**
  2. `catch` 에서 `DatabaseConstraintMatcher.matches(e, "uk_team_name")` 일 때만 중복으로 바꾸고, 아니면 `throw e`.
     `service/annual_leave/AnnualLeaveService.java` 의 `saveAnnualLeaves` 가 본보기다.
- 테스트: 제약 이름이 다른 `DataIntegrityViolationException` 은 그대로 전파된다.

### [ ] E-2 · 직원 등록 — 같은 문제
- 파일: `domain/employee/Employee.java`(60·63행), `service/employee/EmployeeService.java`(74~79행)
- 할 일: E-1 과 같은 방식. 제약 이름은 `uk_employee_code`, `uk_employee_email` (`V1` 21·22행).
  어느 제약인지에 따라 `ofEmployeeCode` / `ofEmail` 을 고르고, 둘 다 아니면 `throw e`.

### [ ] E-3 · 이름 길이 검증 추가
- 파일: `dto/employee/request/EmployeeSaveRequest.java`, `dto/team/request/` 의 팀 등록 요청, `openapi.yml`
- 문제: 컬럼은 `VARCHAR(255)` 인데 `@Size(max)` 가 없다. 300자 이름을 보내면 DB 오류가 "이미 존재" 409 로 나간다.
- 할 일: `name`, `teamName`, `managerName`, `email` 에 `@Size(max = 255)` 와 메시지를 붙인다.
  `openapi.yml` 에 `maxLength: 255` → `gen:api`. 400 `VALIDATION_ERROR` 를 확인하는 컨트롤러 테스트 추가.

### [ ] E-4 · 월별 출퇴근 목록 정렬 보장
- 파일: `repository/commute/CommuteHistoryRepository.java` 29행, `service/commute/CommuteHistoryService.java` 161행
- 문제: `findAllByEmployeeIdAndWorkDateBetween` 에 `ORDER BY` 가 없어 순서가 인덱스에 달려 있다.
- 할 일: 메서드 이름을 `findAllByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc` 로 바꾸고 호출부를 고친다.
  `(employee_id, work_date)` 가 유니크라 `workDate` 만으로 순서가 정해진다.
- 테스트: 날짜 역순으로 저장한 뒤 조회 결과가 오름차순인지 확인하는 리포지토리 테스트.

### [ ] E-5 · 연차인 날 출근 시 오류 문구
- 파일: `service/commute/CommuteHistoryService.java` 82행, `domain/commute/DuplicateWorkOnDateException.java`, `frontend/src/lib/errorMessages.ts` 33행
- 문제: 연차로 등록된 날에 출근을 누르면 화면에 "오늘은 이미 출근을 기록했습니다." 가 뜬다. 실제 원인은 연차다.
- 할 일: 82행에서 기존 기록이 연차인지 구분해 서버 메시지를 다르게 만든다 (예외에 이름 있는 팩토리 추가, boolean 인자 금지).
  프론트 전역 문구에서 `DUPLICATE_WORK` 를 지워 서버 메시지가 그대로 보이게 한다.
  서버 메시지 끝의 날짜 표기(`: 2026-03-10`)가 사용자에게 자연스러운지도 확인한다.

---

## F. 배포·CI — 전부 `[운영 영향]`

수정 전에 `docs/DEPLOYMENT.md`, `docs/CD_RUNBOOK.md`, `.github/workflows/deploy.yml`, `deploy/remote-deploy.sh` 를 읽는다.
셸 스크립트는 `bash -n <file>` 과 (설치돼 있으면) `shellcheck` 로 확인한다. 운영 서버에서 직접 실험하지 않는다.

### [ ] F-1 · `remote-deploy.sh` — 검증을 덮어쓰기보다 먼저
- 파일: `deploy/remote-deploy.sh` 90~131행
- 문제: 운영 중인 compose·nginx 설정과 `.env` 의 `APP_IMAGE` 를 먼저 덮어쓴 뒤에 `compose config --quiet`(131행)로 검증한다.
  검증이 실패하면 서버에는 깨진 설정이 남고, 이후 컨테이너가 재시작되면 그 설정으로 뜬다.
- 할 일: 릴리스 디렉터리의 파일과 임시 `.env.next` 로 먼저 `docker compose --env-file … -f … config --quiet` 를 돌리고, 통과한 뒤에만 설치한다.

### [ ] F-2 · `remote-deploy.sh` — 이미지 pull 을 덮어쓰기보다 먼저
- 선행: F-1
- 파일: 같은 파일 141~145행
- 할 일: ECR 로그인과 `docker pull "$image_uri"` 를 설정 설치 앞으로 옮긴다. pull 이 실패하면 서버 파일은 그대로여야 한다.

### [ ] F-3 · `remote-deploy.sh` — 백엔드 기동 전 실패 시 설정 복원
- 선행: F-2
- 할 일: 설치 이후부터 `up -d app`(148행) 전까지 실패하면 `backup_dir` 의 compose·nginx·`.env` 를 되돌리는 처리를 넣는다.
  **`up -d app` 이후는 자동 복원하지 않는다** — Flyway 마이그레이션은 되돌릴 수 없다는 기존 방침(168행 메시지)을 유지한다.

### [ ] F-4 · `[결정 필요]` nginx 컨테이너 정의 변경이 반영되지 않음
- 파일: `deploy/remote-deploy.sh` 148행 (`up -d app` 만 실행)
- 문제: `docker-compose.prod.yml` 의 nginx 서비스(이미지 버전·포트·볼륨)를 바꿔도 운영에 적용되지 않는다. `nginx.conf` 내용 변경은 reload 로 적용된다.
- 선택지: (a) 정의가 달라졌을 때만 nginx 재생성 (수 초 중단), (b) 수동 절차로 두고 런북에 명시.

### [ ] F-5 · 백업·릴리스·이미지 보존 개수 제한
- 파일: `deploy/remote-deploy.sh` 끝부분
- 문제: `backups/`, `releases/`, 도커 이미지가 배포마다 쌓이기만 한다. 각 백업에는 `.env` 사본도 들어 있다 (80행).
- 할 일: 배포 성공 후 최근 N개(제안 5)만 남기고 삭제한다. 삭제 대상 경로가 `$deploy_root/backups/`·`$deploy_root/releases/` 아래인지 검사한 뒤 지운다.
  현재 릴리스와 직전 릴리스는 항상 남긴다. 이미지는 현재·직전 태그를 제외하고 정리한다.

### [ ] F-6 · `ssm-bootstrap.sh` — `exec` 때문에 정리 trap 이 실행되지 않음
- 파일: `deploy/ssm-bootstrap.sh` 48~54행(trap), 99행(`exec`)
- 문제: `exec` 는 셸을 교체하므로 `EXIT` trap 이 돌지 않는다. `incoming/release-<sha>.tar.gz` 가 배포마다 남는다.
- 할 일: `exec` 직전에 `rm -f -- "$artifact_path"` 를 명시적으로 실행하거나, `exec` 를 빼고 종료 코드를 그대로 전달한다.

### [ ] F-7 · `ssm-bootstrap.sh` — 아카이브 경로 검사가 건너뛰어질 수 있음
- 파일: `deploy/ssm-bootstrap.sh` 68행
- 문제: `tar -tzf … | grep -Eq …` 에서 `grep -q` 가 먼저 끝나면 `tar` 가 SIGPIPE 로 죽는다. `pipefail` 때문에 파이프 전체가 실패로 평가돼 "위험 경로 발견" 분기를 타지 않는다.
- 할 일: 목록을 변수나 임시 파일에 먼저 받고 (`tar` 실패 시 중단), 그 결과에 `grep` 을 건다.
- 위험도는 낮다 (아카이브는 체크섬 검증을 통과한 것).

### [ ] F-8 · 수동 배포 시 해당 커밋의 CI 성공 확인
- 파일: `.github/workflows/deploy.yml` 22~28행
- 문제: `workflow_dispatch` 는 CI 결과를 보지 않고 `main` HEAD 를 배포한다.
- 할 일: 수동 실행일 때 해당 SHA 의 CI 실행이 `success` 인지 조회해 아니면 실패시키는 단계를 맨 앞에 추가한다. `permissions` 에 `actions: read` 가 필요하다.

### [ ] F-9 · `[결정 필요]` 문서만 바뀐 푸시는 배포하지 않기
- 파일: `.github/workflows/ci.yml` 3~6행
- 문제: `main` 푸시마다 (문서만 고쳐도) 백엔드가 재시작돼 전 직원이 로그아웃된다.
- 제안: `on.push` 에만 `paths-ignore` (`**.md`, `docs/**`, `src/test/docs/**`)를 건다. CI 가 돌지 않으면 `workflow_run` 배포도 일어나지 않는다.
  `pull_request` 트리거는 건드리지 않는다 — 잡 이름이 브랜치 보호의 필수 체크다.
- 확인할 것: `docs/` 와 함께 코드가 바뀐 푸시는 정상적으로 CI 가 도는지.

### [ ] F-10 · 배포 워크플로 — 이미지가 이미 있으면 jar 빌드 생략
- 파일: `.github/workflows/deploy.yml` 71~72행, 101~122행
- 할 일: `./gradlew bootJar` 를 ECR 에 이미지가 없을 때의 분기 안으로 옮긴다. 재실행 시 몇 분이 줄어든다.
- 우선순위 낮음.

### [ ] F-11 · `./gradlew build` 가 jar 를 두 개 만들어 Docker 빌드가 깨짐
- 파일: `build.gradle`, `Dockerfile` 7행 (`COPY build/libs/*.jar app.jar`)
- 문제: `build` 는 실행 jar 와 `-plain.jar` 를 함께 만든다. 그 상태에서 `docker build` 하면 `COPY` 가 실패한다 (`bootJar` 만 쓰면 괜찮다).
- 할 일: `build.gradle` 에 `tasks.named('jar') { enabled = false }` 추가.
- 완료 기준: `./gradlew clean build` 후 `ls build/libs` 에 파일이 하나.

### [ ] F-12 · 환경 변수 문서와 실제 구성 맞추기
- 파일: `.env.example`, `docs/DEPLOYMENT.md`, `deploy/docker-compose.prod.yml`, `deploy/docker-compose.mysql.yml`
- 문제:
  1. `DB_ROOT_PASSWORD` 는 `docker-compose.mysql.yml` 이 필수로 요구하는데 `.env.example` 과 환경 변수 표에 없다.
  2. `SERVER_PORT`, `PUBLIC_API_URL` 은 문서에 있지만 `docker-compose.prod.yml` 이 컨테이너로 넘기지 않아 설정해도 효과가 없다.
  3. `.env.example` 의 값이 비어 있는 항목(`ADMIN_PASSWORD_HASH=`, `MAIL_HOST=` 등)은 그대로 복사하면 "빈 문자열로 설정됨"이 돼 기본값을 덮는다.
- 할 일: 1번은 키 이름만 추가한다 (값은 넣지 않는다). 2번은 문서에서 지우거나 compose 에 추가한다. 3번은 주석 처리한 예시 형태로 바꾼다.
- 메모: 이 문서를 쓸 때 `.env.example` 은 권한 설정상 다시 열어 보지 못했다. **먼저 현재 내용을 확인한다.**

### [ ] F-13 · `application-prod.yml` 의 localhost 기본값 제거
- 파일: `src/main/resources/application-prod.yml` 5~6행
- 문제: `DB_URL`, `DB_USERNAME` 에 localhost 기본값이 있어 환경 변수가 빠져도 엉뚱한 곳에 접속을 시도한다.
- 할 일: 기본값을 지워 누락 시 기동이 실패하게 한다. prod 프로필로 뜨는 테스트·로컬 절차가 없는지 먼저 `grep -rn 'prod' src/test docs` 로 확인한다.

### [ ] F-14 · MySQL 테스트 이미지를 운영과 같은 버전으로
- 파일: `CommuteCorrectionMySqlIntegrationTest.java` 57행, `ReportDispatchRepositoryMySqlIntegrationTest.java` 40행, `AnnualLeaveServiceMySqlIntegrationTest.java` 43행
- 문제: 테스트는 `mysql:8.4`, 운영(`deploy/docker-compose.mysql.yml`)과 로컬(`docker-compose.yml`)은 `mysql:8.0`.
- 할 일: 테스트 3곳을 `mysql:8.0` 으로 바꾼다 (운영 DB 업그레이드는 별도 결정).
- 검증: Docker 실행 상태에서 `./gradlew test --tests '*MySqlIntegrationTest'` — 스킵이 아니라 실행됐는지 확인.

### [ ] F-15 · `[결정 필요]` Dockerfile 강화
- 파일: `Dockerfile`
- 항목: (1) root 가 아닌 사용자로 실행, (2) `eclipse-temurin:21-jre` 태그를 구체 버전으로 고정.
- 주의: 운영의 `app-logs` 볼륨(`/app/logs`)은 root 소유로 만들어져 있다. 사용자만 바꾸면 로그를 쓰지 못한다. 권한 이전 절차를 함께 설계해야 한다.

### [ ] F-16 · `[결정 필요]` p6spy 를 운영 jar 에서 제외
- 파일: `build.gradle` 31행, `application-prod.yml` 61~72행
- 현재: 운영에서는 설정으로 꺼 두기만 한다. 의존성은 jar 에 포함된다.
- 선택지: `developmentOnly` 로 옮기기 / 지금처럼 두기. 옮기면 dev·mysql 프로필과 테스트에서 클래스가 필요한지 확인해야 한다.

### [ ] F-17 · `[결정 필요]` 추적 파일에 들어 있는 인프라 식별자
- 위치: `.github/workflows/deploy.yml` 95행(계정 ID), `deploy/nginx.conf` 29·50행(IP 가 들어간 호스트명),
  `docs/DEPLOY_LOG_2026-08.md`·`docs/DEPLOY_LOG_2026-08-20.md`(서버 주소·접속 정보), `docs/CD_RUNBOOK.md`(IAM 역할 이름)
- 정할 것: 저장소 공개 범위에 비춰 그대로 둘지, 저장소 변수로 옮기거나 문서에서 가릴지.
- 주의: 값을 이 파일이나 커밋 메시지에 옮겨 적지 않는다.

---

## G. 낡은 문서·파일 정리

삭제 전에 다른 문서에서 링크하는지 확인한다:

```bash
grep -rn '<파일 이름>' --include='*.md' --include='*.yml' --include='*.sh' .
```

### [ ] G-1 · `deploy/AGENT_DEPLOY_PROMPT.md` 삭제
- 이유: CD 도입 전 수동 배포용 일회성 프롬프트다. 지금 따라 하면 현재 절차와 충돌한다.

### [ ] G-2 · `docs/handoff/` 삭제
- 대상: `commute-correction-backend.md`, `commute-correction-frontend.md`, `commute-correction-frontend-integration.md`, `commute-correction-integration.md`
- 이유: 병합 완료된 PR #53 의 작업 인계 문서다. 그중 하나는 본문에서 `main` 에 커밋하지 말라고 적혀 있다.

### [ ] G-3 · `docs/COMMUTE_CORRECTION_PLAN.md` 상태 갱신
- 문제: 머리말이 "기술 계약 미확정, 구현 요청 이후 진행"이고 마지막 마이그레이션을 V14 로 적는다. 실제로는 구현·배포됐고 V16 까지 있다.
- 할 일: 머리말 상태를 "구현 완료"로 고치고 현재 문서(`docs/COMMUTE_CORRECTION_API.md`, `docs/COMMUTE_CORRECTION_OPERATIONS.md`)를 가리킨다.
  4~8절의 낡은 구현 계획은 지우거나 "당시 계획" 이라고 표시한다.

### [ ] G-4 · `docs/COMMUTE_CORRECTION_API.md` 의 낡은 문장 2개
- 79행: "`MyCommutePage.tsx` 가 `UNCLOSED` 를 써서 `tsc` 가 실패한다" — 이미 고쳐졌다. 괄호 부분을 지운다.
- 133행 근처: 점검 때 낡은 서술로 표시됐지만 어느 문장인지 기록이 남아 있지 않다. 요청 예시 주변 설명을 현재 프론트 구현(`frontend/src/pages/MyCommutePage.tsx`)과 대조해 다른 부분만 고친다.

### [ ] G-5 · `docs/DEPLOY_LOG_2026-08-20.md` 롤백 절차에 경고 추가
- 위치: "4. 롤백 절차" 코드 블록 (`rm -rf dist && mv dist.bak dist`)
- 문제: `dist` 는 디렉터리 바인드 마운트다. 디렉터리를 지우고 새로 만들면 실행 중인 nginx 는 옛 inode 를 계속 봐서 사이트가 빈 화면이 될 수 있다.
- 할 일: 기록 문서라 본문은 두고, 블록 위에 "이 절차는 쓰지 말 것 — `rsync -a --delete dist.bak/ dist/` 로 내용만 교체" 경고를 단다.
  두 배포 로그(`DEPLOY_LOG_2026-08*.md`)의 수동 절차 요약에도 "현재는 CD 가 대체, `docs/CD_RUNBOOK.md` 참고" 한 줄을 단다.

### [ ] G-6 · `docs/DEPLOYMENT.md` 내부 모순 정리
- 확인할 것:
  1. 98행은 `useSSL=false&allowPublicKeyRetrieval=true` 를 운영에 복사하지 말라고 하는데, `deploy/docker-compose.mysql.yml` 머리 주석의 운영용 URL 에는 두 파라미터가 들어 있다. 어느 쪽이 맞는지 정해 한쪽을 고친다.
  2. 132행의 리허설 명령 `docker compose --profile full up` 에는 `--build` 가 없어 예전 이미지로 뜰 수 있다. 실제로 동작하는 명령으로 고친다.
- 할 일: 실제 파일과 대조해 틀린 쪽을 고친다. 실행해 보지 못한 명령은 PR 설명에 그렇게 적는다.

### [ ] G-7 · `deploy/docker-compose.prod.yml` 머리 주석 갱신 — `[운영 영향]`
- 문제: 주석이 "파일 하나로 `up -d`", "관리형 DB 권장"을 설명한다. 실제로는 `docker-compose.mysql.yml` 을 겹쳐 쓰고 `remote-deploy.sh` 가 실행한다.
- 주의: 주석만 바꿔도 `main` 머지 시 배포가 돈다. 다른 배포 변경(F 묶음)과 함께 내보내는 편이 낫다.

### [ ] G-8 · 삭제된 `TODO.md` 를 가리키는 참조 정리
- 위치: `src/test/docs/adr/2026_08_09_adr1.md` 4행, `src/test/docs/adr/2026_08_10_adr3.md` 4·224행,
  `src/test/docs/solution/2026_08_20_solution1.md` 64·174행
- 배경: 루트 `TODO.md` 는 커밋 `e68cbc7` 에서 삭제됐다. 위 문서들은 그 파일의 "N번 항목"을 가리킨다.
- 할 일: 각 문서의 첫 참조에 "(TODO.md 는 삭제됨 — `git show e68cbc7^:TODO.md` 로 확인)" 을 덧붙인다.
- 건드리지 않는 것: `src/main/resources/db/migration/V10__drop_holiday_tables.sql` 2행 — 적용된 마이그레이션이다.

### [ ] G-9 · `.gitignore` 의 쓰이지 않는 항목
- 위치: 42~46행 — `docker/`, `src/main/resources/static/docs/`(RestDocs)
- 할 일: 저장소에 해당 경로·도구가 없음을 확인하고 두 블록을 지운다. 우선순위 낮음.

### [ ] G-10 · `README.md` 의 Before/After 코드 조각 확인
- 할 일: README 에 인용된 코드가 현재 코드와 같은지 대조한다. 다르면 갱신하거나 "당시 코드" 라고 표시한다.

---

## H. 쓰이지 않거나 중복된 코드

### [ ] H-1 · `AnnualLeaves.numberOfLeaves` 삭제
- 파일: `domain/annual_leave/AnnualLeaves.java`, `AnnualLeavesTest.java`
- 근거: 프로덕션 호출이 없고 테스트 1곳만 쓴다. 메서드와 그 테스트를 함께 지운다.
- 확인: `grep -rn 'numberOfLeaves' src`

### [ ] H-2 · `CommuteHistory.endTimeIsNull` 삭제
- 파일: `domain/commute/CommuteHistory.java`, 사용하는 테스트
- 근거: 프로덕션 호출이 없다. 테스트의 단언은 `getWorkEndTime()` 이 null 인지 보는 것으로 바꾼다.
- 확인: `grep -rn 'endTimeIsNull' src`

### [ ] H-3 · `[결정 필요]` 테스트에서만 쓰는 `CommuteHistory.endWork`, `OverTimeReportService.generateExcelReport`
- 현황: `endWork` 는 테스트 픽스처와 통합 테스트 여러 곳이 쓴다 (운영 퇴근은 조건부 UPDATE 쿼리). `generateExcelReport` 는 `OverTimeReportServiceTest` 10곳이 쓴다.
- 선택지: 그대로 두기 / 테스트를 공개 진입점 기준으로 다시 쓰고 삭제. 테스트 수정량이 커서 사용자 판단이 필요하다.

### [ ] H-4 · `AnnualLeaveEnrollment` 단순화
- 선행: A-5 (같은 파일을 건드린다)
- 파일: `domain/annual_leave/AnnualLeaveEnrollment.java`, `domain/employee/Employee.java`(`enrollAnnualLeave`)
- 문제: `employeeId` 필드가 쓰이지 않는다. 2인자 생성자와 `enroll` 오버로드 일부는 테스트만 쓴다.
  팀 기준 검사(`team.isNotEnoughCriteria`)가 `Employee.enrollAnnualLeave` 와 `AnnualLeaveEnrollment.enroll` 에서 **두 번** 실행된다.
- 할 일: 검사를 한 곳으로 모으고 쓰이지 않는 필드·오버로드를 지운다. 테스트는 남는 진입점으로 옮긴다.

### [ ] H-5 · 비슷한 두 쿼리 조사
- 파일: `repository/commute/CommuteHistoryRepository.java` 38~70행 (`findUnclosedByWorkDateBetween`, `findUnresolvedByWorkDateBetween`)
- 할 일(조사만): 두 쿼리의 차이(조건·반환 프로젝션)를 표로 정리하고 합칠 수 있는지 판단해 이 항목 아래에 적는다. 합치기는 별도 항목으로.

### [ ] H-6 · 유니크 제약 이름 리터럴 중복 제거
- 문제: 같은 제약 이름이 엔티티의 `@UniqueConstraint` 와 서비스 상수에 따로 적혀 있다 (예: `AnnualLeaveService` 의 `UK_ANNUAL_LEAVE_EMPLOYEE_DATE`).
- 할 일: 엔티티에 `public static final String` 상수로 두고 양쪽에서 참조한다. 엔티티 하나당 커밋 하나. E-1·E-2 를 먼저 하면 같은 방식을 적용한다.

### [ ] H-7 · `DatabaseConstraintMatcher.normalize` 의 비교 순서
- 파일: `global/persistence/DatabaseConstraintMatcher.java`
- 문제: `_INDEX_…` 꼬리를 떼는 정규화를 먼저 하고 비교한다. 제약 이름 자체에 그 패턴이 있으면 잘못 일치할 수 있다.
- 할 일: 원본 이름 비교를 먼저 하고, 정규화 비교는 그다음 수단으로 쓴다. 두 경우의 단위 테스트 추가.

### [ ] H-8 · 로그 수준 조정
- 파일: `global/exception/GlobalExceptionHandler.java`
- 할 일:
  1. 89행 `log.warn("Validation failed for request", e)` — 스택 트레이스 전체가 찍힌다. 필드 오류 요약만 남긴다.
  2. 135행(401)·142행(403) — 로그인하지 않은 요청마다 WARN 이 찍힌다. INFO 또는 DEBUG 로 내린다.
- 테스트 변경 없음. `./gradlew check` 만 확인.

### [ ] H-9 · `handleInvalidJson` 의 필드 경로 추출 범위 확대
- 파일: `GlobalExceptionHandler.java` 98~117행
- 문제: 필드 경로를 `InvalidFormatException` 에서만 뽑는다. 다른 `JsonMappingException`(타입 불일치 등)은 경로 없이 일반 문구가 나간다.
  같은 폴백 문구 "요청 본문을 해석할 수 없습니다." 가 두 번 적혀 있다.
- 할 일: `JsonMappingException` 으로 넓히고 문구를 상수로 뽑는다. 컨트롤러 테스트에 사례 1개 추가.

### [ ] H-10 · `[결정 필요]` `spring.jpa.open-in-view` 명시
- 현황: 설정이 없어 기동 때마다 경고가 뜬다 (기본값 true).
- 주의: `false` 로 바꾸면 컨트롤러에서 지연 로딩하는 코드가 있을 때 깨진다. `./gradlew check` 와 주요 화면을 확인한 뒤에만 바꾼다.

### [ ] H-11 · dev 프로필의 SQL 로그 삼중 출력
- 파일: `src/main/resources/application-dev.yml` 17행(`show_sql`), 28~35행(p6spy)
- 문제: `show_sql`, p6spy, 하이버네이트 디버그 로그가 같은 SQL 을 세 번 찍고 루트에 `spy.log` 를 만든다 (`.gitignore` 처리됨).
- 할 일: p6spy 한 가지만 남기고 파일 출력을 끈다. dev 서버를 띄워 SQL 이 한 번씩만 보이는지 확인.

---

## I. 테스트 품질

### [ ] I-1 · 실제 시계를 쓰는 통합 테스트 고정 — 출퇴근
- 파일: `src/test/java/.../service/commute/CommuteHistoryServiceIntegrationTest.java`(2곳)
- 문제: 실제 시각으로 근무일을 계산한다. 한국 시간 자정 근처에 실행하면 날짜가 넘어가 실패할 수 있다.
- 할 일: `src/test/java/.../config/MutableClock.java` 를 `Clock` 빈으로 주입하고 고정 시각에서 시작한다. `RegularEndTargetIntegrationTest` 가 본보기다.

### [ ] I-2 · 같은 문제 — 동시성 테스트
- 파일: `src/test/java/.../service/commute/CommuteHistoryServiceConcurrentTest.java`
- 할 일: I-1 과 같다.

### [ ] I-3 · `CommuteHistoryServiceTest`, `CommuteHistoryTest` 의 `now()` 정리
- 파일: `service/commute/CommuteHistoryServiceTest.java`(5곳), `domain/commute/CommuteHistoryTest.java`(1곳)
- 할 일: 날짜 경계에 걸리는 사용인지 하나씩 확인하고, 걸리면 고정 `Instant` 로 바꾼다.

### [ ] I-4 · 스케줄러 테스트가 실제 애너테이션을 검사하도록
- 파일: `src/test/java/.../scheduler/OverTimeReportDispatchSchedulerTest.java`, `scheduler/OverTimeReportDispatchScheduler.java` 47·55행
- 문제: 테스트 안에 복사해 둔 cron 문자열(`DISPATCH_CRON`, `ALERT_CRON`)을 검사한다. 실제 `@Scheduled` 값을 바꿔도 테스트는 통과한다.
- 할 일: 리플렉션으로 메서드의 `@Scheduled` 에서 `cron()` 과 `zone()` 을 읽어 검사한다. `zone` 이 `Asia/Seoul` 인지도 단언한다.

### [ ] I-5 · `ReportDispatchRepositoryTest.yearMonthRoundTrips`
- 문제: 저장한 엔티티를 1차 캐시에서 다시 꺼내므로 `YearMonth` 컨버터를 거치지 않는다.
- 할 일: 저장 후 `flush()` + `clear()` 한 다음 조회해 비교한다.

### [ ] I-6 · 아무것도 검증하지 않는 연차 테스트
- 위치: `AnnualLeaveServiceTest.java` 143~153행 근처, `AnnualLeaveEnrollmentTest.testEnroll`
- 할 일: 의미 있는 단언(반환 값·저장 인자)을 넣거나 삭제한다. A-4 와 같은 파일이므로 A-4 이후에 한다.

### [ ] I-7 · `EmployeeServiceTest.rejectsInvalidPairs`
- 문제: 서비스가 아니라 `Employee` 도메인 메서드를 직접 부른다.
- 할 일: `EmployeeTest` 로 옮기거나 서비스 경유로 고친다.

### [ ] I-8 · 세션 헬퍼를 `SessionRoleFixture` 로 통합
- 파일: `src/test/java/.../auth/SessionRoleFixture.java`, 이를 쓰는 컨트롤러 테스트들
- 문제: `MANAGER_ID`/`MEMBER_ID` 상수가 있는데 테스트들은 `1L`/`2L` 을 직접 쓰고, `managerSession()`/`memberSession()` 이 5개 파일에 복사돼 있다 (예: `TeamControllerTest.java` 306~316행).
  찾기: `grep -rln 'MockHttpSession managerSession' src/test`
- 할 일:
  1. `SessionRoleFixture` 에 `managerSession()`, `memberSession()` 정적 메서드 추가.
  2. 컨트롤러 테스트 **하나당 커밋 하나**로 지역 헬퍼를 지우고 픽스처를 쓴다.

### [ ] I-9 · 개발용 시드가 H2 테스트마다 로드됨
- 파일: `src/test/resources/application.yml`, `src/main/resources/data.sql`
- 문제: 테스트 설정에 `spring.sql.init.mode` 가 없어 내장 DB 기본 동작으로 `data.sql`(개발 시드)이 모든 테스트 컨텍스트에 들어간다.
  `EmployeeRepositoryTest`, `AuthControllerTest`, `MonthlyClosingControllerTest` 는 시드 계정(`ADMIN001`, `APPROVER01`)을 지우는 코드를 갖고 있다.
- 할 일: 테스트 설정에 `spring.sql.init.mode: never` 를 넣고, 시드에 기대던 테스트는 자기 데이터를 직접 만든다. 위 세 테스트의 삭제 코드를 지운다.
- 주의: 영향 범위가 넓다. `./gradlew check` 전체 통과가 완료 기준이다.

### [ ] I-10 · `OriginCheckInterceptor` 허용 목록 분기 테스트
- 파일: `auth/OriginCheckInterceptor.java`
- 할 일: 허용 목록에 있는 Origin 은 통과, 없는 Origin 은 `CSRF_ORIGIN_REJECTED`, Origin 헤더가 없는 변경 요청의 처리 — 세 경우 테스트를 추가한다.

### [ ] I-11 · 동시성 테스트 하네스 공유
- 파일: `CommuteCorrectionConcurrentTest`, `CommuteHistoryServiceConcurrentTest`, `AnnualLeaveServiceConcurrentTest`
- 할 일: 반복되는 `CountDownLatch` + 스레드 풀 코드를 테스트 유틸 `runConcurrently(int threads, Callable<T>)` 로 뽑는다. 테스트 하나당 커밋 하나.

### [ ] I-12 · MySQL Testcontainers 설정 공유
- 파일: `*MySqlIntegrationTest` 3개
- 문제: 컨테이너 선언과 `@DynamicPropertySource` 가 복사돼 있고 컨테이너가 세 번 뜬다.
- 할 일: 공용 추상 클래스(정적 싱글턴 컨테이너)로 뽑는다. F-14 를 먼저 하면 버전 문자열도 한 곳에 모인다.

### [ ] I-13 · 좁은 단언 보강
- 위치: `ApiHolidayCalendarTest.java` 48~56행, `OverTimeReportDispatchServiceTest.java` 250~258행
- 할 일: 각 테스트가 이름에서 말하는 동작 전체(반환 값·상태 전이)를 단언하는지 확인하고 빠진 것을 채운다.

---

## J. API 계약

### [ ] J-1 · 공통 400 코드를 `openapi.yml` 에 문서화
- 문제: `MISSING_PARAMETER`, `INVALID_PARAMETER`, `INVALID_JSON` 은 `GlobalExceptionHandler` 가 실제로 돌려주지만 여러 오퍼레이션의 400 응답에 적혀 있지 않다.
- 할 일: 쿼리 파라미터가 있는 오퍼레이션에는 앞의 두 코드를, 요청 본문이 있는 오퍼레이션에는 `INVALID_JSON` 을 400 설명에 추가한다.
  오퍼레이션 묶음(auth / employee / team / commute / annual-leave / overtime / corrections / closings)당 커밋 하나.
- 검증: `./gradlew openApiValidate`, `pnpm --dir frontend gen:api` 후 프론트 빌드.

### [ ] J-2 · `[결정 필요]` 도달할 수 없는 404 `EMPLOYEE_NOT_FOUND`
- 문제: 출퇴근·연차 오퍼레이션이 404 `EMPLOYEE_NOT_FOUND` 를 문서화한다. 실제로는 인터셉터가 먼저 401 을 돌려주므로 거의 도달하지 않는다.
- 선택지: 문서에서 제거 / 방어적 문서화로 유지.

### [ ] J-3 · 이메일 형식 검증 세 곳 일치
- 위치: `dto/employee/request/EmployeeSaveRequest.java` 39~43행(정규식, 최상위 도메인 2자 이상 필수), `openapi.yml` 의 해당 스키마, `frontend/src/pages/EmployeesPage.tsx` 82행(`/^\S+@\S+$/`)
- 문제: 백엔드 정규식이 명세와 프론트 검증보다 엄격하다. 프론트에서 통과한 값이 서버에서 400 이 된다.
- 할 일: 백엔드 정규식을 기준으로 `openapi.yml` 에 `pattern` 을 적고 프론트 검증을 같은 규칙으로 맞춘다.
