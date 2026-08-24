# TICKET-016 — Frontend — Page de connexion

## Story
[US-001] — Connexion

## Description
Créer la page de connexion ADAC : formulaire email + mot de passe, gestion des erreurs, redirection vers le dashboard selon le rôle après login réussi. Le JWT est géré par le cookie — le frontend ne le manipule pas.

Design : couleurs ADAC (`#cc3d34` rouge, `#faf8f5` fond), police Manrope, bouton "Commencer la session".

## Repo
[x] front/   [ ] back   [ ] both

## Files to create or modify
- `src/pages/auth/LoginPage.jsx` — formulaire, appel API, gestion erreurs
- `src/services/authService.js` — `login(email, password)`, `logout()`, `getMe()`
- `src/context/AuthContext.jsx` — mettre à jour avec `login()` et `logout()` qui appellent `authService`
- `src/mocks/handlers.js` — MSW handler pour `POST /api/auth/login`

## Acceptance criteria
- [ ] Formulaire avec champs Email et Mot de passe + bouton "Commencer la session"
- [ ] Lien "Mot de passe oublié / Activer mon compte" sous le formulaire
- [ ] Texte "Pas de compte ? Contactez votre administrateur" (pas de lien d'inscription)
- [ ] Login réussi → redirection vers `/dashboard` (ou `/` selon le rôle)
- [ ] Identifiants invalides → message d'erreur "Identifiants invalides"
- [ ] Compte non activé → message "Compte non activé, consultez vos emails"
- [ ] Chargement en cours → bouton désactivé / spinner

## Branch
`feature/auth`
- [ ] Create: `git checkout -b feature/auth`
- [x] Switch to existing: `git checkout feature/auth`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `LoginPage` se rend avec les champs email, password et le bouton
- [ ] Test 2 : Submit avec MSW qui retourne 200 → `AuthContext` mis à jour, redirection
- [ ] Test 3 : Submit avec MSW qui retourne 401 "Identifiants invalides" → message d'erreur affiché
- [ ] Test 4 : Submit avec MSW qui retourne 401 "Compte non activé" → message spécifique affiché
- [ ] Test 5 : Bouton désactivé pendant la requête en cours

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(auth): add login page with form, error handling and role redirect`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/auth` — see TICKET-018

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /react-vite-best-practices → génère les composants React, les pages et les tests Vitest

## Depends on
- TICKET-008 — setup React + Axios
- TICKET-014 — endpoint `POST /api/auth/login` opérationnel (ou MSW mock)

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
