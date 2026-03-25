# Manual E2E Smoke Test — manual-test-01.md

**Data:** 2026-03-25
**Aplikacja:** rezerwacje POC — Spring Boot 3.4.3, H2, profil `dev`
**Bugfix w trakcie testu:** `LazyInitializationException` przy GET /reservations — naprawiono przez `JOIN FETCH` w repozytorium

---

## Użytkownicy

| User  | userId | email           |
|-------|--------|-----------------|
| Alice | alice  | alice@zoo.pl    |
| Bob   | bob    | bob@zoo.pl      |
| Carol | carol  | carol@zoo.pl    |
| Dave  | dave   | dave@zoo.pl     |

Tokeny wygenerowane przez `GET /dev/token?userId=X&email=Y` (profil dev, HS256).

---

## Scenariusz

### [T1] Brak tokena → 401

```
POST /api/v1/reservations  (bez Authorization)
→ HTTP 401
```
✅ PASS

---

### [T2] Alice: rezerwacja 10:00–11:00 z gośćmi → 201

```json
POST /api/v1/reservations
{
  "startTime": "2026-06-01T10:00:00+02:00",
  "endTime":   "2026-06-01T11:00:00+02:00",
  "guests":    ["guest1@zoo.pl","guest2@zoo.pl"]
}
→ HTTP 201
{
  "id": "18876984-a779-41bf-bcc3-b3574d46462f",
  "status": "ACTIVE",
  "guests": ["guest1@zoo.pl","guest2@zoo.pl"]
}
```
✅ PASS

---

### [T3] Bob: rezerwacja 12:00–13:00 → 201

```json
→ HTTP 201
{ "id": "acf1bcb6-35bf-4dc0-bb57-4b99710badda", "status": "ACTIVE", "guests": [] }
```
✅ PASS

---

### [T4] Dave: rezerwacja 14:00–15:30 z gościem → 201

```json
→ HTTP 201
{ "id": "16f40b82-3434-480b-abca-efcb0b8f3b43", "status": "ACTIVE", "guests": ["vip@zoo.pl"] }
```
✅ PASS

---

### [T5] Carol: 10:30–11:30 — KONFLIKT z Alice → 409

```json
→ HTTP 409
{ "error": "Conflict", "message": "Wybrany termin koliduje z istniejącą rezerwacją." }
```
✅ PASS

---

### [T6] Carol: 11:45–12:15 — KONFLIKT z Bobem → 409

```json
→ HTTP 409
{ "error": "Conflict", "message": "Wybrany termin koliduje z istniejącą rezerwacją." }
```
✅ PASS

---

### [T7] Alice: 16:00–16:15 — za krótka (15 min < 30 min) → 400

```json
→ HTTP 400
{ "error": "Bad Request", "message": "Minimalny czas rezerwacji to 30 minut." }
```
✅ PASS

---

### [T8] Bob próbuje anulować rezerwację Alice → 403

```json
DELETE /api/v1/reservations/18876984-...  (token: bob)
→ HTTP 403
{ "error": "Forbidden", "message": "Możesz anulować tylko własne rezerwacje." }
```
✅ PASS

---

### [T9] Alice anuluje swoją rezerwację → 200 CANCELLED

```json
DELETE /api/v1/reservations/18876984-...  (token: alice)
→ HTTP 200
{ "status": "CANCELLED", "cancelledAt": "2026-03-25T15:10:39...", "guests": ["guest1@zoo.pl","guest2@zoo.pl"] }
```
✅ PASS

---

### [T10] Alice anuluje już anulowaną → 409

```json
→ HTTP 409
{ "error": "Conflict", "message": "Rezerwacja jest już anulowana." }
```
✅ PASS

---

### [T11] Carol: 10:30–11:30 (slot zwolniony po anulowaniu Alice) → 201

```json
→ HTTP 201
{ "id": "9e71d157-ca7b-40ff-a4a5-a8db0d1c7a86", "status": "ACTIVE", "guests": ["vip@zoo.pl"] }
```
✅ PASS

---

### [T12] GET /api/v1/reservations — lista wszystkich → 200

Zwrócono 4 rezerwacje (alice CANCELLED, carol ACTIVE, bob ACTIVE, dave ACTIVE), każda z prawidłowymi gośćmi.

```
→ HTTP 200  [4 obiekty]
```
✅ PASS
*(bugfix: LazyInitializationException naprawiony przez JOIN FETCH)*

---

### [T13] GET /api/v1/reservations/00000000-0000-0000-0000-000000000000 → 404

```json
→ HTTP 404
{ "error": "Not Found", "message": "Rezerwacja nie istnieje: 00000000-..." }
```
✅ PASS

---

### [T14] GET /api/v1/availability?date=2026-06-01 → 200

28 slotów co 30 min (08:00–22:00).
Zajęte (available=false): 08:30–09:00, 09:00–09:30 (Carol), 10:00–11:00 (Bob), 12:00–13:30 (Dave).
Alice CANCELLED → jej slot 08:00–09:00 wrócił jako `available=true`.

```
→ HTTP 200  { "slotMinutes": 30, "slots": [...28 elementów...] }
```
✅ PASS

---

## Podsumowanie

| Test | Scenariusz                                  | Oczekiwany | Wynik  |
|------|---------------------------------------------|-----------|--------|
| T1   | Brak tokena                                 | 401       | ✅ 401 |
| T2   | Alice: nowa rezerwacja z gośćmi             | 201       | ✅ 201 |
| T3   | Bob: nowa rezerwacja                        | 201       | ✅ 201 |
| T4   | Dave: nowa rezerwacja z gościem             | 201       | ✅ 201 |
| T5   | Carol: konflikt z Alice                     | 409       | ✅ 409 |
| T6   | Carol: konflikt z Bobem                     | 409       | ✅ 409 |
| T7   | Alice: za krótka (15 min)                   | 400       | ✅ 400 |
| T8   | Bob anuluje cudzą (Alice)                   | 403       | ✅ 403 |
| T9   | Alice anuluje własną                        | 200       | ✅ 200 |
| T10  | Alice ponownie anuluje anulowaną            | 409       | ✅ 409 |
| T11  | Carol zajmuje slot po anulowaniu Alice      | 201       | ✅ 201 |
| T12  | GET lista wszystkich z gośćmi               | 200       | ✅ 200 |
| T13  | GET nieistniejąca rezerwacja                | 404       | ✅ 404 |
| T14  | GET availability — wolne sloty              | 200       | ✅ 200 |

**14/14 PASS**

---

## Bugi znalezione i naprawione

| # | Bug | Przyczyna | Fix |
|---|-----|-----------|-----|
| 1 | `GET /reservations` → 500 | `LazyInitializationException` — `guests` ładowane poza transakcją | Dodano `findAllWithGuests()` i `findByIdWithGuests()` z `JOIN FETCH` w `ReservationRepository` |

---

## Obserwacje

- Czasy zapisywane w UTC (H2 nie zachowuje offset `+02:00`) — zachowanie poprawne, do udokumentowania dla frontendu
- `GET /availability` prawidłowo ignoruje CANCELLED rezerwacje przy oznaczaniu slotów
- Powiadomienia logowane poprawnie w konsoli przy każdym `create` i `cancel`
