# TICKET-029 — Backend — Messagerie individuelle

## Story
[US-013] — Messagerie individuelle

## Description
Créer les endpoints de messagerie individuelle : liste des conversations, messages dans une conversation, envoi de message, marquage comme lu. Les règles de destinataires varient selon le rôle (voir `docs/tech.md`).

**Note de révision (2026-09-05)** : les URLs ci-dessous et le marquage "lu" sont corrigés pour suivre
`docs/tech.md` tel qu'il existe réellement (et les DTOs/entités déjà posés par TICKET-005 —
`SendMessageRequest`, `ConversationResponse`, `MessageMapper` — qui anticipent tous ce contrat-là,
pas celui écrit ci-dessous à l'origine) : pas de préfixe `/conversations`, `POST` va sur
`/api/messages/send` (un seul endpoint pour l'individuel ET le groupé, `SendMessageRequest` porte
les deux formes de body — le groupé est TICKET-030, hors périmètre ici), et **`PATCH
/api/messages/{id}/read` marque un seul message** (par son id) — pas tous les messages d'une
conversation, malgré Test 5 plus bas à l'origine. Décision validée avec Charlotte : le contrat
partagé avec Manon (tech.md) prime. Contrat réel :
- `GET /api/messages` — liste des conversations
- `GET /api/messages/{conversationId}` — messages d'une conversation (`conversationId` = id de l'autre participant)
- `POST /api/messages/send` — envoyer un message individuel (`recipientIds`) — le groupé (`filter`) est TICKET-030
- `PATCH /api/messages/{id}/read` — marquer UN message comme lu

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `controller/MessageController.java` — tous les endpoints avec autorisations et Swagger
- `service/MessageService.java` (interface) + `MessageServiceImpl.java` — logique envoi, conversations, règles d'accès
- `service/NotificationService.java` (interface minimale) + `NotificationServiceImpl.java` — juste de
  quoi satisfaire l'AC "déclenche une notification" ; le CRUD complet (liste, marquer lu, etc.) est
  TICKET-033, hors périmètre ici
- `dto/request/SendMessageRequest.java`, `dto/response/MessageResponse.java`,
  `dto/response/ConversationResponse.java` — déjà posés par TICKET-005, inchangés ici
- `mapper/MessageMapper.java` — déjà posé par TICKET-005, inchangé ici

## Acceptance criteria
- [x] `POST /api/messages/send` — SUPER_ADMIN peut écrire à n'importe qui
- [x] ADMIN peut écrire à : SUPER_ADMIN, tout ADMIN actif, tout STAGIAIRE actif
- [x] STAGIAIRE peut écrire à : SUPER_ADMIN, tout ADMIN actif
- [x] Tentative d'écriture vers un rôle non autorisé → 403
- [x] `GET /api/messages` → triées par date du dernier message (plus récente en premier)
- [x] `PATCH /api/messages/{id}/read` → le message `id` marqué `readAt = now()` (un seul message, voir note de révision)
- [x] Envoi d'un message → déclenche une notification pour le destinataire (appel `NotificationService`)
- [x] Tous les endpoints documentés Swagger

Hors liste minimale, couvert en plus (voir review branch-wide) : matrice de rôles exhaustive
(`@ParameterizedTest`, 14 cas), `recipientIds` avec 0, 2+ ou `null` éléments → 400, destinataire
inconnu → 403 (pas 404, oracle d'énumération fermé), `GET /api/messages/{conversationId}` inconnu
→ `[]` (même raison), idempotence de `markAsRead`, `readAt`/`recipients` résolus par lot (pas de
N+1) sur la lecture d'un fil et sur la liste des conversations, `/api/messages/**` protégé par
cookie (`JwtAuthenticationIntegrationTest`) — 73 nouveaux tests au total (216 vs 143 avant ce
ticket).

## Branch
`feature/messagerie`
- [x] Create: `git checkout -b feature/messagerie`
- [ ] Switch to existing: `git checkout feature/messagerie`

## Write tests first (TDD)
Before writing any implementation code:
- [x] Test 1 (`@WebMvcTest(MessageController.class)`): `POST /api/messages/send` SUPER_ADMIN → STAGIAIRE → 201
- [x] Test 2 : STAGIAIRE → ADMIN → 201 ; STAGIAIRE → STAGIAIRE → 403
- [x] Test 3 (`@DataJpaTest`): requête conversations — triées par `createdAt DESC`
- [x] Test 4 (`@ExtendWith(MockitoExtension)`): `sendMessage` → `NotificationService.notify` appelé (verify mock)
- [x] Test 5 : `markAsRead(messageId)` → le message a `readAt != null` (voir note de révision — un seul
      message, pas toute la conversation)

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
[ ] To do   [ ] In progress   [x] Done
