# TICKET-019 — Backend — CRUD Formateurs + Stagiaires

## Story
[US-007] — Gérer les comptes Formateurs
[US-008] — Gérer les comptes Stagiaires

## Description
Créer les endpoints de gestion des utilisateurs (Formateurs et Stagiaires) : création (avec envoi d'email d'activation automatique), liste, suspension/réactivation. Seul le SUPER_ADMIN peut créer des comptes. Le Formateur ne voit que les stagiaires actifs.

Contrat API (`docs/tech.md`) :
- `POST /api/users` — créer formateur ou stagiaire
- `GET /api/users` — liste (avec filtre `role`, `isActive`)
- `GET /api/users/{id}` — détail
- `PATCH /api/users/{id}` — modifier / suspendre / réactiver

## Repo
[ ] front/   [x] back/   [ ] both

## Files to create or modify
- `controller/UserController.java` — CRUD avec `@PreAuthorize("hasRole('SUPER_ADMIN')")` sur création/modification
- `service/UserService.java` (interface) + `UserServiceImpl.java` — logique CRUD, génération token d'activation, trigger email
- `dto/request/CreateUserRequest.java` — `firstName`, `lastName`, `email`, `role`, `formationIds` (optionnel pour stagiaire)
- `dto/response/UserResponse.java` — champs publics sans mot de passe
- `mapper/UserMapper.java` — Entity ↔ DTO
- `exception/DuplicateEmailException.java` — levée si email déjà existant (→ 409)

## Acceptance criteria
- [ ] `POST /api/users` (SUPER_ADMIN) → 201 + `UserResponse` ; email d'activation envoyé automatiquement
- [ ] `POST /api/users` email déjà utilisé → 409 `{"error": "Email déjà utilisé"}`
- [ ] `POST /api/users` par un ADMIN ou STAGIAIRE → 403
- [ ] `GET /api/users?role=ADMIN` → liste des formateurs (SUPER_ADMIN voit tous ; ADMIN voit actifs seulement)
- [ ] `GET /api/users?role=STAGIAIRE` → ADMIN ne voit que `isActive=true`
- [ ] `PATCH /api/users/{id}` avec `isActive=false` → suspension (utilisateur ne peut plus se connecter)
- [ ] `PATCH /api/users/{id}` avec `isActive=true` → réactivation
- [ ] Tous les endpoints documentés Swagger

## Branch
`feature/users`
- [x] Create: `git checkout -b feature/users`
- [ ] Switch to existing: `git checkout feature/users`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@WebMvcTest(UserController.class)`): `POST /api/users` par SUPER_ADMIN → 201
- [ ] Test 2 : `POST /api/users` par ADMIN → 403
- [ ] Test 3 : `POST /api/users` email dupliqué → 409
- [ ] Test 4 (`@ExtendWith(MockitoExtension)` sur `UserServiceImpl`): créer un STAGIAIRE → token d'activation créé + email envoyé (verify mock)
- [ ] Test 5 : `GET /api/users?role=STAGIAIRE` par ADMIN → ne retourne que les actifs

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(users): add CRUD endpoints for formateurs and stagiaires`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/users` — see TICKET-021

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code

## Depends on
- TICKET-005 — `UserRepository`, `UserMapper`, DTOs
- TICKET-015 — système d'activation (token + email) pour le trigger auto à la création

## Estimated time
3h

## Status
[ ] To do   [ ] In progress   [ ] Done
