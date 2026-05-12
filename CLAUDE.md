# CLAUDE.md

## Build / Test
- Run app (dev, H2 TCP): `./gradlew bootRun --args='--spring.profiles.active=dev'`
- Run app (mysql): `./gradlew bootRun --args='--spring.profiles.active=mysql'`
- Tests: `./gradlew test`
- Full check (tests + `openApiValidate`): `./gradlew check`
- Build jar: `./gradlew build`

## Project Structure
- `src/main/java/com/company/officecommute/` — layered: `controller / service / domain / repository / dto / web / auth / global / config / scheduler`
- `src/main/resources/db/migration/V*__*.sql` — Flyway (MySQL/prod schema, canonical)
- `src/main/resources/data.sql` — H2 dev seed
- `src/main/resources/application*.yml` — profile configs (`dev`, `mysql`, `prod`)
- `src/test/java/...` — mirrors main package layout
- `openapi.yml` (root) — wire-format source of truth
- `WORKS.md` / `PLAN.md` — feature list & current plan
- `scripts/api_test.sh` — manual API smoke test

## Profiles
- `dev`: H2 TCP `jdbc:h2:tcp://localhost/~/test`, `ddl-auto: create-drop`, `data.sql` on, Flyway off.
- `mysql` / `prod`: Flyway on, `ddl-auto: validate`. Overrides via `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`.
- Holiday API requires `PUBLIC_API_SERVICE_KEY`.
- **Do not commit secret values or per-developer settings here.** Put them in `CLAUDE.local.md` (gitignored) — e.g. your local MySQL URL, your `PUBLIC_API_SERVICE_KEY`, sandbox credentials. Only the *names* of required env vars belong in this file.

## Coding Conventions
- Java 21, Spring Boot 3.5. 4-space indentation, no tabs.
- Date/time: avoid `LocalDateTime` (timezone-ambiguous). Use `ZonedDateTime` for timestamps (already standard in commute/overtime), `LocalDate` / `YearMonth` for calendar dates. Inject `Clock` for "now" so tests can control time. Never downgrade dates to `String`.
- Naming: controllers `*Controller`, services `*Service`, repos `*Repository`, DTOs `*Request` / `*Response`, exceptions `*Exception`.
- Domain exceptions: one class per business meaning (e.g. `TeamAlreadyExistsException`). Never reuse `IllegalArgumentException` for business errors.
- Error envelope: `ErrorResult { code, message }` or `ValidationErrorResult { code, message, fieldErrorResults }`. Codes follow `*_ALREADY_EXISTS`, `*_NOT_FOUND`, `VALIDATION_ERROR`, `INVALID_JSON`.
- Static factories for registration: `Entity.register(...)`; validation/normalization lives there or in the canonical full-arg constructor.
- Three-layer validation: domain constructor/factory + JPA `@Column(nullable=false, unique=…)` + Flyway DDL must agree.
- ID-based API: registration returns `{ entityId }`; mutation endpoints carry the ID in the path (`PUT /employee/{employeeId}/team`). Don't introduce name-based contracts.

## Always
- **Spec-first**: update `openapi.yml` whenever a controller or DTO changes. No `@Operation` / `@ApiResponse` annotations on controllers — the yml is the spec.
- **Never edit an applied Flyway `V*__*.sql` file** (checksum). Schema changes go in a new `V` file.
- When a column changes, update `data.sql` too — otherwise `dev` boot fails.
- Throw domain exceptions, don't catch — `GlobalExceptionHandler` (`@RestControllerAdvice`) converts them. After a uniqueness pre-check, catch `DataIntegrityViolationException` and re-throw the matching `*_ALREADY_EXISTS` (race safety net).
- Default: derive aggregates instead of storing them (e.g. `Team.memberCount` → `COUNT(*) GROUP BY team_id`) — removes sync-bug surface. Escape hatch: if a query is measurably slow under realistic load (not hypothetically), introduce a stored counter / cache with the invariant documented and the update path covered by tests. Don't add one preemptively.

> Scoped rules: test conventions live in `.claude/rules/testing.md`, auth rules for controllers in `.claude/rules/auth.md` (auto-loaded by path).
