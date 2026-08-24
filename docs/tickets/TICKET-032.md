# TICKET-032 — Frontend — Messagerie groupée

## Story
[US-014] — Messagerie groupée

## Description
Créer l'interface de composition d'un message groupé avec sélection de filtre (formation, documents manquants, sélection libre pour SUPER_ADMIN ; formation uniquement pour ADMIN), prévisualisation des destinataires, et confirmation avant envoi.

## Repo
[x] front/   [ ] back   [ ] both

## Files to create or modify
- `src/pages/messaging/GroupMessagePage.jsx` — page de composition de message groupé
- `src/components/messaging/GroupFilters.jsx` — filtres par type (FORMATION / MISSING_DOCS / FREE_SELECT)
- `src/components/messaging/RecipientPreview.jsx` — liste des destinataires avant confirmation
- `src/services/messagingService.js` — ajouter `previewGroupRecipients(filterType, params)`, `sendGroupMessage(data)`
- `src/mocks/handlers.js` — MSW handlers pour `/api/messages/group`

## Acceptance criteria
- [ ] Bouton "Message groupé" dans l'interface messagerie (visible uniquement pour SUPER_ADMIN et ADMIN)
- [ ] SUPER_ADMIN : 3 options de filtre (Formation, Documents manquants, Sélection libre)
- [ ] ADMIN : 1 seule option (Formation) — autres options non affichées
- [ ] Après sélection du filtre → liste des destinataires prévisualisée automatiquement
- [ ] Zone de texte pour le message + bouton "Envoyer à X destinataires"
- [ ] Envoi réussi → message de confirmation "Message envoyé à X destinataires" + retour à la liste
- [ ] 0 destinataires → bouton désactivé + message "Aucun destinataire correspondant"

## Branch
`feature/messagerie`
- [ ] Create: `git checkout -b feature/messagerie`
- [x] Switch to existing: `git checkout feature/messagerie`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `GroupMessagePage` SUPER_ADMIN — 3 filtres affichés
- [ ] Test 2 : `GroupMessagePage` ADMIN — 1 seul filtre (Formation)
- [ ] Test 3 : Sélection filtre FORMATION → `previewGroupRecipients` appelé, liste affichée
- [ ] Test 4 : 0 destinataires → bouton envoi désactivé
- [ ] Test 5 : Submit MSW 201 → message de confirmation affiché

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(messaging): add group messaging page with role-based filters`

## PR (only on last ticket of this branch)
- [x] This is the last ticket on `feature/messagerie`
- [x] Run `/review-code` on the full branch before creating the PR
- [x] Run `/create-pr` — generates title + description, pushes, opens PR to `dev`

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /react-vite-best-practices → génère les composants React, les pages et les tests Vitest

## Depends on
- TICKET-030 — endpoints messagerie groupée (ou MSW mock)
- TICKET-031 — interface messagerie de base déjà créée

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
