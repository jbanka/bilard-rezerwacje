# PLAN_DEV_POC — Proof of Concept

## Cel POC

Udowodnić, że:
1. Logika konfliktu rezerwacji działa poprawnie (brak nakładek, min 30 min)
2. REST API przyjmuje i zwraca rezerwacje
3. Anulowanie rezerwacji działa
4. Powiadomienie e-mail odpala się przy create/cancel (stub — tylko log)
5. Autoryzacja przez JWT działa (mock token, bez prawdziwego Cognito)

POC to NIE jest produkcja. Celem jest szybka weryfikacja konceptu, nie pełna implementacja.

---

## Co odpuszczamy w POC

| Temat                  | Decyzja POC                                          |
|------------------------|------------------------------------------------------|
| Baza danych            | H2 in-memory (zamiast PostgreSQL)                    |
| Migracje Flyway        | Hibernate `ddl-auto: create-drop`                    |
| AWS Cognito SSO        | Mock JWT (statyczny token w konfiguracji)            |
| AWS SES / SQS          | `NotificationService` loguje na stdout               |
| Docker / ECS           | Pomijamy — lokalny `mvn spring-boot:run`             |
| Terraform / CDK        | Pomijamy                                             |
| Pełne testy E2E        | Kilka testów integracyjnych z MockMvc                |

---

## Stack POC

```
Spring Boot 3.4
Java 21
H2 (in-memory)
Spring Security (JWT — statyczny mock secret)
SpringDoc OpenAPI 2.x (Swagger UI pod /swagger-ui.html)
JUnit 5 + MockMvc
Maven
```

---

## Zakres implementacji

### 1. Model (JPA / H2)

```
Reservation
  - id: UUID
  - ownerId: String          (z JWT subject)
  - ownerEmail: String
  - startTime: OffsetDateTime
  - endTime: OffsetDateTime
  - status: ACTIVE | CANCELLED

ReservationGuest
  - id: UUID
  - reservationId: UUID (FK)
  - email: String
```

### 2. Reguły biznesowe (ReservationService)

```
create():
  - endTime - startTime >= 30 minut                 → else 400
  - brak ACTIVE rezerwacji w tym przedziale         → else 409
  - zapis do H2
  - NotificationService.notifyCreated(reservation)  → log

cancel():
  - reservation musi należeć do caller (ownerId)    → else 403
  - status musi być ACTIVE                          → else 409
  - status = CANCELLED
  - NotificationService.notifyCancelled(reservation) → log
```

### 3. API REST

```
POST   /api/v1/reservations           → utwórz
GET    /api/v1/reservations           → lista (opcjonalny ?date=2025-04-01)
GET    /api/v1/reservations/{id}      → szczegóły
DELETE /api/v1/reservations/{id}      → anuluj
GET    /api/v1/availability?date=...  → wolne sloty 08:00–22:00 co 30 min
```

### 4. Autoryzacja (mock JWT)

Spring Security waliduje JWT podpisany symetrycznym kluczem (`HS256`).
W POC używamy statycznego secret z `application.yml`.
`ownerId` = `sub` z tokena, `ownerEmail` = claim `email`.

Generowanie tokena do testów — endpoint pomocniczy (tylko profil `dev`):
```
GET /dev/token?userId=user1&email=user1@example.com
```

### 5. NotificationService (stub)

```java
// POC — tylko log
log.info("[NOTIFY] Created: reservationId={}, owner={}, guests={}",
         reservation.getId(), reservation.getOwnerEmail(), guests);
```

---

## Struktura projektu POC

```
src/main/java/org/example/rezerwacje/
├── RezerwacjeApplication.java
├── config/
│   ├── SecurityConfig.java          # JWT mock
│   └── OpenApiConfig.java
├── domain/
│   ├── Reservation.java             # @Entity
│   ├── ReservationGuest.java        # @Entity
│   ├── ReservationStatus.java       # enum
│   ├── ReservationRepository.java   # JpaRepository + conflict query
│   └── ReservationService.java      # logika biznesowa
├── api/
│   ├── ReservationController.java
│   ├── AvailabilityController.java
│   ├── dto/
│   │   ├── CreateReservationRequest.java
│   │   ├── ReservationResponse.java
│   │   └── AvailabilityResponse.java
│   └── GlobalExceptionHandler.java
├── notification/
│   └── NotificationService.java     # stub (log only)
└── dev/
    └── DevTokenController.java      # tylko profil dev
```

```
src/main/resources/
├── application.yml
└── application-dev.yml

src/test/java/org/example/rezerwacje/
├── domain/
│   └── ReservationServiceTest.java  # testy jednostkowe reguł
└── api/
    └── ReservationControllerTest.java  # MockMvc integracja
```

---

## Kluczowe fragmenty implementacji

### Zapytanie conflict check (JPQL)

```java
@Query("""
    SELECT COUNT(r) FROM Reservation r
    WHERE r.status = 'ACTIVE'
      AND r.startTime < :endTime
      AND r.endTime   > :startTime
""")
long countConflicts(OffsetDateTime startTime, OffsetDateTime endTime);
```

### application.yml (POC)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:rezerwacje;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  h2:
    console:
      enabled: true   # dostępna pod /h2-console

app:
  jwt:
    secret: my-super-secret-key-for-poc-only-32chars
    expiration-minutes: 60
  availability:
    day-start: "08:00"
    day-end:   "22:00"
    slot-minutes: 30
```

---

## Testy do napisania

### Jednostkowe — `ReservationServiceTest`

| Test                                                  | Oczekiwany wynik |
|-------------------------------------------------------|------------------|
| Rezerwacja 60 min, brak konfliktu                     | OK               |
| Rezerwacja 29 min                                     | 400 Bad Request  |
| Rezerwacja nachodzi na istniejącą (overlap)           | 409 Conflict     |
| Rezerwacja styka się (end = start innej)              | OK (brak overlap)|
| Anulowanie własnej rezerwacji                         | OK               |
| Anulowanie cudzej rezerwacji                          | 403 Forbidden    |
| Anulowanie już anulowanej rezerwacji                  | 409 Conflict     |

### Integracyjne — `ReservationControllerTest` (MockMvc)

| Test                                      | Oczekiwany HTTP |
|-------------------------------------------|-----------------|
| POST /reservations — poprawna             | 201 Created      |
| POST /reservations — konflikt             | 409             |
| DELETE /reservations/{id} — własna        | 200             |
| DELETE /reservations/{id} — cudza         | 403             |
| GET /availability?date=2025-04-01         | 200 + lista     |
| Brak tokena JWT                           | 401             |

---

## Kryteria zaliczenia POC

- [ ] `mvn spring-boot:run` startuje bez błędów
- [ ] Swagger UI działa pod `/swagger-ui.html`
- [ ] POST rezerwacji tworzy wpis w H2
- [ ] Dwie nakładające się rezerwacje zwracają 409
- [ ] Rezerwacja < 30 min zwraca 400
- [ ] DELETE anuluje rezerwację właściciela
- [ ] DELETE cudzej rezerwacji zwraca 403
- [ ] GET /availability zwraca wolne sloty na dany dzień
- [ ] Wszystkie testy jednostkowe i integracyjne zielone
- [ ] Przy create/cancel widać log z powiadomieniem

---

## Co dalej po POC

Po zaliczeniu POC przechodzimy do `PLAN_DEV.md`:

1. Zamiana H2 → PostgreSQL + Flyway
2. Zamiana mock JWT → AWS Cognito OIDC
3. Zamiana log stub → AWS SQS + SES
4. Dockeryzacja + deploy na ECS Fargate
