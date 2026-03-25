# CLAUDE.md

(Task-agnostic. Applied to all code in this repository by all team members.)

---

## Activation

This file defines engineering standards, not a task list.
Do ONLY what the current prompt asks for.
Sections below are reference material — apply only what is relevant to the current instruction.

---

## Stack

| Layer         | Technology                                      |
|---------------|-------------------------------------------------|
| Runtime       | Java 21 (compiled target); JVM may be newer    |
| Framework     | Spring Boot 3.4.3                               |
| Build         | Maven                                           |
| Persistence   | Spring Data JPA / Hibernate 6.6                 |
| Database      | H2 in-memory (POC) → PostgreSQL (target)        |
| Security      | Spring Security + custom JWT filter (JJWT 0.12.6, HS256) → AWS Cognito OIDC (target) |
| API docs      | SpringDoc OpenAPI 2.8.5 (`/swagger-ui.html`)   |
| Testing       | JUnit 5, Mockito, MockMvc, spring-security-test |
| Lombok        | **Not used** — explicit constructors throughout |

---

## Architecture

- Layered: `controller → service → repository`. No layer skipping.
- **Controllers** handle HTTP only: request parsing, `@Valid` delegation, `ResponseEntity` mapping.
- **Services** are the single source of truth for all business rules and `@Transactional` boundaries.
- **Repositories** handle persistence only — no logic.
- Package structure: **package-by-feature** under `org.example.rezerwacje`:
  ```
  api/controller, api/dto, api/exception
  domain/model, domain/repository, domain/service
  config/
  notification/
  dev/          ← profile=dev only
  ```

---

## Dependency Injection

- **Constructor injection exclusively.** No `@Autowired` on fields, no setter injection.
- All injected fields are `final`.
- No Lombok — write explicit constructors. One constructor per class unless there is a clear reason.
- Do not inject `ApplicationContext` except in infrastructure/bootstrap code.

---

## Java Style

- Target: **Java 21**. Do not use preview features or language constructs above Java 21.
- **Records** for all immutable DTOs, value objects, and `@ConfigurationProperties` (`JwtProperties`, `AvailabilityProperties`).
- **Enums** for fixed domain states (`ReservationStatus`).
- `Optional` as return type only — never as a field or parameter.
- No raw types. No unchecked casts without `@SuppressWarnings` + explanatory comment.
- Avoid `null` in domain code. Use `Optional` or explicit sentinel values.
- Use `var` where the type is obvious from the right-hand side; avoid it when it obscures intent.
- `OffsetDateTime` for all timestamps — never `LocalDateTime` (timezone-aware storage required).

---

## Spring Boot

- All configuration externalized via `application.yml`. No hardcoded values in `@Value`.
- Profiles: `dev` (H2 console, DevTokenController), `test` (test DB), `prod` (PostgreSQL, no dev endpoints).
- Bind config to `@ConfigurationProperties` records — not `@Value` strings scattered in beans.
- Avoid `@ComponentScan` customization unless strictly necessary.
- `spring.jpa.hibernate.ddl-auto=validate` in production; `create-drop` only in POC/test.

---

## Hibernate / JPA

- Always define explicit `@Table(name = "...")` and `@Column(name = "...")`. No implicit naming.
- `FetchType.LAZY` by default on all associations. Override to EAGER only with justification comment.
- Avoid bidirectional associations unless there is a clear query-driven reason.
- **`JOIN FETCH` required** when accessing lazy collections outside the originating `@Transactional` method. Add dedicated repository queries (`findAllWithGuests`, `findByIdWithGuests`) rather than accessing lazy proxies in the controller or mapper.
- Never call `entityManager.flush()` / `clear()` in business logic.
- ID generation: `GenerationType.UUID` (POC/H2); switch to `GenerationType.SEQUENCE` for PostgreSQL in production.
- Never expose JPA entities outside the service layer — return domain objects; controllers map to DTOs.

---

## Database

- **POC:** H2 in-memory, `ddl-auto: create-drop`. No migrations needed.
- **Production target:** PostgreSQL + Flyway migrations. All schema changes via versioned scripts (`V1__...sql`). No DDL in application code.
- JPQL preferred. Native SQL only when JPQL cannot express the query — document why.

---

## Security

- Authentication: stateless JWT, `SessionCreationPolicy.STATELESS`.
- JWT validated in `JwtAuthFilter` (JJWT `Jwts.parser().verifyWith(key())`). Claims: `sub` = userId, `email`.
- Authenticated user available via `@AuthenticationPrincipal UserPrincipal` in controllers.
- `UserPrincipal` is a record — never expose it outside the `api` or `config` layers.
- CSRF disabled — stateless API, no browser session cookies.
- `/dev/**` permitted only under profile `dev`. Never expose in `prod`.
- Swagger UI and H2 console are `permitAll()` — review before moving to production.
- Do not disable Spring Security without an explicit comment explaining why and where it is re-enabled.

---

## API / REST Conventions

- Controllers accept and return **DTOs** (records), never JPA entities.
- Use `ResponseEntity<>` for explicit HTTP status control.
- `POST` → 201 Created with `Location` header.
- `DELETE` → 200 OK with updated resource (to confirm state change).
- API versioning in path: `/api/v1/...`.
- Input validation: `@Valid` + Bean Validation annotations on request records.
- All error responses use `ErrorResponse` record via `GlobalExceptionHandler`.

---

## Error Handling

- **`GlobalExceptionHandler` (`@RestControllerAdvice`)** is the single place for exception-to-HTTP mapping.
- No `try/catch` in controllers or repositories.
- Domain exceptions in `api/exception`:
  - `ValidationException` → 400
  - `ConflictException` → 409
  - `ForbiddenException` → 403
  - `NotFoundException` → 404
- Never leak internal stack traces or Hibernate messages in HTTP responses.
- Log with context before converting exceptions: `log.warn("...", id, e.getMessage())`.

---

## Logging

- SLF4J: `private static final Logger log = LoggerFactory.getLogger(ClassName.class);`
- **Parameterized logging only:** `log.info("Reservation created id={}", id)` — never string concatenation.
- Do not log PII (email, names), credentials, tokens, or full request/response bodies at INFO level.
- Log levels:
  - `ERROR` — broken, needs immediate attention
  - `WARN` — unexpected but handled
  - `INFO` — significant business events (reservation created, cancelled)
  - `DEBUG` — internal flow (dev/troubleshooting only)
- `NotificationService` stubs use `log.info("[NOTIFY] ...")` — acceptable for POC; replace with SQS/SES in production.

---

## Testing

Follow rules in `.claude/rules/testing.md`.

**Quick reference:**
- Unit tests: `ReservationServiceTest` — Mockito, `@ExtendWith(MockitoExtension.class)`.
  Use `@MockitoSettings(strictness = LENIENT)` only when stubs are shared across tests with different paths.
- Integration tests: `ReservationControllerTest` — `@SpringBootTest`, `MockMvc`, profile `test`.
  Use `SecurityMockMvcRequestPostProcessors.user(...)` or inject a real JWT via `JwtService` in tests.
- Test the business rule, not the framework. Avoid testing that Spring wires beans correctly.
- ByteBuddy note: JVM > 21 requires `-Dnet.bytebuddy.experimental=true` in Surefire config (already set in `pom.xml`).

---

## Guardrails

- Do not commit secrets, tokens, credentials, or environment-specific URLs.
- `application-local.yml` and `application-secrets.yml` are in `.gitignore` — use them for local overrides.
- Do not use `System.out.println` — SLF4J only.
- No `TODO` comments in committed code without a linked issue reference.
- No `@SuppressWarnings` without an explanatory comment.
- `GenerationType.UUID` is fine for POC. Before switching to PostgreSQL: change to `SEQUENCE` and add a Flyway migration.
- The `dev` profile exposes `DevTokenController` which issues arbitrary JWTs. Ensure it is unreachable in production — verify via `@Profile("dev")` annotation.
