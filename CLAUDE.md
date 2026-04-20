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

# Clean build
./gradlew clean build
```

## Development Environment

- **Language**: Java 21
- **Framework**: Spring Boot 3.5.5
- **Database**: H2 (dev), MySQL 8.0 (prod)
- **Dev profile** uses H2 TCP connection (`jdbc:h2:tcp://localhost/~/test`)
- External API requires `PUBLIC_API_SERVICE_KEY` environment variable (공공데이터포털 특일정보 API)

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

- `AuthController`: `POST /api/auth/login` (email + password) → `HttpSession`에 `currentEmployeeId`, `currentRole` 저장. `POST /api/auth/logout`은 세션 무효화.
- `AuthInterceptor`: 매 요청마다 세션에서 `currentEmployeeId`, `currentRole`을 읽어 request attribute로 복사. 세션 없으면 401.
- `WebConfig`에서 `/api/auth/**`는 인터셉터 제외 (로그인/로그아웃은 세션 없이도 호출 가능).
- `@ManagerOnly` 어노테이션으로 MANAGER 전용 API 권한 체크 (인터셉터에서 처리)
- `Role` enum: MANAGER, MEMBER
- 비밀번호는 현재 평문 저장 (BCrypt 해싱은 별도 작업)

### Test Patterns

- Service 테스트: `@SpringBootTest` + H2 인메모리
- Controller 테스트: `@WebMvcTest` + MockMvc
- 동시성 테스트: `*ConcurrentTest`, `*ConcurrencyTest` 클래스
- 테스트 픽스처: `EmployeeBuilder`, `Employees`, `Teams` 유틸리티 클래스

## Output Format

- 도구 사용 시 앞에 🥕 이모지를 붙여서 표시
