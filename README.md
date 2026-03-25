## Historia projektu i praca z Claude Code

Projekt budowany w parze z **Claude Code** (model: Claude Sonnet 4.6) w trybie krok-po-kroku.

---

### Branch: `poc-pre-code-review-fixes`

Pierwsza działająca wersja POC. Zbudowana krok po kroku zgodnie z `PLAN_DEV_POC.md`:

| Krok | Co zostało zrobione                                              |
|------|------------------------------------------------------------------|
| 0    | `pom.xml` — Spring Boot 3.4.3, JJWT, SpringDoc, H2              |
| 1    | Model domenowy: `Reservation`, `ReservationGuest`, `ReservationStatus` |
| 2    | `ReservationRepository` — JPQL overlap query (`countConflicts`)  |
| 3    | `ReservationService` — logika: min 30 min, conflict check, cancel z owner check |
| 4    | Wyjątki domenowe + `NotificationService` (stub → log)            |
| 5    | Security: `JwtAuthFilter`, `JwtService`, `UserPrincipal`, `SecurityConfig` |
| 6    | DTO + `ReservationController` + `GlobalExceptionHandler`         |
| 7    | `AvailabilityService` + `AvailabilityController` (sloty co 30 min) |
| 8    | `DevTokenController` (`@Profile("dev")`)                         |
| 9    | `application.yml` + `RezerwacjeApplication`                      |
| 10   | Testy jednostkowe — `ReservationServiceTest` (9 testów Mockito)  |
| 11   | Testy integracyjne — `ReservationControllerTest` (7 testów MockMvc) |
| 12   | `mvn test` → **16/16 PASS**                                      |

Po zbudowaniu POC przeprowadzony został **manualny test E2E** (`e2e-manual-poc.md`):
14 scenariuszy (4 użytkowników, konflikty, anulowania, dostępność), wynik: **14/14 PASS**.
Podczas testu znaleziono i naprawiono bug: `LazyInitializationException` przy `GET /reservations`
(brak `JOIN FETCH` dla kolekcji `guests` poza transakcją).

---

### Code Review — `code_review_poc.md`

Wyczerpujący przegląd kodu wykonany przez Claude Code zgodnie z instrukcją `.claude/code_review_instr.md`.
Przegląd uwzględniał specyfikę stacku: JJWT, Hibernate lazy loading, Spring Security filter chain, MockMvc.

Znalezione problemy (15 issues):

| ID    | Severity   | Problem                                                             |
|-------|------------|---------------------------------------------------------------------|
| CR-01 | Major      | `JwtService`: `.claims(Map)` nadpisywało `sub` — błąd JJWT API    |
| CR-02 | Major      | `cancel()`: `findById` zamiast `findByIdWithGuests` → LazyInit     |
| CR-03 | Major      | Brak obsługi `DateTimeException` → niekontrolowany 500             |
| CR-04 | Minor      | Brak `@Column(name=...)` na polach encji                           |
| CR-05 | Minor      | Brak walidacji `endTime > startTime`                               |
| CR-06 | Minor      | Brak komentarzy przy `permitAll()` dla `/h2-console`, `/dev/**`    |
| CR-07 | Minor      | `frameOptions.deny()` blokuje H2 console w dev                     |
| CR-08 | Minor      | Magic string `'ACTIVE'` w JPQL zamiast parametru enum              |
| CR-09 | Minor      | `getGuests()` zwraca mutowalną listę                               |
| CR-10 | Minor      | `createdAt` inicjalizowane w polu zamiast `@PrePersist`            |
| CR-11 | Minor      | `LocalTime.parse()` wywoływane przy każdym żądaniu                 |
| CR-12 | Minor      | Brak `@Email` na elementach listy gości                            |
| CR-13 | Minor      | `catch (JwtException ignored)` — brak logowania                    |
| CR-14 | Minor      | `@MockitoSettings(LENIENT)` na poziomie klasy bez uzasadnienia     |
| CR-15 | Suggestion | `new ObjectMapper()` w teście zamiast `@Autowired`                 |

---

### Branch: `poc-post-code-review-fixes`

Wszystkie 15 issues z code review wdrożone. Szczegóły w `post-code-review-fix.md`.

Kluczowe zmiany:

```
JwtService          .claims(Map) → .claim("email", email)          — CR-01: fix błędu JJWT
ReservationService  cancel(): findById → findByIdWithGuests         — CR-02: fix LazyInit
GlobalExceptionHandler  + DateTimeException → 400                   — CR-03
Reservation         @Column(name=...) explicit na wszystkich polach — CR-04
ReservationService  walidacja endTime > startTime                   — CR-05
SecurityConfig      komentarze + frameOptions.sameOrigin()          — CR-06/07
ReservationRepository  'ACTIVE' → parametr ReservationStatus        — CR-08
Reservation         getGuests() → unmodifiableList + addGuest()     — CR-09
Reservation         createdAt → @PrePersist                         — CR-10
AvailabilityService LocalTime.parse() cachowane w konstruktorze     — CR-11
CreateReservationRequest  @Email na elementach listy guests         — CR-12
JwtAuthFilter       catch (JwtException) → log.debug(...)           — CR-13
ReservationServiceTest  usunięto @MockitoSettings(LENIENT)          — CR-14
ReservationControllerTest  @Autowired ObjectMapper                  — CR-15
```

Po fixach: ponowny test E2E (`e2e-manual-poc-post-review-fix.md`) — **14/14 PASS**, zero regresji.

Następnie wykonany **drugi code review** (`code_review_poc_fin.md`) — weryfikacja stanu po fixach.
Znaleziono 12 nowych obserwacji (bez Critical), verdict: **APPROVE WITH CHANGES**.

---

### Dokumenty w repozytorium

| Plik                              | Opis                                                        |
|-----------------------------------|-------------------------------------------------------------|
| `PLAN.md`                         | Wymagania biznesowe                                         |
| `PLAN_DEV_POC.md`                 | Szczegółowy plan techniczny POC (wygenerowany przez Claude) |
| `PLAN_DEV.md`                     | Plan kolejnego etapu (PostgreSQL, Cognito, SQS)             |
| `POSTEP_DEV_POC.md`               | Dziennik postępu — krok po kroku, z uwagami implementacyjnymi |
| `code_review_poc.md`              | Code review przed fixami (15 issues)                        |
| `post-code-review-fix.md`         | Lista wdrożonych fixów (CR-01 – CR-15)                      |
| `e2e-manual-poc.md`               | Manualny test E2E przed fixami — 14/14 PASS                 |
| `e2e-manual-poc-post-review-fix.md` | Manualny test E2E po fixach — 14/14 PASS, zero regresji   |
| `CLAUDE.md`                       | Standardy inżynierskie dla Claude Code (stałe dla projektu) |

---

## Co dalej (po POC)

Zgodnie z `PLAN_DEV.md`:

1. **Baza danych** — H2 → PostgreSQL + Flyway migrations (`V1__init.sql`)
2. **Autoryzacja** — mock JWT (HS256) → AWS Cognito OIDC
3. **Powiadomienia** — `NotificationService` log stub → AWS SQS + SES
4. **ID generation** — `GenerationType.UUID` → `GenerationType.SEQUENCE`
5. **Infrastruktura** — Dockerfile + AWS ECS Fargate + Terraform/CDK
6. **Konfiguracja** — `application-prod.yml`, secrets w AWS Secrets Manager
7. **Monitoring** — structured logging (JSON), CloudWatch
