# .claude/task/new_code.md
# Active task context: NEW CODE mode
# Activated by /task-type → new_code
# Edit before/during session: delete the work type you don't need, fill in the scope.

---

## Session Goal
# DELETE all work types except the one that applies to this session.

### [ GOAL: New Feature — Greenfield ]
- Implement a new feature from scratch: new entity, service, repository, controller
- No existing code is being modified (or only wired up to new code)
- TODO: describe the feature in one sentence

### [ GOAL: New Feature — Extension of Existing ]
- Add new behaviour to an existing feature (new endpoint, new service method, new query)
- Existing code is modified to accommodate new requirements
- TODO: describe what is being extended and what the new requirement is

### [ GOAL: Feature Change — New Requirements ]
- Existing feature is being changed to meet updated business requirements
- Behaviour changes are intentional and expected — existing tests will be updated accordingly
- TODO: describe what changes and why (reference requirement or ticket if available)

---

## Scope

- New classes to be created:
  - TODO: list planned new classes (Entity, Service, Repository, Controller, DTO, etc.)
- Existing classes to be modified:
  - TODO: list classes that will be touched and what changes
- Out of scope (do not touch):
  - TODO: list what must not change in this session

---

## API Contract (if adding/changing endpoints)

- HTTP method + path: TODO (e.g. `POST /api/v1/orders`)
- Request body: TODO (describe or sketch DTO fields)
- Response body: TODO
- Error responses: TODO (e.g. `404` if entity not found, `400` if validation fails)
- TODO: confirm if API contract is pre-defined by interviewer or free to design

---

## Data Model (if adding/changing entities)

- New table(s): TODO
- New columns on existing table(s): TODO
- Migration script required: [ ] yes / [ ] no
- TODO: confirm Flyway/Liquibase usage and migration naming convention

---

## Acceptance Criteria

- TODO: list what "done" looks like for this session
  - Example: "`POST /api/v1/orders` creates an order, persists to DB, returns `201` with order ID"
  - Example: "Discount is applied correctly when cart total exceeds threshold"
  - Example: "All new service methods have unit tests, endpoint has `@WebMvcTest` coverage"

---

## Constraints

- TODO: add session-specific constraints
  - Example: "Do not change existing DB schema — add new table only"
  - Example: "Must reuse existing `CustomerRepository` — do not create a new one"
