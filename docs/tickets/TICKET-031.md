# TICKET-031 — Frontend — Interface messagerie individuelle

## Story
[US-013] — Messagerie individuelle

## Description
Créer l'interface de messagerie individuelle : liste des conversations à gauche, thread de messages à droite, formulaire d'envoi. Messages non lus mis en évidence. Accès depuis le bouton "Envoyer un message" des profils utilisateurs.

## Repo
[x] front/   [ ] back/   [ ] both

## Files to create or modify
- `src/pages/messaging/MessagingPage.jsx` — layout deux colonnes (liste + thread)
- `src/components/messaging/ConversationList.jsx` — liste des conversations, badge non lus, tri par date
- `src/components/messaging/MessageThread.jsx` — messages de la conversation sélectionnée, scroll auto en bas
- `src/components/messaging/MessageInput.jsx` — textarea + bouton envoyer
- `src/services/messagingService.js` — `getConversations()`, `getMessages(conversationId)`, `sendMessage(recipientId, content)`, `markAsRead(conversationId)`
- `src/mocks/handlers.js` — MSW handlers pour `/api/messages`

## Acceptance criteria
- [ ] Liste des conversations triée par date (plus récente en premier), badge "non lu" sur les conversations avec messages non lus
- [ ] Sélectionner une conversation → thread s'affiche à droite, messages marqués comme lus
- [ ] Formulaire d'envoi : textarea + bouton "Envoyer" — appui sur Entrée envoie (Shift+Entrée = saut de ligne)
- [ ] Nouveau message → ajouté au thread immédiatement (optimistic update)
- [ ] Depuis un profil utilisateur avec "Envoyer un message" → ouvre la conversation avec cet utilisateur
- [ ] Message d'état "Aucune conversation" si liste vide

## Branch
`feature/messagerie`
- [ ] Create: `git checkout -b feature/messagerie`
- [x] Switch to existing: `git checkout feature/messagerie`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `ConversationList` rend les conversations avec badges non lus (MSW mock)
- [ ] Test 2 : Cliquer une conversation → `MessageThread` s'affiche avec les messages
- [ ] Test 3 : `MessageInput` — Entrée envoie, appel API `POST /api/messages`
- [ ] Test 4 : Après sélection conversation → `markAsRead` appelé, badge disparu
- [ ] Test 5 : Navigation depuis profil avec `recipientId` → conversation correcte ouverte

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(messaging): add individual messaging interface with conversation list and thread`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/messagerie` — see TICKET-032

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /react-vite-best-practices → génère les composants React, les pages et les tests Vitest

## Depends on
- TICKET-029 — endpoints messagerie individuelle (ou MSW mock)

## Estimated time
3h

## Status
[ ] To do   [ ] In progress   [ ] Done
