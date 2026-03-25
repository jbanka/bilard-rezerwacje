# PLAN_DEV — Rezerwacje stołu bilardowego

## Kontekst

- Jeden stół bilardowy
- Brak nakładających się rezerwacji
- Minimalny slot: 30 minut
- Możliwość anulowania rezerwacji
- Powiadamianie dodatkowych osób o rezerwacji
- Tylko serwisy (bez frontendu)
- Deployment: AWS
- Autoryzacja: SSO (enterprise)

---

## Stack technologiczny

| Warstwa            | Technologia                                      |
|--------------------|--------------------------------------------------|
| Język              | Java 21 (LTS)                                    |
| Framework          | Spring Boot 3.x                                  |
| Build              | Maven                                            |
| Baza danych        | PostgreSQL (AWS RDS)                             |
| Migracje DB        | Flyway                                           |
| Autoryzacja        | Spring Security + OAuth2/OIDC (AWS Cognito SSO)  |
| Powiadomienia      | AWS SES (e-mail) / AWS SNS (opcjonalnie SMS)     |
| Messaging          | AWS SQS (async powiadomienia)                    |
| API                | REST (JSON)                                      |
| Dokumentacja API   | SpringDoc OpenAPI (Swagger UI)                   |
| Testy              | JUnit 5, Mockito, Testcontainers                 |
| Konteneryzacja     | Docker + AWS ECS (Fargate)                       |
| CI/CD              | GitHub Actions                                   |

---

## Architektura

Monolityczny serwis (single deployable unit) z wyraźnym podziałem wewnętrznym na warstwy.
Przy wzroście złożoności łatwy do rozbicia na mikroserwisy.

```
[Klient (API)] --> [API Gateway / ALB] --> [Reservation Service]
                                                  |
                        +-----------------------+-+--------------+
                        |                       |                |
                   [PostgreSQL]            [AWS SQS]        [AWS Cognito]
                   (RDS)             (kolejka powiadomień)   (SSO/OIDC)
                                              |
                                         [AWS SES]
                                      (e-mail notyfikacje)
```

---

## Model domenowy

### Encje

#### `Reservation`
| Pole              | Typ              | Opis                                  |
|-------------------|------------------|---------------------------------------|
| id                | UUID             | PK                                    |
| ownerId           | String           | ID użytkownika z SSO                  |
| ownerEmail        | String           | E-mail właściciela                    |
| startTime         | OffsetDateTime   | Początek rezerwacji                   |
| endTime           | OffsetDateTime   | Koniec rezerwacji                     |
| status            | Enum             | ACTIVE, CANCELLED                     |
| createdAt         | OffsetDateTime   | Timestamp utworzenia                  |
| cancelledAt       | OffsetDateTime   | Timestamp anulowania (nullable)       |

#### `ReservationGuest`
| Pole              | Typ    | Opis                                        |
|-------------------|--------|---------------------------------------------|
| id                | UUID   | PK                                          |
| reservationId     | UUID   | FK -> Reservation                           |
| email             | String | E-mail osoby powiadamianej                  |
| notifiedAt        | OffsetDateTime | Timestamp wysłania powiadomienia   |

### Reguły biznesowe
1. `endTime - startTime >= 30 minut`
2. Nowa rezerwacja nie może nakładać się z żadną ACTIVE rezerwacją:
   `NOT (newStart < existingEnd AND newEnd > existingStart)`
3. Tylko właściciel rezerwacji może ją anulować
4. Anulowanie możliwe tylko dla statusu ACTIVE
5. Po utworzeniu / anulowaniu — powiadomienie e-mail do właściciela i wszystkich gości

---

## API REST

Base path: `/api/v1`

### Reservations

| Metoda | Endpoint                        | Opis                                       | Auth    |
|--------|---------------------------------|--------------------------------------------|---------|
| POST   | `/reservations`                 | Utwórz rezerwację                          | SSO     |
| GET    | `/reservations`                 | Lista rezerwacji (filtr: date, status)     | SSO     |
| GET    | `/reservations/{id}`            | Szczegóły rezerwacji                       | SSO     |
| DELETE | `/reservations/{id}`            | Anuluj rezerwację                          | SSO     |
| POST   | `/reservations/{id}/guests`     | Dodaj gości do powiadomienia               | SSO     |

### Availability

| Metoda | Ścieżka                         | Opis                                        | Auth    |
|--------|---------------------------------|---------------------------------------------|---------|
| GET    | `/availability`                 | Wolne sloty dla podanego dnia/zakresu       | SSO     |

### Przykładowy request — tworzenie rezerwacji

```json
POST /api/v1/reservations
{
  "startTime": "2025-04-01T18:00:00+02:00",
  "endTime":   "2025-04-01T19:30:00+02:00",
  "guests": ["jan@example.com", "anna@example.com"]
}
```

---

## Struktura pakietów

```
org.example.rezerwacje
├── config/              # Spring Security, AWS beans, OpenAPI
├── domain/
│   ├── model/           # Reservation, ReservationGuest (JPA entities)
│   ├── repository/      # JPA repositories
│   └── service/         # ReservationService, AvailabilityService
├── api/
│   ├── controller/      # ReservationController, AvailabilityController
│   ├── dto/             # Request/Response DTO
│   └── exception/       # GlobalExceptionHandler, domain exceptions
└── notification/
    ├── NotificationService
    └── SqsMessagePublisher / SesEmailSender
```

---

## Fazy implementacji

### Faza 1 — Fundament (setup)
- [ ] Aktualizacja `pom.xml`: Spring Boot 3, PostgreSQL, Flyway, Spring Security OAuth2
- [ ] Konfiguracja bazy: encje + migracje Flyway (V1, V2)
- [ ] Docker Compose do lokalnego dev (Postgres + LocalStack)
- [ ] Konfiguracja Spring Security z JWT/OIDC (mock dla testów)

### Faza 2 — Core biznes
- [ ] `ReservationService.create()` z walidacją konfliktu i slotu 30 min
- [ ] `ReservationService.cancel()` z walidacją właściciela
- [ ] `AvailabilityService.getFreeSlots()` dla podanego dnia
- [ ] Testy jednostkowe dla reguł biznesowych

### Faza 3 — API REST
- [ ] `ReservationController` (CRUD + anulowanie)
- [ ] `AvailabilityController`
- [ ] GlobalExceptionHandler (409 Conflict, 400 Bad Request, 403 Forbidden)
- [ ] Dokumentacja OpenAPI
- [ ] Testy integracyjne z Testcontainers (Postgres)

### Faza 4 — Powiadomienia
- [ ] `NotificationService` — interfejs + implementacja SES
- [ ] `SqsMessagePublisher` — async publish po create/cancel
- [ ] SQS consumer — wysyłka maili do właściciela i gości
- [ ] Obsługa `ReservationGuest` — zapis + powiadomienie

### Faza 5 — AWS / Deployment
- [ ] Dockerfile (multi-stage build)
- [ ] AWS Cognito — konfiguracja User Pool + App Client (SSO)
- [ ] Terraform / CDK: RDS, ECS Fargate, ALB, SQS, SES, Cognito
- [ ] GitHub Actions CI/CD pipeline (test → build → push ECR → deploy ECS)
- [ ] AWS Secrets Manager dla credentiali DB

### Faza 6 — Jakość i observability
- [ ] Spring Actuator + CloudWatch metrics
- [ ] Structured logging (JSON) → CloudWatch Logs
- [ ] Testy E2E (smoke tests po deploy)

---

## Konfiguracja środowisk

| Parametr               | Local (dev)               | AWS (prod)                      |
|------------------------|---------------------------|---------------------------------|
| DB                     | Docker Postgres            | RDS PostgreSQL                  |
| SSO                    | Keycloak / mock JWT        | AWS Cognito                     |
| E-mail                 | LocalStack SES / Mailhog   | AWS SES (verified domain)       |
| Queue                  | LocalStack SQS             | AWS SQS                         |
| Secrets                | `application-local.yml`    | AWS Secrets Manager             |

---

## Kluczowe decyzje techniczne

1. **UUID jako PK** — lepsze dla distributed systems, brak sequential ID leakage
2. **OffsetDateTime z timezone** — rezerwacje zależne od czasu lokalnego
3. **Pesymistyczne blokowanie** (`SELECT FOR UPDATE`) przy tworzeniu rezerwacji — zapobiega race condition
4. **Async powiadomienia przez SQS** — create/cancel nie czeka na wysyłkę maila
5. **Java 21 LTS zamiast 25** — stabilność, wsparcie, kompatybilność z AWS toolingiem
6. **Jeden deployable artifact** — prostota operacyjna na start, możliwość rozbicia w przyszłości
