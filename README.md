# Rezerwacje — System Rezerwacji Stołu Bilardowego

Aplikacja backendowa do zarządzania rezerwacjami stołu bilardowego.
Zbudowana jako REST API — bez frontendu, docelowo deployowana na AWS.

---

## Cel biznesowy

- Rezerwacja stołu bilardowego (1 stół) tak, żeby dwie rezerwacje się nie nakładały
- Minimalny czas rezerwacji: **30 minut**
- Możliwość anulowania rezerwacji
- Powiadamianie dodatkowych osób (gości) o rezerwacji
- Autoryzacja przez **SSO** (model enterprise)

---

## Stack technologiczny

| Warstwa       | Technologia                                                       |
|---------------|-------------------------------------------------------------------|
| Runtime       | Java 21 (kompilacja); JVM może być nowszy                         |
| Framework     | Spring Boot 3.4.3                                                 |
| Build         | Maven                                                             |
| Persistence   | Spring Data JPA / Hibernate 6.6                                   |
| Baza danych   | H2 in-memory (POC) → PostgreSQL (docelowo)                        |
| Security      | Spring Security + custom JWT filter (JJWT 0.12.6, HS256) → AWS Cognito OIDC (docelowo) |
| API docs      | SpringDoc OpenAPI 2.8.5 — Swagger UI pod `/swagger-ui.html`       |
| Testy         | JUnit 5, Mockito, MockMvc, spring-security-test                   |
| Lombok        | **Nieużywany** — explicite konstruktory w całym projekcie         |

---

## Szybki start (lokalnie)

```bash
# Uruchomienie z profilem dev (H2 console + DevTokenController)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Wygenerowanie tokenu JWT do testów
GET http://localhost:8080/dev/token?userId=user1&email=user1@example.com

# Swagger UI
open http://localhost:8080/swagger-ui.html

# H2 Console (tylko profil dev)
open http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:rezerwacje
```

---

## API

| Metoda   | Endpoint                           | Opis                                 | Auth |
|----------|------------------------------------|--------------------------------------|------|
| `POST`   | `/api/v1/reservations`             | Utwórz rezerwację                    | ✅   |
| `GET`    | `/api/v1/reservations`             | Lista wszystkich rezerwacji          | ✅   |
| `GET`    | `/api/v1/reservations/{id}`        | Szczegóły rezerwacji                 | ✅   |
| `DELETE` | `/api/v1/reservations/{id}`        | Anuluj rezerwację (tylko właściciel) | ✅   |
| `GET`    | `/api/v1/availability?date=...`    | Wolne sloty na dany dzień            | ✅   |
| `GET`    | `/dev/token?userId=&email=`        | Generuj token JWT (tylko profil dev) | ❌   |

### Przykład — tworzenie rezerwacji

```json
POST /api/v1/reservations
Authorization: Bearer <token>

{
  "startTime": "2026-06-01T10:00:00+02:00",
  "endTime":   "2026-06-01T11:00:00+02:00",
  "guests":    ["guest1@example.com", "guest2@example.com"]
}

→ 201 Created
{
  "id": "9c079323-...",
  "status": "ACTIVE",
  "ownerId": "user1",
  "guests": ["guest1@example.com", "guest2@example.com"]
}
```

### Kody błędów

| HTTP | Sytuacja                                   |
|------|--------------------------------------------|
| 400  | Rezerwacja krótsza niż 30 min / zły format |
| 401  | Brak lub nieprawidłowy token JWT           |
| 403  | Próba anulowania cudzej rezerwacji         |
| 404  | Rezerwacja nie istnieje                    |
| 409  | Konflikt terminów / już anulowana          |

---

## Testy

```bash
mvn test
```

```
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

| Typ              | Klasa                        | Pokrycie                                              |
|------------------|------------------------------|-------------------------------------------------------|
| Jednostkowe      | `ReservationServiceTest`     | Logika biznesowa: min 30 min, konflikty, anulowanie   |
| Integracyjne     | `ReservationControllerTest`  | REST API end-to-end: MockMvc, Spring Security, H2     |

---

## Architektura

```
org.example.rezerwacje/
├── api/
│   ├── controller/      ReservationController, AvailabilityController
│   ├── dto/             CreateReservationRequest, ReservationResponse, AvailabilityResponse, ErrorResponse
│   └── exception/       GlobalExceptionHandler, ValidationException, ConflictException, ...
├── domain/
│   ├── model/           Reservation, ReservationGuest, ReservationStatus
│   ├── repository/      ReservationRepository (JOIN FETCH queries)
│   └── service/         ReservationService, AvailabilityService
├── config/              SecurityConfig, JwtAuthFilter, JwtService, JwtProperties, UserPrincipal
├── notification/        NotificationService (stub → SQS/SES docelowo)
└── dev/                 DevTokenController (@Profile("dev"))
```

Zasady:
- **Controller → Service → Repository** — bez skracania warstw
- `@Transactional` wyłącznie w serwisie
- Encje JPA nigdy nie wychodzą poza serwis — kontroler operuje wyłącznie na DTO
- `JOIN FETCH` dla wszystkich zapytań zwracających kolekcje (`findAllWithGuests`, `findByIdWithGuests`)
