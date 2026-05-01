# AGENTS.md

Guide for agentic coding assistants working in this repository.
Keep changes small, follow existing patterns, and verify behavior with the narrowest relevant Gradle task.

## Project Snapshot
- Java 21, Spring Boot 3.5.5, Gradle wrapper.
- Main stack: Spring MVC, Spring Data JPA, validation, session-based auth, Flyway, Apache POI.
- Databases: H2 for `dev`, MySQL 8.0 for local `mysql` and `prod`.
- Tests: JUnit 5, Spring Boot test, MockMvc/MockMvcTester, Mockito, Spring REST Docs.

## Commands
- Run all tests: `./gradlew test`
- Run one test class: `./gradlew test --tests "com.company.officecommute.service.employee.EmployeeServiceTest"`
- Run one test method: `./gradlew test --tests "com.company.officecommute.service.employee.EmployeeServiceTest.methodName"`
- Build with tests and REST Docs: `./gradlew build`
- Clean build: `./gradlew clean build`
- Generate REST Docs: `./gradlew asciidoctor`
- Run dev profile: `./gradlew bootRun`
- Run local MySQL profile: `SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun`
- Start local MySQL: `docker compose up -d`

## Configuration
- Default profile is `dev` from `src/main/resources/application.yml`.
- `dev` uses H2 TCP at `jdbc:h2:tcp://localhost/~/test`, `ddl-auto: create-drop`, `data.sql`, and Flyway disabled.
- `mysql` uses local MySQL, Flyway, and `ddl-auto: validate`; `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` can override defaults.
- `prod` uses MySQL 8.0, Flyway, and `ddl-auto: validate`.
- Flyway migrations live in `src/main/resources/db/migration/V*__*.sql`; schema changes for MySQL/prod should be new migration files.
- Environment variables can be loaded from `.env`; start from `.env.example`.
- Public holiday API calls require `PUBLIC_API_SERVICE_KEY`.

## Architecture
- Keep the existing package boundaries: `controller`, `service`, `repository`, `domain`, `dto`, `auth`, `config`, `web`, `scheduler`.
- Request flow is Controller -> Service -> Repository -> Database.
- Put business rules and invariants in domain objects or services, not controllers.
- Use repositories for persistence access only.
- Session auth is handled by `AuthInterceptor`; authenticated values are exposed as request attributes such as `currentEmployeeId` and `currentRole`.
- `POST /api/auth/login` authenticates by email/password, invalidates any existing session, then stores `currentEmployeeId` and `currentRole` in a new session.
- Passwords are hashed and verified through `BCryptPasswordEncoder`.
- `WebConfig` excludes `/api/auth/**` from the interceptor; other endpoints require a valid session.
- Manager-only endpoints use `@ManagerOnly`; roles are `MANAGER` and `MEMBER`.

## Key Domain Concepts
- `CommuteHistory` records work start/end, enforces one record per `employee_id + work_date`, and calculates working minutes in `endWork()`.
- Annual leave is represented through `AnnualLeave`, `AnnualLeaves`, and `AnnualLeaveEnrollment`; enrollment validates team criteria and duplicate dates.
- `ApiConvertor` fetches holidays from the public API, stores successful responses in the database, and falls back to usable cached holiday data when the API fails.
- Overtime calculation subtracts weekends and weekday holidays from the month, then uses 8 hours per standard working day.

## Coding Guidelines
- Follow the style already present in nearby files; no new formatter or lint tooling is configured.
- Use 4-space indentation, K&R braces, no wildcard imports.
- Prefer Java `record` for immutable request/response DTOs.
- Validate request bodies with `jakarta.validation` and `@Valid` where the endpoint accepts a body.
- Services should use `@Service` and `@Transactional`; use `@Transactional(readOnly = true)` for read paths.
- Prefer repository methods returning `Optional` and convert missing/domain-invalid state to clear exceptions at the service boundary.
- Do not add dependencies or change build tooling unless the task explicitly requires it.

## Error Handling
- Business validation errors generally use `IllegalArgumentException`.
- Login failures use `AuthenticationFailedException`.
- Authorization failures use `ForbiddenException` or `@ManagerOnly` through `AuthInterceptor`.
- Holiday API/cache failures use `HolidayDataUnavailableException`.
- Keep responses aligned with `GlobalExceptionHandler`, `ErrorResult`, and `ValidationErrorResult`.

## Tests And Docs
- Controller integration tests use `@SpringBootTest`, `@AutoConfigureMockMvc`, and `MockMvcTester`.
- REST Docs tests extend `RestDocsSupport` and generate snippets under `build/generated-snippets`.
- Service/domain tests use Mockito or Spring context according to the existing test in that package.
- Use existing fixtures such as `EmployeeBuilder`, `Employees`, and `Teams`.
- Add or update tests when behavior changes; REST Docs only renders after tests pass.

## Output Format
- 도구 사용 시 앞에 🥕 이모지를 붙여서 표시

## Language
For every prompt I enter, whether in Korean or English, first rewrite it into proper English. If I write in English, point out any grammatical errors and suggest improvements to make the sentence more natural. Then, proceed with the rewritten, polished English prompt.

## Working Rules
- Keep edits focused on the requested behavior.
- Preserve existing user changes in the working tree.
- Prefer existing helper APIs and naming conventions over new abstractions.
- If unsure, inspect the closest controller/service/test and match that pattern.
