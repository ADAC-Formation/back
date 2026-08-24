# TICKET-030 — Backend — Messagerie groupée + filtres

## Story
[US-014] — Messagerie groupée

## Description
Implémenter l'envoi de messages groupés avec filtres de destinataires. Le SUPER_ADMIN peut filtrer par formation, documents manquants ou sélection libre. Le ADMIN peut filtrer par formation uniquement.

Contrat API (`docs/tech.md`) :
- `POST /api/messages/group` — envoyer un message groupé
- `GET /api/messages/group/preview` — prévisualiser les destinataires avant envoi

## Repo
[ ] front/   [x] back/   [ ] both

## Files to create or modify
- `controller/MessageController.java` — ajouter `POST /messages/group` et `GET /messages/group/preview`
- `service/MessageService.java` (extend) + `MessageServiceImpl.java` — logique de filtrage, fan-out vers chaque destinataire
- `dto/request/SendGroupMessageRequest.java` — `content`, `filterType` (FORMATION / MISSING_DOCS / FREE_SELECT), `formationId` (optionnel), `userIds` (optionnel pour FREE_SELECT)

## Acceptance criteria
- [ ] `POST /api/messages/group` SUPER_ADMIN, filtre FORMATION → un message individuel créé pour chaque inscrit à la formation
- [ ] `POST /api/messages/group` SUPER_ADMIN, filtre MISSING_DOCS → destinataires = stagiaires sans documents requis
- [ ] `POST /api/messages/group` SUPER_ADMIN, filtre FREE_SELECT → destinataires = `userIds` fournis
- [ ] `POST /api/messages/group` ADMIN, filtre FORMATION → destinataires = inscrits à ses formations uniquement
- [ ] `POST /api/messages/group` ADMIN, filtre MISSING_DOCS ou FREE_SELECT → 403
- [ ] `GET /api/messages/group/preview?filterType=FORMATION&formationId=1` → liste des destinataires sans envoyer
- [ ] Chaque destinataire reçoit une notification individuelle

## Branch
`feature/messagerie`
- [ ] Create: `git checkout -b feature/messagerie`
- [x] Switch to existing: `git checkout feature/messagerie`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@WebMvcTest`): `POST /api/messages/group` SUPER_ADMIN FORMATION → 201
- [ ] Test 2 : ADMIN filtre FREE_SELECT → 403
- [ ] Test 3 (`@ExtendWith(MockitoExtension)`): `sendGroupMessage` FORMATION avec 3 inscrits → 3 messages créés, 3 notifications envoyées
- [ ] Test 4 : `previewGroupRecipients` MISSING_DOCS → retourne uniquement les stagiaires sans documents

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(messaging): add group messaging with formation and document filters`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/messagerie` — see TICKET-032

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code
- [x] /jpa-patterns → génère les entités JPA, les repositories et les @Query custom

## Depends on
- TICKET-029 — `MessageService` de base et `MessageRecipient`
- TICKET-022 — `FormationRepository` pour les filtres par formation

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
