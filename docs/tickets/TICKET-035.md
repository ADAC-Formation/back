# TICKET-035 — Frontend — Cloche + page notifications

## Story
[US-015] — Notifications in-app

## Description
Créer la cloche de notifications dans le header (badge avec compteur non lus, panneau déroulant) et la page d'historique complet. La cloche n'affiche que les non lues ; la page historique affiche tout.

## Repo
[x] front/   [ ] back   [ ] both

## Files to create or modify
- `src/components/notifications/NotificationBell.jsx` — icône cloche + badge non lus + panneau au clic
- `src/components/notifications/NotificationPanel.jsx` — liste des notifications non lues avec bouton supprimer de la cloche
- `src/components/notifications/NotificationItem.jsx` — une notification avec type, message, date, lien, bouton supprimer
- `src/pages/notifications/NotificationsPage.jsx` — historique complet avec filtres (lues/non lues, date)
- `src/services/notificationService.js` — `getBellNotifications()`, `getAllNotifications(params)`, `markAsRead(id)`, `deleteFromBell(id)`
- `src/mocks/handlers.js` — MSW handlers pour `/api/notifications`

## Acceptance criteria
- [ ] Cloche dans le header sur toutes les pages authentifiées avec badge rouge (compteur non lus)
- [ ] Cliquer la cloche → panneau s'ouvre, affiche uniquement les non lues
- [ ] Cliquer une notification → navigation vers l'élément concerné + marquage comme lue + retrait du panneau
- [ ] Bouton "✕" sur chaque notification du panneau → `deletedFromBell` (reste dans l'historique)
- [ ] Panneau vide → message "Aucune nouvelle notification"
- [ ] Page "Notifications" : affiche TOUT l'historique (lues + non lues)
- [ ] Filtres sur la page : par statut (lues/non lues), par date
- [ ] Types de notifications affichés différemment (icône ou couleur) : MESSAGE, DOCUMENT, FORMATION

## Branch
`feature/notifications`
- [ ] Create: `git checkout -b feature/notifications`
- [x] Switch to existing: `git checkout feature/notifications`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `NotificationBell` — badge affiche le bon compteur (MSW mock 3 non lues)
- [ ] Test 2 : Cliquer la cloche → panneau s'ouvre avec les notifications
- [ ] Test 3 : Cliquer une notification → `markAsRead` appelé, badge décrémenté
- [ ] Test 4 : Bouton "✕" → `deleteFromBell` appelé, notification retirée du panneau
- [ ] Test 5 : `NotificationsPage` — filtre "Non lues" → appelle API avec `?read=false`

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(notifications): add notification bell with panel and full history page`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/notifications` — see TICKET-036

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /react-vite-best-practices → génère les composants React, les pages et les tests Vitest

## Depends on
- TICKET-033 — endpoints `/api/notifications` (ou MSW mock)

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
