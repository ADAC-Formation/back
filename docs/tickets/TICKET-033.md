# TICKET-033 — Backend — Notifications (CRUD + logique)

## Story
[US-015] — Notifications in-app

## Description
Créer les endpoints de notifications in-app avec le comportement dual : la cloche affiche uniquement les non lues (supprimables avec `deletedFromBell = true`), la page complète affiche l'historique total (non supprimable, filtrable).

Contrat API (`docs/tech.md`) :
- `GET /api/notifications` — toutes les notifications (page complète), avec filtres
- `GET /api/notifications/unread` — uniquement les non lues et non supprimées de la cloche (`{count, notifications}`)
- `PATCH /api/notifications/{id}/read` — marquer comme lue
- `PATCH /api/notifications/read-all` — marquer toutes les notifications comme lues
- `DELETE /api/notifications/{id}` — supprimer de la cloche (`deletedFromBell = true`)

> **Écarts assumés avec cette fiche** (`docs/tech.md` prime, même règle que TICKET-029/030/032) :
> - Endpoint cloche : `/unread` (pas `/bell`), réponse `{count, notifications}` (pas un tableau nu)
> - `DELETE /api/notifications/{id}` sans suffixe `/bell` — absent de la fiche mais déjà dans `tech.md`
> - `PATCH /api/notifications/read-all` — absent de cette fiche, déjà dans `tech.md` ; ajouté ici
> - `notify(recipientId, type, content, entityType, entityId)` — signature réelle déjà posée par
>   TICKET-029 (utilisée par `MessageServiceImpl`), pas le `notify(userId, type, message)` à 3
>   arguments décrit plus bas
> - Types réels : `NEW_MESSAGE`, `DOCUMENT_UPLOADED`, `FORMATION_UPDATED` (posés par TICKET-003),
>   pas `MESSAGE`/`DOCUMENT`/`FORMATION`

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `controller/NotificationController.java` — endpoints avec Swagger
- `service/NotificationService.java` (interface) + `NotificationServiceImpl.java` — logique CRUD, `notify(...)` (signature réelle, voir écart ci-dessus)
- `repository/NotificationRepository.java` — requêtes cloche/historique/filtre + bulk update `read-all`
- `dto/response/UnreadNotificationsResponse.java` — `{count, notifications}` pour `/unread`

## Acceptance criteria
- [x] `GET /api/notifications/unread` → uniquement `isRead = false` ET `deletedFromBell = false` (capé à 50, `count` reste le total réel — voir revue)
- [x] `GET /api/notifications` → toutes les notifications de l'utilisateur (même lues, même supprimées de la cloche), capé à 200 (voir revue)
- [x] `GET /api/notifications?read=false` → filtre par statut lu/non lu
- [x] `PATCH /api/notifications/{id}/read` → `isRead = true`, idempotent
- [x] `PATCH /api/notifications/read-all` → toutes les notifications non lues de l'appelant marquées lues (bulk update)
- [x] `DELETE /api/notifications/{id}` → `deletedFromBell = true`, ne plus apparaître dans la cloche (toujours dans l'historique), idempotent
- [x] `NotificationService.notify(...)` — déjà implémenté et utilisé par `MessageServiceImpl` (TICKET-029)
- [x] Types supportés : voir écart ci-dessus (enum réel `NotificationType`)
- [x] **404** (pas 403) si l'utilisateur tente d'accéder à la notification d'un autre — décision prise
      avec Charlotte avant codage : un 403 confirmerait l'existence de la ressource à quelqu'un qui
      n'a pas le droit de la voir (même oracle déjà fermé pour `MessageService.markAsRead` et
      `FormationService` côté ADMIN non-propriétaire)

### Ajouté en revue (branch-wide, 2 agents Opus : sécurité, backend+clean-code)
- [x] `GET /api/notifications` et `/unread` cappés (200 / 50) — la table ne purge jamais
      (`DELETE` est un flag, pas une suppression), donc un historique non borné = requête et
      payload non bornés ; combiné à l'absence de rate-limit sur l'envoi de messages (déjà
      disclosé en TICKET-029), un ADMIN peut s'auto-spammer et alourdir chaque appel
- [x] `count` de `/unread` recalculé séparément (pas `notifications.size()`) pour rester exact
      même quand le nombre réel dépasse le plafond d'affichage de 50
- [x] Vérification d'appartenance poussée dans la requête (`findByIdAndRecipient`) plutôt qu'un
      fetch + comparaison en Java — rend un accès à la notification d'un autre structurellement
      impossible plutôt que dépendant d'un `if` à ne pas oublier
- [x] Log `WARN` ajouté sur l'échec de la vérification d'appartenance (seule frontière
      d'autorisation de cette fonctionnalité — sans ça, un balayage d'ids étrangers ne laissait
      aucune trace, même si la réponse 404 reste correcte côté client)
- [x] `@Modifying` du bulk `read-all` complété avec `flushAutomatically = true` (en plus de
      `clearAutomatically = true` déjà présent) — sans ça, un futur appelant composant cette
      requête dans une transaction plus large perdrait silencieusement des écritures en attente
- [x] Tests contrôleur corrigés : le principal était matché avec `any()` au lieu de
      `eq(currentPrincipal())` (convention `WithMockAdacUser`), ce qui aurait laissé passer un
      binding de principal cassé sans le détecter — critique ici puisqu'il n'y a aucune autre
      couche d'autorisation sur ces endpoints
- [x] `MethodArgumentTypeMismatchException` géré dans `GlobalExceptionHandler` (`?read=abc` → 400
      avec le bon format d'erreur, pas le body par défaut de Spring Boot)
- [ ] **Déféré, signalé à Charlotte** : `content` des notifications (ex. "Nouveau message de {nom}
      {prénom}") vient de champs utilisateur sans restriction de caractères (`CreateUserRequest`,
      TICKET-019) et est maintenant lu par le frontend (avant ce ticket, aucun endpoint ne le
      renvoyait). Pas de risque côté backend (Jackson échappe), mais si le frontend affiche
      `content` via un rendu HTML brut, un `nom` contenant du markup devient du XSS stocké chez
      tous les destinataires. À coordonner avec Manon (rendu texte, pas HTML) et/ou restreindre
      `nom`/`prenom` dans TICKET-019 — hors périmètre de ce ticket.

## Branch
`feature/notifications`
- [x] Create: `git checkout -b feature/notifications`
- [ ] Switch to existing: `git checkout feature/notifications`

## Write tests first (TDD)
Before writing any implementation code:
- [x] Test 1 (`@WebMvcTest(NotificationController.class)`): `GET /api/notifications/unread` → uniquement non lues et non supprimées de la cloche
- [x] Test 2 : `DELETE /api/notifications/{id}` → 204 ; notification toujours dans `GET /api/notifications`
- [x] Test 3 (`@DataJpaTest`): requête cloche — ne retourne pas les lues ni les supprimées
- [x] Test 4 (`@ExtendWith(MockitoExtension)`): `notify(...)` → notification sauvegardée (déjà couvert par TICKET-029)

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(notifications): add notification endpoints with bell and history views`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/notifications` — see TICKET-036

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code
- [x] /jpa-patterns → génère les entités JPA, les repositories et les @Query custom

## Depends on
- TICKET-005 — `NotificationRepository` avec requêtes custom
- TICKET-029 — `MessageService` appelle `NotificationService.notify`

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [x] Done
