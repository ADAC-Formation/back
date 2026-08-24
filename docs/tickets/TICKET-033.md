# TICKET-033 — Backend — Notifications (CRUD + logique)

## Story
[US-015] — Notifications in-app

## Description
Créer les endpoints de notifications in-app avec le comportement dual : la cloche affiche uniquement les non lues (supprimables avec `deletedFromBell = true`), la page complète affiche l'historique total (non supprimable, filtrable).

Contrat API (`docs/tech.md`) :
- `GET /api/notifications` — toutes les notifications (page complète), avec filtres
- `GET /api/notifications/bell` — uniquement les non lues et non supprimées de la cloche
- `PATCH /api/notifications/{id}/read` — marquer comme lue
- `DELETE /api/notifications/{id}/bell` — supprimer de la cloche (`deletedFromBell = true`)

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `controller/NotificationController.java` — endpoints avec Swagger
- `service/NotificationService.java` (interface) + `NotificationServiceImpl.java` — logique CRUD, `notify(userId, type, message, referenceId)`
- `dto/response/NotificationResponse.java` — `id`, `type`, `message`, `readAt`, `createdAt`, `deletedFromBell`
- `mapper/NotificationMapper.java`

## Acceptance criteria
- [ ] `GET /api/notifications/bell` → uniquement `readAt IS NULL` ET `deletedFromBell = false`
- [ ] `GET /api/notifications` → toutes les notifications de l'utilisateur (même lues, même supprimées de la cloche)
- [ ] `GET /api/notifications?read=false` → filtre par statut lu/non lu
- [ ] `PATCH /api/notifications/{id}/read` → `readAt = now()`, retirer du compteur cloche
- [ ] `DELETE /api/notifications/{id}/bell` → `deletedFromBell = true`, ne plus apparaître dans la cloche (toujours dans l'historique)
- [ ] `NotificationService.notify(userId, type, message)` — méthode publique utilisée par Message/DocumentService
- [ ] Types supportés : `MESSAGE`, `DOCUMENT`, `FORMATION`
- [ ] 403 si l'utilisateur tente d'accéder aux notifications d'un autre

## Branch
`feature/notifications`
- [x] Create: `git checkout -b feature/notifications`
- [ ] Switch to existing: `git checkout feature/notifications`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@WebMvcTest(NotificationController.class)`): `GET /api/notifications/bell` → uniquement non lues et non supprimées de la cloche
- [ ] Test 2 : `DELETE /api/notifications/{id}/bell` → 204 ; notification toujours dans `GET /api/notifications`
- [ ] Test 3 (`@DataJpaTest`): `findAllByUserAndDeletedFromBellFalse` — ne retourne pas les supprimées
- [ ] Test 4 (`@ExtendWith(MockitoExtension)`): `notify(userId, MESSAGE, "Nouveau message")` → notification sauvegardée

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
[ ] To do   [ ] In progress   [ ] Done
