# Manual E2E Smoke Test — post-review-fix

**Data:** 2026-03-25
**Aplikacja:** rezerwacje POC — Spring Boot 3.4.3, H2, profil `dev`
**Branch:** `poc-post-code-review-fixes`
**Poprzedni test:** `e2e-manual-poc.md` — wynik 14/14 PASS
**Cel:** weryfikacja stanu po zastosowaniu fixów z `code_review_poc.md`

---

## Użytkownicy

| User  | userId | email        |
|-------|--------|--------------|
| Alice | alice  | alice@zoo.pl |
| Bob   | bob    | bob@zoo.pl   |
| Carol | carol  | carol@zoo.pl |
| Dave  | dave   | dave@zoo.pl  |

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
  "id": "9c079323-4b51-4aed-a200-6c51f06ecc59",
  "ownerId": "alice",
  "ownerEmail": "alice@zoo.pl",
  "startTime": "2026-06-01T08:00:00Z",
  "endTime": "2026-06-01T09:00:00Z",
  "status": "ACTIVE",
  "guests": ["guest2@zoo.pl","guest1@zoo.pl"]
}
```
✅ PASS

---

### [T3] Bob: rezerwacja 12:00–13:00 → 201

```json
→ HTTP 201
{
  "id": "1cf9ea9b-327e-4f8b-a482-9d16aba6f0ef",
  "ownerId": "bob",
  "status": "ACTIVE",
  "guests": []
}
```
✅ PASS

---

### [T4] Dave: rezerwacja 14:00–15:30 z gościem → 201

```json
→ HTTP 201
{
  "id": "06a17986-a872-42e5-ba64-393a4da93c15",
  "ownerId": "dave",
  "status": "ACTIVE",
  "guests": ["vip@zoo.pl"]
}
```
✅ PASS

---

### [T5] Carol: 10:30–11:30 — KONFLIKT z Alice → 409

```json
→ HTTP 409
{
  "status": 409,
  "error": "Conflict",
  "message": "Wybrany termin koliduje z istniejącą rezerwacją.",
  "timestamp": "2026-03-25T16:09:29.598..."
}
```
✅ PASS

---

### [T6] Carol: 11:45–12:15 — KONFLIKT z Bobem → 409

```json
→ HTTP 409
{
  "status": 409,
  "error": "Conflict",
  "message": "Wybrany termin koliduje z istniejącą rezerwacją.",
  "timestamp": "2026-03-25T16:09:29.633..."
}
```
✅ PASS

---

### [T7] Alice: 16:00–16:15 — za krótka (15 min < 30 min) → 400

```json
→ HTTP 400
{
  "status": 400,
  "error": "Bad Request",
  "message": "Minimalny czas rezerwacji to 30 minut.",
  "timestamp": "2026-03-25T16:09:29.664..."
}
```
✅ PASS

---

### [T8] Bob próbuje anulować rezerwację Alice → 403

```json
DELETE /api/v1/reservations/9c079323-...  (token: bob)
→ HTTP 403
{
  "status": 403,
  "error": "Forbidden",
  "message": "Możesz anulować tylko własne rezerwacje.",
  "timestamp": "2026-03-25T16:09:29.713..."
}
```
✅ PASS

---

### [T9] Alice anuluje swoją rezerwację → 200 CANCELLED

```json
DELETE /api/v1/reservations/9c079323-...  (token: alice)
→ HTTP 200
{
  "id": "9c079323-4b51-4aed-a200-6c51f06ecc59",
  "status": "CANCELLED",
  "cancelledAt": "2026-03-25T16:09:39.630947+01:00",
  "guests": ["guest2@zoo.pl","guest1@zoo.pl"]
}
```
✅ PASS

---

### [T10] Alice anuluje już anulowaną → 409

```json
→ HTTP 409
{
  "status": 409,
  "error": "Conflict",
  "message": "Rezerwacja jest już anulowana.",
  "timestamp": "2026-03-25T16:09:39.675..."
}
```
✅ PASS

---

### [T11] Carol: 10:30–11:30 (slot zwolniony po anulowaniu Alice) → 201

```json
→ HTTP 201
{
  "id": "fffd5f21-26d8-4c7d-8a54-66dce9bbc66f",
  "ownerId": "carol",
  "status": "ACTIVE",
  "guests": ["vip@zoo.pl"]
}
```
✅ PASS

---

### [T12] GET /api/v1/reservations — lista wszystkich → 200

Zwrócono 4 rezerwacje z prawidłowymi gośćmi:

| ownerId | status    | startTime (UTC)      | guests          |
|---------|-----------|----------------------|-----------------|
| alice   | CANCELLED | 2026-06-01T08:00:00Z | guest1, guest2  |
| carol   | ACTIVE    | 2026-06-01T08:30:00Z | vip@zoo.pl      |
| bob     | ACTIVE    | 2026-06-01T10:00:00Z | —               |
| dave    | ACTIVE    | 2026-06-01T12:00:00Z | vip@zoo.pl      |

```
→ HTTP 200  [4 obiekty, każdy z listą guests]
```
✅ PASS

---

### [T13] GET /api/v1/reservations/00000000-0000-0000-0000-000000000000 → 404

```json
→ HTTP 404
{
  "status": 404,
  "error": "Not Found",
  "message": "Rezerwacja nie istnieje: 00000000-0000-0000-0000-000000000000",
  "timestamp": "2026-03-25T16:09:49.294..."
}
```
✅ PASS

---

### [T14] GET /api/v1/availability?date=2026-06-01&offset=+02:00 → 200

28 slotów co 30 min (08:00–22:00). Zajęte (available=false): 7 slotów.

```
BUSY  2026-06-01T10:30+02:00 → 11:00+02:00  ← Carol
BUSY  2026-06-01T11:00+02:00 → 11:30+02:00  ← Carol
BUSY  2026-06-01T12:00+02:00 → 12:30+02:00  ← Bob
BUSY  2026-06-01T12:30+02:00 → 13:00+02:00  ← Bob
BUSY  2026-06-01T14:00+02:00 → 14:30+02:00  ← Dave
BUSY  2026-06-01T14:30+02:00 → 15:00+02:00  ← Dave
BUSY  2026-06-01T15:00+02:00 → 15:30+02:00  ← Dave
```

Alice CANCELLED → jej slot (08:00–09:00) wrócił jako `available: true`.

```
→ HTTP 200  { "slotMinutes": 30, "slots": [...28 elementów...], available: 21, busy: 7 }
```
✅ PASS

---

## Podsumowanie

| Test | Scenariusz                                  | Oczekiwany | Wynik  |
|------|---------------------------------------------|------------|--------|
| T1   | Brak tokena                                 | 401        | ✅ 401 |
| T2   | Alice: nowa rezerwacja z gośćmi             | 201        | ✅ 201 |
| T3   | Bob: nowa rezerwacja                        | 201        | ✅ 201 |
| T4   | Dave: nowa rezerwacja z gościem             | 201        | ✅ 201 |
| T5   | Carol: konflikt z Alice                     | 409        | ✅ 409 |
| T6   | Carol: konflikt z Bobem                     | 409        | ✅ 409 |
| T7   | Alice: za krótka (15 min)                   | 400        | ✅ 400 |
| T8   | Bob anuluje cudzą (Alice)                   | 403        | ✅ 403 |
| T9   | Alice anuluje własną                        | 200        | ✅ 200 |
| T10  | Alice ponownie anuluje anulowaną            | 409        | ✅ 409 |
| T11  | Carol zajmuje slot po anulowaniu Alice      | 201        | ✅ 201 |
| T12  | GET lista wszystkich z gośćmi               | 200        | ✅ 200 |
| T13  | GET nieistniejąca rezerwacja                | 404        | ✅ 404 |
| T14  | GET availability — wolne i zajęte sloty     | 200        | ✅ 200 |

**14/14 PASS**

---

## Porównanie z poprzednim testem (e2e-manual-poc.md)

| Aspekt | poprzedni test | ten test |
|--------|---------------|----------|
| Wynik  | 14/14 PASS    | 14/14 PASS |
| Bugi znalezione podczas testu | 1 (LazyInitializationException) | 0 |
| `ErrorResponse` z `timestamp` | nie | tak (dodane po review) |
| `@Column(name=...)` na encjach | nie | tak |
| `findByIdWithGuests` w cancel() | nie | tak |
| JPQL enum param (nie magic string) | nie | tak |
| `getGuests()` unmodifiable | nie | tak |
| `endTime > startTime` walidacja | nie | tak |

---

## Obserwacje

- Czasy zapisywane i zwracane w UTC (H2 konwertuje `+02:00` → UTC) — zachowanie poprawne i spójne
- `ErrorResponse` zawiera teraz `timestamp` — ułatwia debugowanie
- `GET /availability` prawidłowo ignoruje CANCELLED rezerwacje przy oznaczaniu slotów
- Powiadomienia logowane poprawnie w konsoli przy każdym `create` i `cancel`
- Brak regresji względem poprzedniego testu
