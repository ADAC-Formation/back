# TICKET-020 — Backend — PATCH /users/me

## Story

[US-016] — Préférences de notification email

## Description

Endpoint pour modifier le profil de l'utilisateur connecté, incluant le toggle `emailNotificationsEnabled`. Tout utilisateur authentifié peut y accéder.

> **Révision (2026-09-04)** : le `GET /api/users/me` initialement prévu ici n'est **pas**
> implémenté — `GET /api/auth/me` (TICKET-014) couvre déjà "lire mon propre profil" et renvoie un
> `UserResponse` complet ; `docs/tech.md` documente explicitement cette décision (§ `GET
> /api/users/{id}` : "un STAGIAIRE consulte son propre profil via `PATCH /api/users/me`... pas de
> `GET /api/users/me` séparé pour l'instant"). Ajouter un second endpoint aurait dupliqué
> `/api/auth/me` sans raison. Décision validée avec Charlotte avant implémentation. Le champ
> `UpdateProfileRequest` ne porte que `emailNotificationsEnabled` (pas de `firstName`/`lastName` —
> absent du contrat `tech.md`, et la convention du code est `nom`/`prenom` de toute façon) ;
> `UpdateProfileRequest.java` existait déjà, créé par anticipation dans le commit TICKET-005.

Contrat API (`docs/tech.md`) :

- `PATCH /api/users/me` — toggle email notifications

## Repo

[ ] front/ [x] back [ ] both

## Files to create or modify

- `controller/UserController.java` — ajouter `PATCH /users/me`
- `service/UserService.java` (interface) + `UserServiceImpl.java` — ajouter `updateMe`
- `dto/request/UpdateProfileRequest.java` — déjà existant (`emailNotificationsEnabled`, optionnel)

## Acceptance criteria

- [x] `PATCH /api/users/me` → met à jour uniquement les champs fournis (partial update)
- [x] `PATCH /api/users/me` avec `emailNotificationsEnabled: false` → sauvegardé en base
- [x] Les emails transactionnels (activation, reset) ne sont pas affectés par ce toggle
- [x] 401 si non authentifié

## Branch

`feature/users`

- [ ] Create: `git checkout -b feature/users`
- [x] Switch to existing: `git checkout feature/users`

## Write tests first (TDD)

Before writing any implementation code:

- [x] Test 1 (`@WebMvcTest(UserController.class)`): `PATCH /api/users/me` avec utilisateur authentifié → 200 + `UserResponse`
- [x] Test 2 : `PATCH /api/users/me` sans authentification → 401
- [x] Test 3 (`@ExtendWith(MockitoExtension)`): `updateMe` avec `emailNotificationsEnabled=false` → champ mis à jour en base
- [x] Test 4 (`@ExtendWith(MockitoExtension)`): `updateMe` avec champ `null` → valeur inchangée (partial update)

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review

Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit

Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):

- `feat(users): add PATCH /users/me with email notification toggle`

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

[ ] To do [ ] In progress [x] Done
