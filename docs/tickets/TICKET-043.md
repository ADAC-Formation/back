# TICKET-043 — Frontend — Dashboard 4 tuiles (tous rôles)

## Story
[US-001] — Connexion (redirection vers dashboard)

## Description
Créer le tableau de bord affiché après connexion. 4 tuiles colorées selon le rôle. Design ADAC : tuiles rouge, rose, orange, jaune (`#cc3d34`, `#F35E6C`, `#d9812c`, `#f6d628`), fond `#faf8f5`, police Manrope.

Tuiles par rôle :
- **SUPER_ADMIN** : Formations, Formateurs, Stagiaires, Messagerie
- **ADMIN** : Mes Formations, Messagerie, Notifications, Mon Profil
- **STAGIAIRE** : Mes Formations, Messagerie, Notifications, Mon Profil

## Repo
[x] front/   [ ] back   [ ] both

## Files to create or modify
- `src/pages/dashboard/DashboardPage.jsx` — page avec 4 tuiles adaptées au rôle
- `src/components/dashboard/DashboardTile.jsx` — composant tuile (couleur, titre, icône, lien)

## Acceptance criteria
- [ ] 4 tuiles affichées dans une grille 2×2 (responsive)
- [ ] Chaque tuile a une couleur distincte (rouge, rose, orange, jaune)
- [ ] Contenu des tuiles adapté au rôle de l'utilisateur connecté
- [ ] Cliquer une tuile → navigation vers la section correspondante
- [ ] Police Manrope chargée via Google Fonts
- [ ] Fond de page `#faf8f5` (blanc cassé)
- [ ] Responsive mobile (grille 1 colonne sur petit écran)

## Branch
`feature/dashboard`
- [x] Create: `git checkout -b feature/dashboard`
- [ ] Switch to existing: `git checkout feature/dashboard`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `DashboardPage` SUPER_ADMIN — 4 tuiles avec titres "Formations", "Formateurs", "Stagiaires", "Messagerie"
- [ ] Test 2 : `DashboardPage` ADMIN — tuile "Mes Formations" présente, "Formateurs" absente
- [ ] Test 3 : `DashboardPage` STAGIAIRE — tuiles identiques à ADMIN
- [ ] Test 4 : `DashboardTile` — cliquer navigue vers le bon lien

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(dashboard): add role-based dashboard with 4 colored tiles`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/dashboard` — see TICKET-044

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /react-vite-best-practices → génère les composants React, les pages et les tests Vitest

## Depends on
- TICKET-016 — `AuthContext` avec le rôle de l'utilisateur connecté

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
