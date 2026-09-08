# TICKET-050 — Backend — PUT /users/{id} (édition par SUPER_ADMIN)

## Story

[US-007/US-008 — extension] Un SUPER_ADMIN doit pouvoir corriger les infos (nom, prénom, email)
d'un formateur ou d'un stagiaire après création — typo dans le nom, faute de frappe dans
l'adresse mail, etc. Actuellement aucun endpoint ne le permet : `PATCH /api/users/me` ne modifie
que son propre compte (et seulement `emailNotificationsEnabled`), `deactivate`/`reactivate` ne
touchent pas aux données.

## Description

Ajouter `PUT /api/users/{id}`, réservé SUPER_ADMIN, pour un update partiel de `nom`/`prenom`/
`email` sur n'importe quel compte (stagiaire, formateur, ou même un autre SUPER_ADMIN).

- Le `role` et `isActive` ne changent jamais via cet endpoint (restent sur `deactivate`/
  `reactivate` et sur la création — pas de bascule stagiaire↔formateur ici).
- `email`, si fourni : validé au format, et unicité vérifiée (409 si déjà pris par un **autre**
  utilisateur — comparer par id, pas juste par valeur, pour ne pas rejeter "corriger l'email vers
  lui-même à l'identique").
- **Décision produit (validée avec Charlotte)** : si l'email est modifié et que le compte n'a
  jamais été activé (`!activationService.hasEverActivated(user)`), renvoyer automatiquement un
  nouveau code d'activation à la nouvelle adresse (réutiliser `ActivationService
  .sendActivationCode(user)`, même mécanisme que `createFormateur`/`createStagiaire` — donc après
  commit, voir `sendActivationCodeAfterCommit` existant). Si le compte est déjà activé, un
  changement d'email est une simple correction de donnée, aucun mail n'est renvoyé.
- 404 si l'id n'existe pas (`ResourceNotFoundException`, même message que les autres routes de ce
  contrôleur — pas d'oracle à fermer ici puisque seul SUPER_ADMIN peut appeler cette route et voit
  déjà tout via les listes).

Contrat API (`docs/tech.md`) :

- `PUT /api/users/{id}` — édition par SUPER_ADMIN

## Repo

[ ] front/ [x] back [ ] both

## Files to create or modify

- `dto/request/UpdateUserRequest.java` — nouveau (`nom`, `prenom`, `email`, tous optionnels ;
  `@Size(min=1, max=255)` sur les trois — voir revue — et `@Email` sur `email`)
- `controller/UserController.java` — ajouter `PUT /users/{id}`, `@PreAuthorize("hasRole('SUPER_ADMIN')")`
- `service/UserService.java` (interface) + `UserServiceImpl.java` — ajouter
  `updateUser(Long id, UpdateUserRequest request, AdacUserDetails principal)` (principal ajouté en
  revue, pour le log d'audit) ; nouveau logger, nouveau helper `sendActivationCodeIgnoringRateLimit`
- `docs/tech.md` — documenter `PUT /api/users/{id}`
- `docs/ARCHI.md` — risque résiduel accepté (JWT/email mutable, voir revue)

## Acceptance criteria

- [x] `PUT /api/users/{id}` avec `nom`/`prenom`/`email` → 200, champs fournis mis à jour, ceux
      omis (`null`) inchangés (partial update, même sémantique que `PUT /api/formations/{id}`)
- [x] `email` déjà utilisé par un **autre** compte → 409, `DuplicateEmailException` (comparaison
      insensible à la casse — voir section revue)
- [x] `email` renvoyé identique à l'actuel (même utilisateur, y compris différence de casse) →
      200, pas de faux 409
- [x] Compte jamais activé + `email` modifié → un nouveau code d'activation est envoyé à la
      **nouvelle** adresse, après commit (via `sendActivationCodeAfterCommit` — voir revue)
- [x] Compte déjà activé + `email` modifié → aucun mail envoyé
- [x] `id` inconnu → 404
- [x] Appelant non-SUPER_ADMIN (ADMIN ou STAGIAIRE) → 403
- [x] Non authentifié → 401 (test end-to-end dans `JwtAuthenticationIntegrationTest`, pas dans le
      slice `@WebMvcTest` — voir note dans `UserControllerTest`)
- [x] `role` et `isActive` ne sont jamais modifiables via cet endpoint (absents du DTO, pinné par
      un test canari en plus de la construction du DTO — voir revue)
- [x] `email`/`nom`/`prenom` vides (`""`) → 400, ne vident jamais la colonne (ajouté en revue,
      voir ci-dessous)

## Branch

`feature/users`

- [x] Create: `git checkout -b feature/users` (recréée depuis `dev` à jour — l'ancienne
      `feature/users`, TICKET-019/020, a déjà été mergée)
- [ ] Switch to existing: `git checkout feature/users`

## Write tests first (TDD)

Before writing any implementation code:

- [x] Test 1 (`@WebMvcTest(UserController.class)`): `PUT /api/users/{id}` en SUPER_ADMIN → 200 + `UserResponse`
- [x] Test 2 (`@WebMvcTest`): `PUT /api/users/{id}` en ADMIN ou STAGIAIRE → 403
- [x] Test 3 (`JwtAuthenticationIntegrationTest`, pas `@WebMvcTest` — voir note dans
      `UserControllerTest`): sans authentification → 401
- [x] Test 4 (`@ExtendWith(MockitoExtension)`): `updateUser` avec seulement `nom` fourni →
      `prenom`/`email` inchangés en base (partial update)
- [x] Test 5 (`@ExtendWith(MockitoExtension)`): `updateUser` avec `email` déjà pris par un autre
      utilisateur → `DuplicateEmailException`
- [x] Test 6 (`@ExtendWith(MockitoExtension)`): `updateUser` avec `email` inchangé (identique à
      l'actuel) → pas d'exception, 200
- [x] Test 7 (`@ExtendWith(MockitoExtension)`): `updateUser` sur un compte jamais activé avec
      `email` modifié → `activationService.sendActivationCode` appelé avec le nouvel email
- [x] Test 8 (`@ExtendWith(MockitoExtension)`): `updateUser` sur un compte déjà activé avec
      `email` modifié → `activationService.sendActivationCode` **jamais** appelé
- [x] Test 9 (`@ExtendWith(MockitoExtension)`): `updateUser` avec `id` inconnu →
      `ResourceNotFoundException`

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

**Ajoutés en revue** (voir section revue ci-dessous) : email différant seulement par la casse d'un
autre compte → 409 ; email inchangé mais casse différente → pas de 409 ; `RateLimitException` du
renvoi d'activation ne fait pas échouer la mise à jour ; `email`/`nom` vides → 400 (contrôleur) ;
canari role/isActive non modifiables.

## Pre-commit review

Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

### Constats de revue (2 agents Opus : sécurité, backend+clean-code)

**BLOCKING corrigés (3)** :
- `{"email":""}`/`{"nom":""}` passaient la validation (`@Email` valide sur `""`, `@Size` sans
  `min`) → colonne `NOT NULL UNIQUE` viderait, compte verrouillé définitivement. Fix :
  `@Size(min=1, max=255)` sur les trois champs (même convention que `UpdateFormationRequest`).
- Le mail d'activation était envoyé **dans** la transaction (`activationService
  .sendActivationCode(saved)` direct) au lieu de passer par `sendActivationCodeAfterCommit` déjà
  existant → réintroduisait le bug "code mailé pour une ligne qui rollback" déjà corrigé sur
  `createFormateur`/`createStagiaire`. Fix : réutilisation du helper existant.
- `RateLimitException` du renvoi d'activation (3 codes/15 min déjà consommés par
  `/resend-activation`) faisait échouer et rollback **toute** la correction nom/prénom/email. Fix :
  capturée et loggée dans `sendActivationCodeIgnoringRateLimit` (nouveau helper partagé) — la
  correction de données reste le résultat principal, le mail est best-effort.

**CRITICAL** :
- Vérification de doublon d'email case-sensitive (`findByEmail`) → deux comptes
  `jane@adac.fr`/`Jane@adac.fr` possibles, fait planter `ExcelImportUtil.resolveFormateur` derrière
  (bug préexistant sur `createPendingUser` aussi, pas introduit par ce ticket, mais ce ticket ajoute
  un 2e chemin avec la même faille). **Corrigé dans `updateUser`** : `findByEmailIgnoreCase` +
  comparaison `equalsIgnoreCase`. **Non corrigé** : `createPendingUser` garde le même gap, et
  aucune contrainte DB (index fonctionnel `lower(email)`) n'empêche la faille en dehors de l'API —
  hors périmètre de ce ticket, à traiter séparément si Charlotte le priorise.
- Aucun log/audit sur un changement d'identité par un SUPER_ADMIN (primitive de prise de contrôle
  silencieuse sans trace). **Corrigé** : log INFO (ids uniquement, jamais les adresses) à chaque
  appel. **Non fait** : notifier l'ancienne adresse d'un changement d'email sur un compte déjà
  activé — nécessiterait un nouveau template `EmailService` et une décision produit sur le
  wording, disclosed comme amélioration future.
- Le JWT est indexé sur l'email (`sub` = `User.email`), rendu modifiable par ce ticket — casse
  l'hypothèse "sujet stable" : changer l'email d'un compte activé tue silencieusement ses sessions,
  et l'ancienne adresse libérée pourrait authentifier un **nouveau** compte via un vieux cookie
  (jusqu'à 24h). **Décision de Charlotte : risque accepté et documenté** (`docs/ARCHI.md` —
  Authentification, même traitement que le risque logout/reset déjà accepté), pas corrigé dans ce
  ticket — recommandation d'ouvrir un ticket dédié pour basculer le JWT sur `User.id`.

**SUGGESTION appliquées** : canari role/isActive non modifiables (test dédié) ; imports statiques
au lieu de noms qualifiés dans les nouveaux tests. **Non appliquée** : rafraîchir/prévenir le
cookie de l'appelant en cas d'auto-édition de son propre email — disclosed dans `docs/tech.md`.

`mvn test` après corrections : 523/523 GREEN.

## Commit

Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):

- `feat(users): add PUT /users/{id} for SUPER_ADMIN to edit any account`

## PR (only on last ticket of this branch)

- [x] Last ticket currently planned on `feature/users` — no other backend ticket depends on or
      follows this one at time of writing. PR to be opened to `dev` once this ticket is done.

## Skills to invoke

- /java-springboot
- /spring-boot-test-patterns

## Depends on

- TICKET-019 — `UserController`, `UserService`, `UserServiceImpl` already exist
- TICKET-015 — `ActivationService.sendActivationCode` / `hasEverActivated` already exist

## Estimated time

1.5h

## Status

[ ] To do   [ ] In progress   [x] Done
