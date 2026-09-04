# TICKET-015 — Auth backend — activation + reset MDP + scheduler

## Story
[US-002] — Activation de compte
[US-003] — Mot de passe oublié

## Description
Implémenter le flux d'activation de compte et de réinitialisation de mot de passe : génération de code à 6 chiffres, envoi par email, validation avec rate-limiting (3 tentatives / 15 min), et invalidation des tokens après usage. Ajouter le `TokenCleanupScheduler`.

Contrat API (`docs/tech.md`) :
- `POST /api/auth/activate` — code + nouveau MDP
- `POST /api/auth/resend-activation` — renvoyer le code (rate-limit)
- `POST /api/auth/forgot-password` — email (même réponse si connu ou non)
- `POST /api/auth/reset-password` — code + nouveau MDP

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `controller/AuthController.java` — ajouter les 4 endpoints
- `service/ActivationService.java` (interface) + `ActivationServiceImpl.java` — générer code, valider, rate-limit, `isActive = true`
- `repository/ActivationTokenRepository.java` — requêtes custom (voir TICKET-005)
- `scheduler/TokenCleanupScheduler.java` — `@Scheduled(cron = "0 0 3 * * *")` ; `findAllByUsedAtIsNotNullOrExpiresAtBefore(LocalDateTime.now())` ; supprime les tokens
- `exception/RateLimitException.java` — levée si > 3 envois en 15 min (→ 429)

## Acceptance criteria
- [ ] `POST /api/auth/activate` avec code valide et non expiré → 200, `isActive = true`, token marqué `usedAt = now()`
- [ ] `POST /api/auth/activate` avec code expiré → 400 `{"error": "Code expiré"}`
- [ ] `POST /api/auth/activate` avec code déjà utilisé → 400 `{"error": "Code déjà utilisé"}`
- [ ] `POST /api/auth/resend-activation` → 200 ; si > 3 en 15 min → 429
- [ ] `POST /api/auth/forgot-password` → même réponse 200 qu'email soit connu ou non
- [ ] `POST /api/auth/reset-password` → mêmes règles que l'activation
- [ ] Le mot de passe est haché avec BCrypt avant d'être sauvegardé
- [ ] `TokenCleanupScheduler` tourne à 3h du matin et supprime les tokens expirés ou utilisés
- [ ] `@EnableScheduling` présent sur `PortailAdacApplication`

## Branch
`feature/auth`
- [ ] Create: `git checkout -b feature/auth`
- [x] Switch to existing: `git checkout feature/auth`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@ExtendWith(MockitoExtension)` sur `ActivationServiceImpl`): activation avec token valide → `user.isActive = true`, `token.usedAt` non null
- [ ] Test 2 : activation avec token expiré → `ActivationTokenExpiredException`
- [ ] Test 3 : activation avec token déjà utilisé (`usedAt != null`) → exception
- [ ] Test 4 : resend > 3 fois en 15 min → `RateLimitException`
- [ ] Test 5 (`@DataJpaTest`): `findAllByUsedAtIsNotNullOrExpiresAtBefore` retourne les bons tokens
- [ ] Test 6 (`@WebMvcTest(AuthController.class)`): `POST /api/auth/forgot-password` → 200 identique qu'email connu ou non

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(auth): add account activation, password reset and token cleanup scheduler`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/auth` — see TICKET-018

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code
- [x] /jpa-patterns → génère les entités JPA, les repositories et les @Query custom

## Depends on
- TICKET-005 — `ActivationTokenRepository` avec requêtes custom
- TICKET-007 — `JavaMailSender` bean pour l'envoi d'emails

## Estimated time
3h

## Status
[ ] To do   [ ] In progress   [x] Done
