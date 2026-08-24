# TICKET-036 — Frontend — Toggle notification email

## Story
[US-016] — Préférences de notification email

## Description
Ajouter le toggle "Notifications par email" sur la page profil. L'activation/désactivation est sauvegardée via `PATCH /api/users/me`. Note informative : les emails transactionnels (activation, reset) sont toujours envoyés.

## Repo
[x] front/   [ ] back/   [ ] both

## Files to create or modify
- `src/pages/profile/ProfilePage.jsx` — ajouter la section "Préférences" avec le toggle
- `src/components/profile/EmailNotificationToggle.jsx` — toggle switch avec label et note informative
- `src/services/userService.js` — ajouter `updateProfile({ emailNotificationsEnabled })`

## Acceptance criteria
- [ ] Toggle "Recevoir les notifications par email" sur la page profil (tous les rôles)
- [ ] État initial du toggle = valeur `emailNotificationsEnabled` de l'utilisateur connecté
- [ ] Changer le toggle → `PATCH /api/users/me` envoyé immédiatement (pas de bouton "Enregistrer" séparé)
- [ ] Note informative : "Les emails de sécurité (activation, réinitialisation) sont toujours envoyés"
- [ ] Toast de confirmation "Préférences sauvegardées" après le PATCH
- [ ] En cas d'erreur API → toggle revient à l'état précédent + message d'erreur

## Branch
`feature/notifications`
- [ ] Create: `git checkout -b feature/notifications`
- [x] Switch to existing: `git checkout feature/notifications`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `EmailNotificationToggle` rend le toggle dans le bon état initial (MSW mock user avec `emailNotificationsEnabled: true`)
- [ ] Test 2 : Changer le toggle → `PATCH /api/users/me` appelé avec `emailNotificationsEnabled: false`
- [ ] Test 3 : PATCH réussi → toast "Préférences sauvegardées"
- [ ] Test 4 : PATCH échoue → toggle revient à `true` + message d'erreur

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(notifications): add email notification toggle on profile page`

## PR (only on last ticket of this branch)
- [x] This is the last ticket on `feature/notifications`
- [x] Run `/review-code` on the full branch before creating the PR
- [x] Run `/create-pr` — generates title + description, pushes, opens PR to `dev`

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /react-vite-best-practices → génère les composants React, les pages et les tests Vitest

## Depends on
- TICKET-035 — page profil partiellement construite
- TICKET-020 — endpoint `PATCH /api/users/me` avec `emailNotificationsEnabled`

## Estimated time
1h

## Status
[ ] To do   [ ] In progress   [ ] Done
