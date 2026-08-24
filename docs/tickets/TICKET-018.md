# TICKET-018 — Frontend — Mot de passe oublié / reset

## Story
[US-003] — Mot de passe oublié

## Description
Créer les deux pages du flux de réinitialisation de mot de passe : saisie de l'email (page 1), puis saisie du code + nouveau mot de passe (page 2). Même réponse affichée qu'email soit connu ou non.

## Repo
[x] front/   [ ] back/   [ ] both

## Files to create or modify
- `src/pages/auth/ForgotPasswordPage.jsx` — champ email, bouton envoyer, message générique
- `src/pages/auth/ResetPasswordPage.jsx` — champ code, nouveau MDP, confirmation
- `src/services/authService.js` — ajouter `forgotPassword(email)` et `resetPassword(code, password)`
- `src/mocks/handlers.js` — MSW handlers pour `/api/auth/forgot-password` et `/api/auth/reset-password`

## Acceptance criteria
- [ ] Page "Mot de passe oublié" : champ email + bouton + texte générique après soumission (même si email inconnu)
- [ ] Page "Réinitialiser" : code 6 chiffres + nouveau MDP + confirmation — mêmes règles de validation que l'activation
- [ ] Code expiré/invalide → message d'erreur
- [ ] Succès → redirection vers `/login` avec bandeau "Mot de passe mis à jour"
- [ ] Lien "Retour à la connexion" présent sur les deux pages

## Branch
`feature/auth`
- [ ] Create: `git checkout -b feature/auth`
- [x] Switch to existing: `git checkout feature/auth`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `ForgotPasswordPage` rend le champ email et le bouton
- [ ] Test 2 : Submit → même message affiché quelle que soit la réponse MSW (200 ou 404)
- [ ] Test 3 : `ResetPasswordPage` — validation MDP côté client
- [ ] Test 4 : Submit reset MSW 200 → redirection vers `/login` avec bandeau

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(auth): add forgot password and reset password pages`

## PR (only on last ticket of this branch)
- [x] This is the last ticket on `feature/auth`
- [x] Run `/review-code` on the full branch before creating the PR
- [x] Run `/create-pr` — generates title + description, pushes, opens PR to `dev`

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /react-vite-best-practices → génère les composants React, les pages et les tests Vitest

## Depends on
- TICKET-017 — page d'activation (mêmes patterns de formulaire et de validation)

## Estimated time
1h

## Status
[ ] To do   [ ] In progress   [ ] Done
