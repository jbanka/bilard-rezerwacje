# POSTEP_DEV_POC.md

## Status ogólny: ✅ UKOŃCZONY

---

## ✅ Krok 0 — pom.xml
Spring Boot 3.4.3, Java 21 (kompilacja), H2, JPA, Security, JJWT 0.12.6, SpringDoc 2.8.5.
Bez Lombok (Java 25 na maszynie — niekompatybilne z bieżącą wersją Lombok/ByteBuddy).
Dodano `-Dnet.bytebuddy.experimental=true` w Surefire dla Mockito na JDK 25.

## ✅ Krok 1 — Model domenowy
- `ReservationStatus` (enum: ACTIVE, CANCELLED)
- `Reservation` (@Entity, UUID PK, LAZY guests, ręczne gettery/settery)
- `ReservationGuest` (@Entity, ManyToOne → Reservation)

## ✅ Krok 2 — ReservationRepository
- `countConflicts(start, end)` — JPQL overlap query
- `findActiveInRange(start, end)`
- `findByStatusOrderByStartTimeAsc`
- `ReservationGuestRepository`

## ✅ Krok 3 — ReservationService
- `create()` — walidacja min 30 min, conflict check, zapis, notify
- `cancel()` — owner check, status check, zapis, notify
- `findById()`, `findAll()`

## ✅ Krok 4 — Wyjątki + NotificationService
- `ValidationException`, `ConflictException`, `ForbiddenException`, `NotFoundException`
- `NotificationService` — stub (SLF4J log)

## ✅ Krok 5 — Security: mock JWT (HS256)
- `JwtProperties`, `JwtService`, `JwtAuthFilter`, `UserPrincipal`, `SecurityConfig`
- `AuthenticationEntryPoint` zwraca 401 (nie 403) przy braku tokena

## ✅ Krok 6 — DTO + API REST + GlobalExceptionHandler
- `CreateReservationRequest`, `ReservationResponse`, `ErrorResponse`
- `GlobalExceptionHandler` (400/403/404/409/500)
- `ReservationController` (POST/GET/GET{id}/DELETE)

## ✅ Krok 7 — AvailabilityService + AvailabilityController
- `AvailabilityProperties`, `AvailabilityResponse` + `SlotDto`
- `GET /api/v1/availability?date=YYYY-MM-DD&offset=+02:00`

## ✅ Krok 8 — DevTokenController (profil dev)
- `GET /dev/token?userId=X&email=Y` — tylko profil dev

## ✅ Krok 9 — application.yml + RezerwacjeApplication
- H2 in-memory, ddl-auto: create-drop, profil dev z H2 console

## ✅ Krok 10 — Testy jednostkowe (ReservationServiceTest)
- 9 testów Mockito, @MockitoSettings(LENIENT)

## ✅ Krok 11 — Testy integracyjne (ReservationControllerTest)
- 7 testów MockMvc, @SpringBootTest, profil test

## ✅ Krok 12 — mvn test

```
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS — 9.5 s
```

---

## Uwagi implementacyjne

| Problem | Rozwiązanie |
|---------|------------|
| Lombok niekompatybilny z Java 25 | Usunięto Lombok, ręczne konstruktory + SLF4J |
| ByteBuddy/Mockito nie obsługuje Java 25 | `-Dnet.bytebuddy.experimental=true` w Surefire |
| Spring Security zwraca 403 zamiast 401 | Dodano `authenticationEntryPoint` w SecurityConfig |
| `UnnecessaryStubbingException` w Mockito | `@MockitoSettings(strictness = LENIENT)` |

---

## Kryteria zaliczenia POC

- [x] Kompilacja bez błędów
- [x] Wszystkie testy zielone (16/16)
- [x] Reguła min 30 min → 400
- [x] Konflikt rezerwacji → 409
- [x] Cancel własnej → 200 CANCELLED
- [x] Cancel cudzej → 403
- [x] Brak tokena → 401
- [x] GET /availability zwraca sloty
- [x] Powiadomienia logowane przy create/cancel
