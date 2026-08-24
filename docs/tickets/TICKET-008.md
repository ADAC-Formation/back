# TICKET-008 — Setup React + Vite + Axios

## Story
Foundation — structure de départ du projet frontend

## Description
Initialiser le projet React avec Vite, configurer Axios avec `withCredentials: true` par défaut, mettre en place React Router v6, la structure de dossiers src/, et les outils de test (Vitest + Testing Library + MSW).

## Repo
[x] front/   [ ] back/   [ ] both

## Files to create or modify
- `front/package.json` — dépendances : react, react-dom, react-router-dom, axios, react-hook-form, vitest, @testing-library/react, @testing-library/user-event, msw
- `front/vite.config.js` — config Vite avec plugin React, alias `@/` → `src/`
- `front/.env` + `front/.env.example` — `VITE_API_URL=http://localhost:8080/api`
- `front/src/api/axios.js` — instance Axios avec `baseURL` depuis env, `withCredentials: true`
- `front/src/router.jsx` — React Router v6 : routes publiques (login, activation, reset) et routes protégées par rôle
- `front/src/App.jsx` — entrée principale avec `RouterProvider`
- `front/src/main.jsx` — point d'entrée
- `front/src/context/AuthContext.jsx` — contexte auth (user courant, isAuthenticated, logout)
- `front/src/components/ProtectedRoute.jsx` — HOC qui redirige vers /login si non authentifié
- `front/vitest.config.js` — config Vitest avec jsdom
- `front/src/mocks/handlers.js` + `front/src/mocks/server.js` — MSW setup

## Acceptance criteria
- [ ] `npm run dev` démarre sur `http://localhost:5173` sans erreur
- [ ] `npm run test` exécute les tests (Vitest)
- [ ] Toutes les requêtes Axios partent avec `withCredentials: true` et `baseURL = VITE_API_URL`
- [ ] Structure de dossiers créée : `pages/`, `components/`, `services/`, `context/`, `api/`, `mocks/`
- [ ] `ProtectedRoute` redirige vers `/login` si l'utilisateur n'est pas connecté

## Branch
`feature/setup`
- [ ] Create: `git checkout -b feature/setup`
- [x] Switch to existing: `git checkout feature/setup`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `App` se rend sans erreur (render + snapshot)
- [ ] Test 2 : Accéder à une route protégée sans être connecté → redirection vers `/login`
- [ ] Test 3 : L'instance Axios a bien `withCredentials: true` et `baseURL` défini

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `chore(setup): initialize React + Vite project with Axios and router`

## PR (only on last ticket of this branch)
- [x] This is the last ticket on `feature/setup`
- [x] Run `/review-code` on the full branch before creating the PR
- [x] Run `/create-pr` — generates title + description, pushes, opens PR to `dev`

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /react-vite-best-practices → génère les composants React, les pages et les tests Vitest

## Depends on
— (indépendant du backend)

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
