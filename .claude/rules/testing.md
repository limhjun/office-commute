---
paths:
  - "src/test/**/*.java"
---

# Test Conventions

## Layer → Test style

| Target | Setup | Why |
|---|---|---|
| Domain (`domain/**`) | Plain JUnit, no Spring | POJO logic; fastest |
| Service (`service/**`) | `@ExtendWith(MockitoExtension.class)` + `@Mock` for repositories, manual `new EmployeeService(...)` in `@BeforeEach` | Unit; isolate from DB |
| Repository (`repository/**`) | `@DataJpaTest` | Slice; real JPA against H2 |
| Controller (`controller/**`) | `@SpringBootTest` + `@AutoConfigureMockMvc` + `MockMvcTester`, services as `@MockitoBean` | Wire/serialization + filters (auth) |

Use the narrowest slice that covers the behavior. Regular service tests should avoid `@SpringBootTest`; concurrency/integration service tests may use it when DB transactions, constraints, or real Spring wiring are part of the behavior.

## Naming

- Class: `<Target>Test` (e.g. `EmployeeServiceTest`).
- Method names are short and English; the human-readable scenario goes in `@DisplayName` (Korean is the project default — match existing files).
- Group cases with `@Nested` per endpoint or per method under test, with `@DisplayName` on the nested class (e.g. `"POST /employee"`).

## Structure

- Use `// given` / `// when` / `// then` comment markers inside test bodies (see `CommuteHistoryRepositoryTest`).
- Mock setup uses BDD style: `BDDMockito.given(...).willReturn(...)` — not `Mockito.when(...)`.
- Assertions: AssertJ only (`assertThat`, `assertThatThrownBy`). No JUnit `Assertions.*` or Hamcrest.
- For exception cases prefer `assertThatThrownBy(() -> ...).isInstanceOf(X.class).hasMessage(...)` over `try/catch`.

## Test data

- Reusable entity construction goes near the entity's test package or the consuming service fixture package. Prefer existing helpers such as `domain/employee/EmployeeBuilder`, `service/employee/Employees`, and `service/team/Teams`.
- Canned fixtures (a single ready-to-use instance) go in `<Entity>s.java` (e.g. `Employees.employee`).
- Controller request bodies: inline Java text blocks (`"""..."""`) holding the exact JSON. Keep one canonical `VALID_BODY` per `@Nested` group and mutate via string ops only when a field is the variable under test.

## Determinism

- Inject `Clock` (already used in services); in tests pass a fixed `Clock` or supply `ZonedDateTime` literals explicitly. Never call `ZonedDateTime.now()` directly in test arrange code.
- Time zone in tests: `ZoneId.of("Asia/Seoul")` matches the `EmployeeBuilder` default.

## Concurrency tests

- Live in `*ConcurrentTest.java` (see `EmployeeServiceConcurrentTest`). Keep them separate from regular service tests so they can be excluded from fast feedback loops if needed.

## Tooling

- `MockMvcTester` (not legacy `MockMvc`).
- `@MockitoBean` (not the old `@MockBean`).
- Test package mirrors `src/main/java/...`.
