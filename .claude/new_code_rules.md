# .claude/rules/new_code.md
# Team-wide standards for writing new code and implementing new features
# Task-agnostic. Always applies when adding or extending functionality in this repository.

---

## Design Before Code

- Before writing implementation, identify: inputs, outputs, failure modes, and transactional boundaries.
- New service classes must have a defined single responsibility stated as a one-sentence comment at the top of the class.
- New REST endpoints must have explicit request/response DTOs — do not expose JPA entities directly.
- Define the interface (method signatures, DTOs) before the implementation when working across layers.

---

## Spring Boot

- New controllers must be `@RestController` with explicit `@RequestMapping` base path.
- All new endpoints must return explicit HTTP status codes — no implicit `200 OK` for everything.
- Input validation via Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`) on request DTOs, not in service logic.
- New configuration properties must be bound via `@ConfigurationProperties`, not scattered `@Value` fields.

---

## Hibernate / JPA

- New entities must have explicit `@Table(name = "...")` and `@Column(name = "...")` on all fields.
- New associations default to `FetchType.LAZY` — document any exception with a comment.
- New repository methods with complex queries use `@Query` with JPQL — no method name queries beyond simple finders.
- Every new entity must have `equals()` and `hashCode()` based on business key, not on `id`.

---

## Testing

- New code is not complete without tests. Unit tests are mandatory for all new service methods.
- New repository methods require at least one `@DataJpaTest` slice test.
- New REST endpoints require at least one `@WebMvcTest` covering happy path and main failure path.

---

## Coverage Guardrails

- New service classes: ≥ 80% line coverage before merge.
- New utility/helper classes: 100% line coverage — they are simple enough to fully test.
- TODO: confirm enforced Jacoco thresholds for this project

---

## Anti-patterns to Avoid

- Do not reuse existing service classes for unrelated new functionality — create a new focused service.
- Do not return `null` from service methods — use `Optional` or throw a domain exception.
- Do not add new `@Autowired` field injections — constructor injection only.
- Do not skip DTOs and pass raw `Map<String, Object>` as request/response bodies.
- Do not add business logic to entity classes — keep entities as pure data containers.
