# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run single test class
./gradlew test --tests "com.company.officecommute.service.employee.EmployeeServiceTest"

# Run single test method
./gradlew test --tests "com.company.officecommute.service.employee.EmployeeServiceTest.testMethodName"

# Run application (dev profile with H2)
./gradlew bootRun

# Run against local MySQL with Flyway (docker-compose up first)
SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun

# Clean build
./gradlew clean build
```

## Development Environment

- **Language**: Java 21
- **Framework**: Spring Boot 3.5.5
- **Profiles**:
  - `dev` (default): H2 TCP (`jdbc:h2:tcp://localhost/~/test`), `ddl-auto: create-drop`, Flyway disabled.
  - `mysql`: 로컬 MySQL 검증용. `ddl-auto: validate` + Flyway. `docker-compose.yml`로 컨테이너 기동 후 사용. `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` 환경변수 오버라이드 가능.
  - `prod`: MySQL 8.0 + Flyway, `ddl-auto: validate`.
- **Migrations**: `src/main/resources/db/migration/V*__*.sql` (Flyway). `prod`/`mysql` 프로파일에서만 실행됨. 스키마 변경은 새 V 파일 추가로만 반영.
- 외부 API: `PUBLIC_API_SERVICE_KEY` 환경변수 필요 (공공데이터포털 특일정보 API)

## Architecture Overview

사내 출퇴근 관리 시스템 - 직원의 출퇴근, 연차, 초과근무를 관리하는 시스템

### Layered Architecture
```
Controller → Service → Repository → Database
                ↓
           Domain (비즈니스 로직)
```

### Key Domain Concepts

1. **CommuteHistory**: 출퇴근 기록 엔티티
   - `employee_id + work_date` 복합 유니크 제약
   - `endWork()` 메서드로 퇴근 처리 및 근무시간(분) 계산
   - `usingDayOff` 플래그로 연차 사용일 구분

2. **AnnualLeaveEnrollment**: 연차 등록 도메인 객체
   - 팀별 연차 신청 기준일(`annualLeaveCriteria`) 검증
   - 기존 연차와 중복 검증 로직 포함

3. **ApiConvertor**: 외부 공휴일 API 연동 + DB 폴백
   - 공공데이터포털 API로 공휴일 조회
   - API 실패 시 DB 캐시에서 조회
   - `prefetchNextMonthHolidays()`: 다음 달 공휴일 선제 저장

4. **OverTimeService**: 초과근무 계산
   - 법정 근무일수 = 월 일수 - 주말 - 공휴일(주말 제외)
   - 법정 근무시간 = 법정 근무일수 × 8시간 × 60분

### Authentication

- 로그인: `POST /api/auth/login` (email + password). 성공 시 기존 세션을 `invalidate()` 후 새 세션을 발급해 `currentEmployeeId`, `currentRole`을 저장 (세션 고정 공격 방어).
- 로그아웃: `POST /api/auth/logout`은 현재 세션을 무효화. 세션이 없어도 200.
- 비밀번호: `BCryptPasswordEncoder`로 해싱·검증 (`EmployeeService.authenticate`).
- `AuthInterceptor`: 매 요청마다 세션에서 `currentEmployeeId`, `currentRole`을 읽어 request attribute로 복사. 세션 없으면 `AuthenticationFailedException` (401).
- `WebConfig`에서 `/api/auth/**`는 인터셉터 제외 (로그인/로그아웃은 세션 없이도 호출 가능).
- `@ManagerOnly` 어노테이션으로 MANAGER 전용 API 권한 체크. 인터셉터에서 검증 후 위반 시 `ForbiddenException` (403).
- `Role` enum: MANAGER, MEMBER.
- 인증 예외 클래스는 `com.company.officecommute.auth` 패키지: `AuthenticationFailedException` (401), `ForbiddenException` (403).

### Test Patterns

- Service 테스트: `@SpringBootTest` + H2 인메모리.
- Controller 테스트: `@SpringBootTest` + `@AutoConfigureMockMvc` + `MockMvcTester` (AssertJ 스타일). 의존 서비스는 `@MockitoBean`으로 대체.
- 동시성 테스트: `*ConcurrentTest`, `*ConcurrencyTest` 클래스.
- 테스트 픽스처: `EmployeeBuilder`, `Employees`, `Teams` 유틸리티 클래스.

## Output Format

- 도구 사용 시 앞에 🥕 이모지를 붙여서 표시

## Language

For every prompt I enter, whether in Korean or English, first rewrite it into proper English. If I write in English, point out any grammatical errors and suggest improvements to make the sentence more natural. Then, proceed with the rewritten, polished English prompt.
