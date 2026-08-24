# TICKET-014 — Auth backend — login / logout / me

## Story
[US-001] — Connexion

## Description
Créer les endpoints d'authentification : login (pose le cookie JWT), logout (supprime le cookie), et GET /auth/me (retourne l'utilisateur connecté). Connecter le `JwtAuthenticationFilter` au `AuthController`.

Contrat API (`docs/tech.md`) :
- `POST /api/auth/login` → 200 + cookie `jwt`
- `POST /api/auth/logout` → 200 + supprime le cookie
- `GET /api/auth/me` → `UserResponse`

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `controller/AuthController.java` — endpoints login, logout, me avec `@Operation` + `@ApiResponse`
- `service/AuthService.java` (interface) + `service/AuthServiceImpl.java` — logique login, me
- `dto/response/AuthResponse.java` — `{ message: "Login successful" }` (le token est dans le cookie, pas dans le body)
- `exception/GlobalExceptionHandler.java` — handler 401 (bad credentials), 403 (account inactive)

## Acceptance criteria
- [ ] `POST /api/auth/login` avec bons identifiants → 200 + cookie `jwt` HttpOnly, SameSite=Strict, 24h
- [ ] `POST /api/auth/login` avec mauvais identifiants → 401 `{"error": "Identifiants invalides"}`
- [ ] `POST /api/auth/login` avec compte inactif → 401 `{"error": "Compte non activé, consultez vos emails"}`
- [ ] `POST /api/auth/logout` → 200 + cookie expiré (maxAge=0)
- [ ] `GET /api/auth/me` avec cookie valide → `UserResponse` (id, email, firstName, lastName, role)
- [ ] `GET /api/auth/me` sans cookie → 401
- [ ] Tous les endpoints documentés avec `@Operation` et `@ApiResponse`

## Branch
`feature/auth`
- [x] Create: `git checkout -b feature/auth`
- [ ] Switch to existing: `git checkout feature/auth`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@WebMvcTest(AuthController.class)`): `POST /api/auth/login` credentials valides → 200 + `Set-Cookie` avec `jwt=`
- [ ] Test 2 : `POST /api/auth/login` mauvais password → 401 + message d'erreur
- [ ] Test 3 : `POST /api/auth/login` compte `isActive=false` → 401 + message spécifique
- [ ] Test 4 : `POST /api/auth/logout` → 200 + cookie `jwt` avec `maxAge=0`
- [ ] Test 5 (`@MockBean AuthService`): `GET /api/auth/me` avec token valide → 200 + `UserResponse`
- [ ] Test 6 : `GET /api/auth/me` sans token → 401

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(auth): add login, logout and me endpoints with JWT cookie`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/auth` — see TICKET-018

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code

## Depends on
- TICKET-006 — Spring Security + JWT filter doivent être en place

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
