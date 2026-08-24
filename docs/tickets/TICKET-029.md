# TICKET-029 — Backend — Messagerie individuelle

## Story
[US-013] — Messagerie individuelle

## Description
Créer les endpoints de messagerie individuelle : liste des conversations, messages dans une conversation, envoi de message, marquage comme lu. Les règles de destinataires varient selon le rôle (voir `docs/tech.md`).

Contrat API (`docs/tech.md`) :
- `GET /api/messages/conversations` — liste des conversations
- `GET /api/messages/conversations/{conversationId}` — messages d'une conversation
- `POST /api/messages` — envoyer un message individuel
- `PATCH /api/messages/conversations/{conversationId}/read` — marquer comme lu

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `controller/MessageController.java` — tous les endpoints avec autorisations et Swagger
- `service/MessageService.java` (interface) + `MessageServiceImpl.java` — logique envoi, conversations, règles d'accès
- `dto/request/SendMessageRequest.java` — `recipientId`, `content`
- `dto/response/MessageResponse.java` — `id`, `sender`, `content`, `sentAt`, `readAt`
- `dto/response/ConversationResponse.java` — `otherUser`, `lastMessage`, `unreadCount`
- `mapper/MessageMapper.java`

## Acceptance criteria
- [ ] `POST /api/messages` — SUPER_ADMIN peut écrire à n'importe qui
- [ ] ADMIN peut écrire à : SUPER_ADMIN, tout ADMIN actif, tout STAGIAIRE actif
- [ ] STAGIAIRE peut écrire à : SUPER_ADMIN, tout ADMIN actif
- [ ] Tentative d'écriture vers un rôle non autorisé → 403
- [ ] `GET /api/messages/conversations` → triées par date du dernier message (plus récente en premier)
- [ ] `PATCH /api/messages/conversations/{id}/read` → tous les messages non lus de la conversation marqués `readAt = now()`
- [ ] Envoi d'un message → déclenche une notification pour le destinataire (appel `NotificationService`)
- [ ] Tous les endpoints documentés Swagger

## Branch
`feature/messagerie`
- [x] Create: `git checkout -b feature/messagerie`
- [ ] Switch to existing: `git checkout feature/messagerie`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@WebMvcTest(MessageController.class)`): `POST /api/messages` SUPER_ADMIN → STAGIAIRE → 201
- [ ] Test 2 : STAGIAIRE → ADMIN → 201 ; STAGIAIRE → STAGIAIRE → 403
- [ ] Test 3 (`@DataJpaTest`): requête conversations — triées par `sentAt DESC`
- [ ] Test 4 (`@ExtendWith(MockitoExtension)`): `sendMessage` → `NotificationService.notify` appelé (verify mock)
- [ ] Test 5 : `markConversationAsRead` → tous les messages de la conversation ont `readAt != null`

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(messaging): add individual messaging endpoints with role-based access`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/messagerie` — see TICKET-032

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code
- [x] /jpa-patterns → génère les entités JPA, les repositories et les @Query custom

## Depends on
- TICKET-005 — `MessageRepository`, `MessageMapper`, DTOs

## Estimated time
3h

## Status
[ ] To do   [ ] In progress   [ ] Done
