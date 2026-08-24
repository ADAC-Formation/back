# TICKET-006 — Spring Security + JWT cookie

## Story
Foundation — sécurité et authentification

## Description
Configurer Spring Security avec JWT en cookie HttpOnly. Créer les deux filtres (`JwtAuthenticationFilter` pour le login, `JwtAuthorizationFilter` pour valider le cookie sur chaque requête), la config CORS, et le `SecurityConfig`. Le `JwtAuthenticationFilter` doit parser du JSON (pas du form data).

## Repo
[ ] front/   [x] back/   [ ] both

## Files to create or modify
- `security/SecurityConfig.java` — CSRF désactivé, CORS avec `allowCredentials=true`, filtres enregistrés, routes publiques (`/api/auth/**`, `/swagger-ui.html`, `/v3/api-docs/**`)
- `security/filter/JwtAuthenticationFilter.java` — surcharge `attemptAuthentication()` pour parser JSON, pose le cookie HttpOnly sur succès
- `security/filter/JwtAuthorizationFilter.java` — lit `request.getCookies()`, valide le JWT, set `SecurityContextHolder`
- `security/JwtTokenService.java` — `generateToken(UserDetails)`, `validateToken(String)`, `extractUsername(String)` — utilise auth0 java-jwt
- `security/CustomUserDetailsService.java` — `loadUserByUsername` depuis `UserRepository`
- `security/CustomAuthenticationManager.java`
- `security/PasswordEncoderConfig.java` — `BCryptPasswordEncoder` bean

## Acceptance criteria
- [ ] `POST /api/auth/login` (JSON `{"email":"...", "password":"..."}`) pose un cookie `jwt` HttpOnly, Secure (false en dev), SameSite=Strict, 24h
- [ ] Toute requête sur `/api/**` (hors auth) sans cookie valide retourne 401
- [ ] Le cookie est relu et validé par `JwtAuthorizationFilter` sur chaque requête protégée
- [ ] CORS configuré pour `http://localhost:5173` avec `allowCredentials=true`
- [ ] CSRF désactivé (cookie HttpOnly + SameSite=Strict suffisent)
- [ ] Swagger UI reste accessible sans authentification

## Branch
`feature/setup`
- [ ] Create: `git checkout -b feature/setup`
- [x] Switch to existing: `git checkout feature/setup`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@WebMvcTest(AuthController.class)` + `@MockBean` des services) : `POST /api/auth/login` avec bons identifiants → 200 + header `Set-Cookie` contenant `jwt=`
- [ ] Test 2 : `POST /api/auth/login` avec mauvais mot de passe → 401
- [ ] Test 3 : Requête sur un endpoint protégé sans cookie → 401
- [ ] Test 4 : Requête sur un endpoint protégé avec cookie valide → 200

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(security): configure Spring Security with JWT HttpOnly cookie`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/setup` — see TICKET-008

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code

## Depends on
- TICKET-003 — `User` entity et `UserRepository` doivent exister

## Estimated time
3h

## Status
[ ] To do   [ ] In progress   [ ] Done
