# TICKET-024 — Frontend — Liste et détail des formations

## Story
[US-004] — Créer et gérer une formation

## Description
Créer les pages de liste et de détail des formations. La liste inclut filtres actives/archivées. Le détail affiche le formateur assigné, les stagiaires inscrits, les documents associés. Vue adaptée selon le rôle.

## Repo
[x] front/   [ ] back   [ ] both

## Files to create or modify
- `src/pages/formations/FormationListPage.jsx` — liste avec filtres status et formateur
- `src/pages/formations/FormationDetailPage.jsx` — détail : infos, formateur, inscrits, documents
- `src/services/formationService.js` — `getFormations(params)`, `getFormationById(id)`, `archiveFormation(id)`
- `src/mocks/handlers.js` — MSW handlers pour `/api/formations`

## Acceptance criteria
- [ ] Liste des formations : filtre actives / archivées, affichage carte (titre, dates, formateur, nb inscrits)
- [ ] SUPER_ADMIN voit toutes les formations et les boutons Modifier + Archiver
- [ ] ADMIN voit toutes les formations, bouton "Mes formations" actif par défaut, pas de bouton Modifier
- [ ] STAGIAIRE voit uniquement ses formations inscrites
- [ ] Page détail : titre, dates, modalité, formateur, liste des stagiaires inscrits, documents
- [ ] Bouton "Archiver" sur la page détail (SUPER_ADMIN) → confirmation + navigation vers liste
- [ ] Formation archivée affichée en lecture seule avec badge "Archivée"

## Branch
`feature/formations`
- [ ] Create: `git checkout -b feature/formations`
- [x] Switch to existing: `git checkout feature/formations`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `FormationListPage` rend les formations (MSW mock) avec leurs cartes
- [ ] Test 2 : Filtre "Archivées" → appelle API avec `status=ARCHIVED`
- [ ] Test 3 : `FormationDetailPage` affiche le formateur et le nombre d'inscrits
- [ ] Test 4 : STAGIAIRE → boutons Modifier/Archiver absents
- [ ] Test 5 : Bouton "Archiver" SUPER_ADMIN → confirmation dialog → appelle `PATCH /api/formations/{id}/archive`

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(formations): add formation list and detail pages with role-based views`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/formations` — see TICKET-025

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /react-vite-best-practices → génère les composants React, les pages et les tests Vitest

## Depends on
- TICKET-022 — endpoints `/api/formations` opérationnels (ou MSW mock)

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
