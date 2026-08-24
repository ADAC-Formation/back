# TICKET-021 — Frontend — Gestion des comptes

## Story
[US-007] — Gérer les comptes Formateurs
[US-008] — Gérer les comptes Stagiaires

## Description
Créer les pages de gestion des comptes formateurs et stagiaires : liste avec filtres actif/suspendu, modal de création, boutons suspension/réactivation, lien "Envoyer un message". Accessible uniquement par le SUPER_ADMIN.

## Repo
[x] front/   [ ] back   [ ] both

## Files to create or modify
- `src/pages/users/FormateurListPage.jsx` — liste avec filtre actif/suspendu, bouton créer, bouton suspendre/réactiver
- `src/pages/users/StagiaireListPage.jsx` — idem + colonne formation(s) inscrite(s)
- `src/components/users/CreateUserModal.jsx` — formulaire création (nom, prénom, email, rôle, formation pour stagiaire)
- `src/pages/users/UserDetailPage.jsx` — profil avec bouton "Envoyer un message"
- `src/services/userService.js` — `getUsers(role, isActive)`, `createUser(data)`, `toggleUserStatus(id, isActive)`, `getUserById(id)`
- `src/mocks/handlers.js` — MSW handlers pour `/api/users`

## Acceptance criteria
- [ ] Liste formateurs : filtre actif/suspendu, bouton "Créer un formateur"
- [ ] Liste stagiaires : filtre actif/suspendu, bouton "Créer un stagiaire", colonne formations
- [ ] Modal de création valide les champs obligatoires avant envoi
- [ ] Après création → liste mise à jour + notification "Compte créé, email d'activation envoyé"
- [ ] Bouton "Suspendre" / "Réactiver" sur chaque ligne
- [ ] Page détail utilisateur → bouton "Envoyer un message" (redirige vers messagerie)
- [ ] Page non accessible si rôle ≠ SUPER_ADMIN (protection de route)

## Branch
`feature/users`
- [ ] Create: `git checkout -b feature/users`
- [x] Switch to existing: `git checkout feature/users`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `FormateurListPage` rend la liste et le bouton créer (MSW mock)
- [ ] Test 2 : `CreateUserModal` — submit avec email invalide → message d'erreur côté client, pas d'appel API
- [ ] Test 3 : `CreateUserModal` — submit valide MSW 201 → modal fermée, liste mise à jour
- [ ] Test 4 : Bouton "Suspendre" → appelle `PATCH /api/users/{id}` avec `isActive=false`
- [ ] Test 5 : Route `/users` redirige vers `/login` si non SUPER_ADMIN

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(users): add formateur and stagiaire management pages`

## PR (only on last ticket of this branch)
- [x] This is the last ticket on `feature/users`
- [x] Run `/review-code` on the full branch before creating the PR
- [x] Run `/create-pr` — generates title + description, pushes, opens PR to `dev`

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /react-vite-best-practices → génère les composants React, les pages et les tests Vitest

## Depends on
- TICKET-019 — endpoints backend `/api/users` opérationnels (ou MSW mock)

## Estimated time
3h

## Status
[ ] To do   [ ] In progress   [ ] Done
