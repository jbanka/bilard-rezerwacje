# Code Review — rezerwacje POC

**Data:** 2026-03-25
**Reviewer:** Claude Sonnet 4.6
**Scope:** Pełna baza kodu POC (src/main + src/test)
**Stack:** Spring Boot 3.4.3 · Java 21 · H2 · JJWT 0.12.6 · Spring Security · JUnit 5 / Mockito / MockMvc

---

## Summary

POC systemu rezerwacji sali w zoo. Implementuje: tworzenie i anulowanie rezerwacji, sprawdzanie dostępności slotów, autoryzację JWT (mock HS256), powiadomienia (stub log). Architektura trójwarstwowa controller→service→repository, poprawna separacja odpowiedzialności, czytelny kod. Kilka istotnych bugów i niespójności wymagających poprawki przed kolejną iteracją.

---

## Issues Found

---

### [CR-01] 🔴 Critical — `JwtService.java:31` — `.claims(Map)` nadpisuje `sub`

**Plik:** `config/JwtService.java:29-31`

```java
return Jwts.builder()
        .subject(userId)          // ustawia sub
        .claims(Map.of("email", email))  // ← nadpisuje CAŁY claims map, kasując sub
```

**Problem:** W JJWT 0.12.x metoda `.claims(Map)` zastępuje cały wewnętrzny `ClaimsMap`, co kasuje wcześniej ustawione `sub`. W efekcie `claims.getSubject()` w filtrze zwraca `null`, a `UserPrincipal.userId` jest `null` dla każdego tokena.

Fakt że testy przechodzą wynika z tego, że Mockito mockuje `JwtService` w unit testach, a w testach integracyjnych `userId` jest używane tylko do asercji na polu `ownerId` — jeśli tam trafił `null`, test mógł przejść przez zbieg okoliczności w H2.

**Fix:**
```java
// Użyj .claim() (singular) zamiast .claims(Map)
return Jwts.builder()
        .subject(userId)
        .claim("email", email)
        .issuedAt(new Date(nowMs))
        .expiration(new Date(nowMs + jwtProperties.expirationMinutes() * 60_000))
        .signWith(key())
        .compact();
```

---

### [CR-02] 🔴 Critical — `ReservationService.java:65` — `cancel()` używa `findById` bez JOIN FETCH

**Plik:** `domain/service/ReservationService.java:65`

```java
Reservation reservation = reservationRepository.findById(id)  // brak JOIN FETCH
        .orElseThrow(...);
// ...
notificationService.notifyCancelled(saved);  // ← accessed r.getGuests() → lazy load
```

**Problem:** `cancel()` używa `findById()` (bez JOIN FETCH gości), a `notifyCancelled()` iteruje po `reservation.getGuests()`. Działa tylko dlatego, że jesteśmy w `@Transactional` — Hibernate może jeszcze wykonać dodatkowy SELECT. To jest N+1 (1 SELECT na rezerwację + 1 SELECT na guests) i jest niespójne z `findById()` w serwisie, który używa `findByIdWithGuests`.

**Fix:**
```java
Reservation reservation = reservationRepository.findByIdWithGuests(id)
        .orElseThrow(() -> new NotFoundException("Rezerwacja nie istnieje: " + id));
```

---

### [CR-03] 🟠 Major — `AvailabilityController.java:30` — `ZoneOffset.of(offset)` nie jest obsługiwany jako 400

**Plik:** `api/controller/AvailabilityController.java:30`

```java
ZoneOffset zoneOffset = ZoneOffset.of(offset);  // throws DateTimeException dla "banana"
```

**Problem:** Nieprawidłowy parametr `?offset=banana` rzuca `DateTimeException`, który nie jest obsługiwany przez `GlobalExceptionHandler` jako 400 — wpada do handlera `Exception.class` i zwraca 500. Błąd użytkownika nie powinien dawać 500.

**Fix:** Opakować `ZoneOffset.of()` w try-catch i rzucić `ValidationException`, albo dodać handler `DateTimeException` w `GlobalExceptionHandler`:
```java
@ExceptionHandler(DateTimeException.class)
public ResponseEntity<ErrorResponse> handleDateTime(DateTimeException ex) {
    log.warn("Invalid date/time parameter: {}", ex.getMessage());
    return ResponseEntity.badRequest()
            .body(ErrorResponse.of(400, "Bad Request", "Nieprawidłowy format daty lub strefy czasowej."));
}
```

---

### [CR-04] 🟠 Major — `Reservation.java:18-26` — brak explicitnych nazw kolumn (`@Column(name=...)`)

**Plik:** `domain/model/Reservation.java:18-26`

```java
@Column(nullable = false)
private String ownerId;        // mapuje do: owner_id (implicit)

@Column(nullable = false)
private String ownerEmail;     // mapuje do: owner_email (implicit)

@Column(nullable = false)
private OffsetDateTime startTime;  // start_time (implicit)
// ...
```

**Problem:** Wszystkie kolumny encji `Reservation` i `ReservationGuest` opierają się na implicitnym mapowaniu nazw Hibernate (camelCase → snake_case). Narusza to CLAUDE.md (`Always define explicit @Table and @Column names`). Po zmianie strategii nazewnictwa lub przejściu na inne DB schema, mapping by się cicho posypał.

**Fix:** Dodać `name` do każdego `@Column`:
```java
@Column(name = "owner_id", nullable = false)
private String ownerId;

@Column(name = "owner_email", nullable = false)
private String ownerEmail;

@Column(name = "start_time", nullable = false)
private OffsetDateTime startTime;
// itd.
```

---

### [CR-05] 🟠 Major — `ReservationService.java:41` — ujemny czas rezerwacji daje mylący błąd

**Plik:** `domain/service/ReservationService.java:41`

```java
if (Duration.between(start, end).toMinutes() < MIN_SLOT_MINUTES) {
    throw new ValidationException("Minimalny czas rezerwacji to 30 minut.");
}
```

**Problem:** Gdy `endTime < startTime`, `Duration.between()` zwraca wartość ujemną — warunek jest spełniony i rzucany jest wyjątek o minimalnym czasie, zamiast czytelnego komunikatu "endTime musi być po startTime". Użytkownik dostaje mylący błąd.

**Fix:** Dodać osobną walidację kolejności przed sprawdzeniem długości:
```java
if (!end.isAfter(start)) {
    throw new ValidationException("endTime musi być późniejszy niż startTime.");
}
if (Duration.between(start, end).toMinutes() < MIN_SLOT_MINUTES) {
    throw new ValidationException("Minimalny czas rezerwacji to " + MIN_SLOT_MINUTES + " minut.");
}
```

---

### [CR-06] 🟠 Major — `SecurityConfig.java:41-43` — `/dev/**` i `/h2-console/**` są `permitAll()` niezależnie od profilu

**Plik:** `config/SecurityConfig.java:38-43`

```java
.requestMatchers("/h2-console/**").permitAll()
.requestMatchers("/dev/**").permitAll()
```

**Problem:** Reguły security są wkompilowane na stałe. `DevTokenController` jest `@Profile("dev")` (nie zarejestruje się w prod), ale reguła `/dev/**` jako `permitAll()` pozostaje aktywna. Każdy nowy endpoint dodany pod `/dev/` przez pomyłkę bez `@Profile("dev")` byłby niezabezpieczony bez ostrzeżenia. To samo dotyczy H2 console — jeśli zostanie przypadkowo włączona w innym profilu, jest dostępna bez auth.

**Suggestion:** Przenieść `permitAll()` dla H2 i `/dev` do konfiguracji specyficznej dla profilu `dev` (np. `@Profile("dev")` na `SecurityConfig` lub osobna konfiguracja dla profilu dev). Jako minimum: dodać komentarz dokumentujący ryzyko.

---

### [CR-07] 🟠 Major — `SecurityConfig.java:45` — `frameOptions().disable()` globalnie

**Plik:** `config/SecurityConfig.java:45`

```java
.headers(headers -> headers.frameOptions(fo -> fo.disable()))
```

**Problem:** Wyłącza ochronę clickjacking dla WSZYSTKICH odpowiedzi, nie tylko H2 console. W POC H2 potrzebuje tego, żeby wyrenderować konsolę w iframe, ale wyłączenie globalne osłabia nagłówki bezpieczeństwa dla całego API.

**Fix dla prod:**
```java
.headers(headers -> headers.frameOptions(fo -> fo.sameOrigin()))
```
Dla H2 console w dev — skonfigurować osobno przez `RequestMatcher`.

---

### [CR-08] 🟡 Minor — `ReservationRepository.java:17` — magic string `'ACTIVE'` w JPQL

**Plik:** `domain/repository/ReservationRepository.java:17,26`

```java
WHERE r.status = 'ACTIVE'
```

**Problem:** Literal string `'ACTIVE'` zamiast parametru. Jeśli enum `ReservationStatus.ACTIVE` zostanie przemianowany, JPQL cicho przestanie zwracać wyniki (brak błędu kompilacji, brak błędu runtime przy starcie — błąd dopiero przy wywołaniu). Dotyczy 2 zapytań.

**Fix:**
```java
@Query("""
    SELECT COUNT(r) FROM Reservation r
    WHERE r.status = :status
      AND r.startTime < :endTime
      AND r.endTime   > :startTime
    """)
long countConflicts(@Param("startTime") OffsetDateTime startTime,
                    @Param("endTime") OffsetDateTime endTime,
                    @Param("status") ReservationStatus status);
```
Wywołanie: `countConflicts(start, end, ReservationStatus.ACTIVE)`.

---

### [CR-09] 🟡 Minor — `Reservation.java:40` — `getGuests()` zwraca mutowalną kolekcję

**Plik:** `domain/model/Reservation.java:40,63`

```java
private List<ReservationGuest> guests = new ArrayList<>();
// ...
public List<ReservationGuest> getGuests() { return guests; }
```

**Problem:** Getter zwraca bezpośrednią referencję do wewnętrznej listy. Zewnętrzny kod może wykonać `reservation.getGuests().clear()` omijając domenę. W `ReservationService.create()` używamy `reservation.getGuests().add(...)` — co jest akceptowalne wewnątrz serwisu, ale eksponowanie mutowalnej listy na zewnątrz to zapach projektu.

**Fix:**
```java
public List<ReservationGuest> getGuests() {
    return Collections.unmodifiableList(guests);
}
```
Mutacja w serwisie przez dedykowaną metodę domenową `addGuest(ReservationGuest g)`.

---

### [CR-10] 🟡 Minor — `Reservation.java:35` — `createdAt` inicjalizowany w konstruktorze, nie przy persystencji

**Plik:** `domain/model/Reservation.java:35`

```java
@Column(nullable = false)
private OffsetDateTime createdAt = OffsetDateTime.now();
```

**Problem:** Timestamp jest ustawiany w chwili tworzenia obiektu Java, nie w chwili zapisu do DB. Przy testach może to dawać subtelne różnice między "czasem stworzenia obiektu" a "czasem zapisu". Różnica jest zazwyczaj pomijalnie mała, ale semantycznie nieprecyzyjna.

**Fix:**
```java
@Column(name = "created_at", nullable = false, updatable = false)
private OffsetDateTime createdAt;

@PrePersist
void prePersist() {
    this.createdAt = OffsetDateTime.now();
}
```

---

### [CR-11] 🟡 Minor — `AvailabilityService.java:29` — parsowanie `LocalTime` przy każdym wywołaniu

**Plik:** `domain/service/AvailabilityService.java:29-30`

```java
LocalTime start = LocalTime.parse(props.dayStart());
LocalTime end   = LocalTime.parse(props.dayEnd());
```

**Problem:** `LocalTime.parse()` wywoływany przy każdym żądaniu `/availability`. Konfiguracja się nie zmienia — warto sparsować raz w konstruktorze lub metodzie `@PostConstruct`.

**Fix:** Przenieść do pola finalnego:
```java
private final LocalTime dayStart;
private final LocalTime dayEnd;

public AvailabilityService(...) {
    ...
    this.dayStart = LocalTime.parse(props.dayStart());
    this.dayEnd   = LocalTime.parse(props.dayEnd());
}
```

---

### [CR-12] 🟡 Minor — `CreateReservationRequest.java` — brak walidacji emaili gości

**Plik:** `api/dto/CreateReservationRequest.java`

```java
public record CreateReservationRequest(
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime,
        List<String> guests          // ← brak @Valid @Email na elementach
)
```

**Problem:** `guests` przyjmuje dowolne stringi. `"not-an-email"` przejdzie przez walidację bez błędu.

**Fix:**
```java
List<@Email(message = "Nieprawidłowy adres email gościa") String> guests
```

---

### [CR-13] 🟡 Minor — `JwtAuthFilter.java:44` — cicha ignorancja błędów JWT bez logu

**Plik:** `config/JwtAuthFilter.java:44`

```java
} catch (JwtException ignored) {
    // invalid token — 401 handled by Spring Security
}
```

**Problem:** Każdy nieprawidłowy token (wygasły, zły podpis, malformed) jest cicho ignorowany. Brak możliwości debugowania "dlaczego mój token nie działa" bez dodania logu.

**Fix:**
```java
} catch (JwtException e) {
    log.debug("Invalid JWT token: {}", e.getMessage());
}
```

---

### [CR-14] 🟡 Minor — `ReservationServiceTest.java:31` — `LENIENT` na poziomie klasy

**Plik:** `test/domain/ReservationServiceTest.java:31`

```java
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationServiceTest {
```

**Problem:** `LENIENT` wyłącza detekcję "unnecessary stubbing" dla wszystkich testów w klasie. Celem tej adnotacji jest umożliwienie współdzielonych stubów w `@BeforeEach`. Tutaj nie ma `@BeforeEach` ze stubami — adnotacja maskuje potencjalne problemy z jakością testów (niechciane stuby, copy-paste).

**Fix:** Usunąć adnotację z poziomu klasy lub dodać ją tylko do testów, które rzeczywiście tego potrzebują.

---

### [CR-15] 💡 Suggestion — `ReservationControllerTest.java:47` — `ObjectMapper` tworzony w `@BeforeEach`

**Plik:** `test/api/ReservationControllerTest.java:47`

```java
@BeforeEach
void setUp() {
    mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ...
}
```

**Suggestion:** `ObjectMapper` jest re-tworzony przed każdym testem. Można `@Autowired ObjectMapper mapper` (Spring Boot autoconfigures it with JavaTimeModule) albo przenieść do pola `private static final`.

---

### [CR-16] 💡 Suggestion — `ReservationController.java:39` — `list()` zwraca wszystkie rezerwacje

**Plik:** `api/controller/ReservationController.java:39`

```java
@GetMapping
public ResponseEntity<List<ReservationResponse>> list() {
    List<ReservationResponse> result = reservationService.findAll().stream()
```

**Suggestion:** `GET /api/v1/reservations` zwraca wszystkie rezerwacje wszystkich użytkowników bez filtrowania. W POC akceptowalne, ale przed produkcją wymaga decyzji: "tylko moje" vs. "wszystkie" (admin).

---

### [CR-17] 💡 Suggestion — `DevTokenController.java:32` — brak walidacji parametrów

**Plik:** `dev/DevTokenController.java:32`

```java
public ResponseEntity<Map<String, String>> token(
        @RequestParam String userId,
        @RequestParam String email) {
```

**Suggestion:** Pusty `userId` lub `email` (np. `?userId=&email=`) generuje token z pustymi claimami. Dodać `@NotBlank` lub prostą walidację.

---

## Positive Observations

- **Architektura:** Czysta separacja warstw controller→service→repository. Żadna logika biznesowa nie przecieka do kontrolerów.
- **`@Transactional` wyłącznie w serwisie** — poprawne, spójne zastosowanie.
- **`@Transactional(readOnly = true)`** używane świadomie dla read paths — `findById`, `findAll`, `forDate`.
- **JOIN FETCH naprawiony w porę** — `findAllWithGuests` i `findByIdWithGuests` rozwiązują LazyInitializationException bez EAGER fetch na encji.
- **Wyjątki domenowe** — oddzielne klasy per przypadek błędu, `GlobalExceptionHandler` jako jedyny punkt mapowania na HTTP. Brak try/catch w kontrolerach.
- **`@ConfigurationProperties` records** — `JwtProperties`, `AvailabilityProperties` — nowoczesny, type-safe pattern konfiguracji.
- **`UserPrincipal` jako record** — minimalistyczny, immutable.
- **`@Profile("dev")` na `DevTokenController`** — zabezpieczenie przed wyciekiem endpointu generującego tokeny na środowiska wyższe.
- **Overlap query w JPQL** — `startTime < :endTime AND endTime > :startTime` — poprawna logika przecięcia przedziałów.
- **`Duration.between().toMinutes()`** — czytelna walidacja minimalnego slotu.
- **Testy jednostkowe** — dobre pokrycie happy path i edge cases dla `ReservationService`. Asercje na typach wyjątków i treści komunikatów.
- **Testy integracyjne z prawdziwym JWT** — zamiast `SecurityMockMvcRequestPostProcessors`, testy używają `JwtService.generate()` co testuje realny przepływ filtra.
- **`@Transactional` na klasie testowej** — każdy test jest izolowany przez rollback, brak zanieczyszczeń między testami.

---

## Summary of Issues

| ID    | Severity    | Area          | Opis                                                        |
|-------|-------------|---------------|-------------------------------------------------------------|
| CR-01 | 🔴 Critical  | Security/JWT  | `.claims(Map)` nadpisuje `sub` — `userId` może być `null`   |
| CR-02 | 🔴 Critical  | JPA/N+1       | `cancel()` używa `findById` zamiast `findByIdWithGuests`     |
| CR-03 | 🟠 Major     | API           | `ZoneOffset.of()` rzuca 500 dla złego parametru offset      |
| CR-04 | 🟠 Major     | JPA           | Brak explicitnych `@Column(name=...)` na encji               |
| CR-05 | 🟠 Major     | Domain        | `endTime < startTime` daje mylący błąd walidacji             |
| CR-06 | 🟠 Major     | Security      | `/dev/**` i `/h2-console/**` `permitAll()` niezależnie od profilu |
| CR-07 | 🟠 Major     | Security      | `frameOptions().disable()` globalnie                        |
| CR-08 | 🟡 Minor     | JPA           | Magic string `'ACTIVE'` w JPQL                              |
| CR-09 | 🟡 Minor     | Domain        | `getGuests()` zwraca mutowalną kolekcję                     |
| CR-10 | 🟡 Minor     | JPA           | `createdAt` inicjalizowany w polu, nie w `@PrePersist`       |
| CR-11 | 🟡 Minor     | Performance   | `LocalTime.parse()` przy każdym żądaniu                     |
| CR-12 | 🟡 Minor     | Validation    | Brak `@Email` na elementach listy gości                     |
| CR-13 | 🟡 Minor     | Security/Log  | Cicha ignorancja błędów JWT — brak `log.debug`              |
| CR-14 | 🟡 Minor     | Tests         | `LENIENT` na poziomie klasy bez potrzeby                    |
| CR-15 | 💡 Suggestion| Tests         | `ObjectMapper` tworzony w `@BeforeEach`                     |
| CR-16 | 💡 Suggestion| API           | `list()` bez filtra użytkownika                             |
| CR-17 | 💡 Suggestion| API           | Brak walidacji parametrów `DevTokenController`              |

---

## Verdict

**APPROVE WITH CHANGES**

POC spełnia swoje zadanie — logika domenowa jest poprawna, architektura czysta, testy działają. Dwa critical wymagają naprawy przed kolejną iteracją (CR-01, CR-02). Trzy major (CR-03, CR-05, CR-06) warto zaadresować zanim POC stanie się bazą dla kolejnych środowisk. Pozostałe można planować w ramach przejścia do `PLAN_DEV.md`.
