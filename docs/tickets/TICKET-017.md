# TICKET-017 — Frontend — Page d'activation de compte

## Story
[US-002] — Activation de compte

## Description
Créer la page d'activation de compte : saisie du code à 6 chiffres reçu par email, nouveau mot de passe + confirmation, bouton "Renvoyer un code" avec feedback. Succès → redirection vers login avec bandeau de confirmation.

## Repo
[x] front/   [ ] back   [ ] both

## Files to create or modify
- `src/pages/auth/ActivationPage.jsx` — formulaire code + nouveau MDP + confirmation + bouton renvoyer
- `src/services/authService.js` — ajouter `activate(code, password)` et `resendActivation(email)`
- `src/mocks/handlers.js` — MSW handlers pour `/api/auth/activate` et `/api/auth/resend-activation`

## Acceptance criteria
- [ ] Champs : code de vérification (6 chiffres), nouveau mot de passe, confirmation du mot de passe
- [ ] Validation côté client : min 8 caractères, au moins 1 majuscule, au moins 1 chiffre
- [ ] Bouton "Renvoyer un code" visible sous le formulaire
- [ ] Code expiré/invalide → message d'erreur + bouton renvoyer mis en évidence
- [ ] Trop de renvois (429) → message "Trop de tentatives, réessayez dans 15 minutes"
- [ ] Succès → redirection vers `/login` avec bandeau vert "Compte activé avec succès"

## Branch
`feature/auth`
- [ ] Create: `git checkout -b feature/auth`
- [x] Switch to existing: `git checkout feature/auth`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `ActivationPage` rend les 3 champs + le bouton renvoyer
- [ ] Test 2 : Validation côté client — mot de passe trop court → message d'erreur, pas d'appel API
- [ ] Test 3 : Submit MSW 200 → redirection vers `/login` avec message succès
- [ ] Test 4 : Submit MSW 400 "Code expiré" → message d'erreur
- [ ] Test 5 : Renvoyer code MSW 429 → message rate-limit

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(auth): add account activation page with validation and resend`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/auth` — see TICKET-018

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /react-vite-best-practices → génère les composants React, les pages et les tests Vitest

## Depends on
- TICKET-008 — setup React
- TICKET-015 — endpoint `/api/auth/activate` (ou MSW mock)

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
