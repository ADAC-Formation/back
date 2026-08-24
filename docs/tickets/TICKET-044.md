# TICKET-044 — Frontend — Page profil

## Story
[US-016] — Préférences de notification email

## Description
Compléter la page profil de l'utilisateur connecté : affichage des informations (nom, email, rôle), formulaire de changement de mot de passe, toggle de notifications email (ajouté en TICKET-036), et section documents stagiaire (ajoutée en TICKET-028).

## Repo
[x] front/   [ ] back/   [ ] both

## Files to create or modify
- `src/pages/profile/ProfilePage.jsx` — page complète avec toutes les sections
- `src/components/profile/ChangePasswordForm.jsx` — ancien MDP, nouveau MDP, confirmation
- `src/services/userService.js` — ajouter `changePassword(oldPassword, newPassword)`

## Acceptance criteria
- [ ] Section "Mes informations" : prénom, nom, email (lecture seule pour email)
- [ ] Section "Changer mon mot de passe" : 3 champs + validation (min 8 car., majuscule, chiffre)
- [ ] Ancien mot de passe incorrect → message "Ancien mot de passe incorrect"
- [ ] Changement de mot de passe réussi → message de succès + champs vidés
- [ ] Section "Notifications par email" : toggle (voir TICKET-036, déjà implémenté)
- [ ] Section "Mes documents" : visible uniquement pour STAGIAIRE (voir TICKET-028, déjà implémenté)
- [ ] Navigation depuis le menu principal → lien "Mon profil" accessible à tous les rôles

## Branch
`feature/dashboard`
- [ ] Create: `git checkout -b feature/dashboard`
- [x] Switch to existing: `git checkout feature/dashboard`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `ProfilePage` rend les informations de l'utilisateur connecté (MSW mock `GET /api/users/me`)
- [ ] Test 2 : `ChangePasswordForm` — submit sans remplir les champs → validation, pas d'appel API
- [ ] Test 3 : Submit MSW 200 → message succès, champs vidés
- [ ] Test 4 : MSW 401 "Ancien mot de passe incorrect" → message d'erreur affiché

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(profile): complete profile page with password change and all sections`

## PR (only on last ticket of this branch)
- [x] This is the last ticket on `feature/dashboard`
- [x] Run `/review-code` on the full branch before creating the PR
- [x] Run `/create-pr` — generates title + description, pushes, opens PR to `dev`

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /react-vite-best-practices → génère les composants React, les pages et les tests Vitest

## Depends on
- TICKET-020 — endpoint `PATCH /api/users/me`
- TICKET-036 — toggle notification email déjà implémenté sur la page profil

## Estimated time
1h

## Status
[ ] To do   [ ] In progress   [ ] Done
