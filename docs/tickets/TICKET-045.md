      # TICKET-045 — Auth : rate limiting login + routes publiques strictes

## Story

Foundation — sécurité et authentification (durcissement post-review TICKET-006)

## Description

Deux failles identifiées par la review de code de TICKET-006, volontairement non corrigées dans
ce ticket-là (hors périmètre de ses critères d'acceptation, décision à valider par Charlotte) :

1. **Pas de protection contre le brute-force sur `POST /api/auth/login`.** L'endpoint est public,
   non throttlé, et rien ne journalise/bloque les tentatives répétées. Avec ~50 emails connus
   (staff + stagiaires), du credential stuffing ou du password spraying tourne sans limite.
2. **`/api/auth/**`entièrement public dans`SecurityConfig`.** Tant qu'il n'existe que
`POST /api/auth/login`(géré par`JwtAuthenticationFilter`, pas de controller), le préfixe
large ne change rien concrètement — mais dès que TICKET-014 ajoute `AuthController`, tout
nouvel endpoint sous `/api/auth/\*\*`(y compris`/api/auth/me`, qui doit répondre 401 sans
cookie selon `tech.md`) devient public par défaut sans qu'aucun changement de code ne le
   signale.

## Repo

[ ] front/ [x] back [ ] both

## Files to create or modify

- `security/filter/JwtAuthenticationFilter.java` — appelle le service de lockout dans
  `attemptAuthentication` (avant `authenticationManager.authenticate`) et dans
  `successfulAuthentication` (reset du compteur)
- `security/LoginAttemptService.java` _(nouveau)_ — compteur en mémoire (`ConcurrentHashMap`,
  clé email+IP), pas de nouvelle dépendance. Verrouillage après 5 échecs / 15 min glissantes.
- `security/SecurityConfig.java` — remplace `"/api/auth/**"` par la liste explicite des routes
  publiques réellement câblées à ce moment-là (`/api/auth/login` seul tant que TICKET-014 n'a pas
  atterri ; à étendre quand `AuthController` ajoute `/activate`, `/forgot-password`,
  `/reset-password` — **ne pas** y ajouter `/api/auth/me`, qui doit rester derrière l'auth)
- `docs/tech.md` — documenter la réponse `429` de `POST /api/auth/login` (même forme que celle
  déjà documentée pour `POST /api/auth/activate`)

## Acceptance criteria

- [x] 5 échecs de login sur le même couple email+IP en moins de 15 min → 6e tentative retourne
      `429 {"status":429,"message":"Trop de tentatives. Réessayez dans 15 minutes."}`, sans
      toucher `AuthenticationManager` (le lockout doit se déclencher avant le check password)
- [x] Un login réussi réinitialise le compteur de ce couple email+IP
- [x] Le verrou expire tout seul après 15 min (pas de déblocage manuel nécessaire pour le MVP)
- [x] `SecurityConfig` ne référence plus `/api/auth/**` en `permitAll` — seules les routes
      effectivement publiques à la date du ticket sont listées explicitement
- [x] `GET /api/auth/me` (une fois TICKET-014 fusionné) reste 401 sans cookie — non concerné par
      le `permitAll`

## Branch

`feature/auth`

- [ ] Create: `git checkout -b feature/auth` (si pas déjà créée par TICKET-014)
- [x] Switch to existing: `git checkout feature/auth`

## Write tests first (TDD)

Before writing any implementation code:

- [x] Test 1 (unitaire, `LoginAttemptServiceTest`, pas de contexte Spring) : 5 échecs enregistrés
      pour une clé → `isLocked(key)` vrai ; un succès enregistré → `isLocked(key)` redevient faux
- [x] Test 2 (unitaire) : le verrou expire après la fenêtre de 15 min (horloge injectée/mockée,
      pas de `Thread.sleep(15min)`) — étalé dans le temps (pas les 5 échecs au même instant), sans
      quoi le test ne peut pas distinguer une fenêtre glissante d'une fenêtre fixe (trouvé en
      review, voir `windowSlidesWithEachFailure...`)
- [x] Test 3 (intégration, MockMvc comme `JwtAuthenticationIntegrationTest`) : 5 mauvais mots de
      passe puis un 6e essai (même avec le bon mot de passe cette fois) → `429`
- [x] Test 4 (intégration) : un login réussi après des échecs sous le seuil de blocage n'est pas
      bloqué, et réinitialise le compteur
- [x] Test 5 (intégration) : requête vers un endpoint sous `/api/auth/**` qui n'est pas dans la
      liste explicite de `PUBLIC_ROUTES` → **401**, pas 404 — corrigé pendant l'implémentation :
      une fois le wildcard `/api/auth/**` réellement retiré (l'objet même de ce ticket), la chaîne
      de filtres Spring Security rejette la requête *avant* que le dispatcher MVC ait la moindre
      chance de répondre 404 pour une route qui n'existe pas. Un 404 ici signalerait au contraire
      que le wildcard traîne encore quelque part.

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review

Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit

Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):

- `feat(security): add login rate limiting and tighten public auth routes; update docs`

## PR (only on last ticket of this branch)

- [x] Checked: TICKET-014 and TICKET-015 are both Done — this is in fact the last ticket on
      `feature/auth` (see the backend-agent ticket order: `feature/auth → 014, 015, 045`)

## Skills to invoke

- [x] /java-springboot
- [x] /spring-boot-test-patterns

## Depends on

- TICKET-006 — Spring Security + JWT cookie (filtres et `SecurityConfig` doivent exister)

## Estimated time

2h

## Status

[ ] To do [ ] In progress [x] Done

## Origine

Trouvé lors de la review de code de TICKET-006 (2026-08-28) par les agents de review sécurité et
backend/clean-code — voir le rapport de fin de ticket TICKET-006 pour le détail complet des
findings (la majorité a été corrigée directement dans TICKET-006 ; ces deux points ont été
reportés ici car ils impliquent un choix de conception que Charlotte voulait trancher).
