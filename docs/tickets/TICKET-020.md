# TICKET-020 — Backend — GET /users/me + PATCH /users/me

## Story
[US-016] — Préférences de notification email

## Description
Endpoint pour lire et modifier le profil de l'utilisateur connecté, incluant le toggle `emailNotificationsEnabled`. Tout utilisateur authentifié peut accéder à ces endpoints.

Contrat API (`docs/tech.md`) :
- `GET /api/users/me` — profil de l'utilisateur connecté
- `PATCH /api/users/me` — modifier prénom, nom, ou toggle email notifications

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `controller/UserController.java` — ajouter `GET /users/me` et `PATCH /users/me`
- `service/UserService.java` (interface) + `UserServiceImpl.java` — ajouter `getMe`, `updateMe`
- `dto/request/UpdateProfileRequest.java` — `firstName`, `lastName`, `emailNotificationsEnabled` (tous optionnels)

## Acceptance criteria
- [ ] `GET /api/users/me` → retourne le profil complet (`UserResponse`) de l'utilisateur dans le SecurityContext
- [ ] `PATCH /api/users/me` → met à jour uniquement les champs fournis (partial update)
- [ ] `PATCH /api/users/me` avec `emailNotificationsEnabled: false` → sauvegardé en base
- [ ] Les emails transactionnels (activation, reset) ne sont pas affectés par ce toggle
- [ ] 401 si non authentifié

## Branch
`feature/users`
- [ ] Create: `git checkout -b feature/users`
- [x] Switch to existing: `git checkout feature/users`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@WebMvcTest(UserController.class)`): `GET /api/users/me` avec utilisateur authentifié → 200 + `UserResponse`
- [ ] Test 2 : `GET /api/users/me` sans authentification → 401
- [ ] Test 3 (`@ExtendWith(MockitoExtension)`): `updateMe` avec `emailNotificationsEnabled=false` → champ mis à jour en base

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(users): add GET and PATCH /users/me with email notification toggle`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/users` — see TICKET-021

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code

## Depends on
- TICKET-019 — `UserController` et `UserService` existent déjà

## Estimated time
1h

## Status
[ ] To do   [ ] In progress   [ ] Done
