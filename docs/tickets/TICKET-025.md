# TICKET-025 — Frontend — Formulaire création + import Excel

## Story
[US-004] — Créer et gérer une formation
[US-005] — Importer une formation via Excel

## Description
Créer le formulaire de création/modification d'une formation (intitulé, dates, formateur dropdown, modalité, drag & drop docs) et le bouton d'import Excel avec feedback d'erreur ligne/colonne.

## Repo
[x] front/   [ ] back   [ ] both

## Files to create or modify
- `src/pages/formations/CreateFormationPage.jsx` — formulaire complet (React Hook Form)
- `src/pages/formations/EditFormationPage.jsx` — mêmes champs pré-remplis (réutilise le formulaire)
- `src/components/formations/FormationForm.jsx` — composant formulaire partagé
- `src/components/ExcelImportButton.jsx` — bouton upload .xlsx avec feedback
- `src/services/formationService.js` — ajouter `createFormation(data)`, `updateFormation(id, data)`, `importExcel(file)`
- `src/mocks/handlers.js` — MSW handlers pour `POST /api/formations` et `POST /api/formations/import`

## Acceptance criteria
- [ ] Champs : intitulé (requis), date début/fin (requis), formateur (dropdown actifs, optionnel), modalité visio/présentiel/mixte (requis), documents (drag & drop optionnel)
- [ ] Si formateur non sélectionné → message info "Le Super Admin sera assigné par défaut"
- [ ] Validation React Hook Form sur tous les champs requis avant soumission
- [ ] Submit réussi → redirection vers page détail de la formation créée
- [ ] Bouton "Importer un fichier Excel" → drag & drop ou sélection de fichier `.xlsx`
- [ ] Import réussi → formations créées, redirection vers liste
- [ ] Import erreur → message avec ligne/colonne problématique affiché

## Branch
`feature/formations`
- [ ] Create: `git checkout -b feature/formations`
- [x] Switch to existing: `git checkout feature/formations`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `FormationForm` rend tous les champs requis
- [ ] Test 2 : Submit sans intitulé → message validation, pas d'appel API
- [ ] Test 3 : Submit valide MSW 201 → redirection vers détail
- [ ] Test 4 : `ExcelImportButton` — upload fichier non-xlsx → message erreur côté client
- [ ] Test 5 : `ExcelImportButton` — MSW retourne 400 avec message ligne/colonne → message affiché

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(formations): add creation form and Excel import with validation`

## PR (only on last ticket of this branch)
- [x] This is the last ticket on `feature/formations`
- [x] Run `/review-code` on the full branch before creating the PR
- [x] Run `/create-pr` — generates title + description, pushes, opens PR to `dev`

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /react-vite-best-practices → génère les composants React, les pages et les tests Vitest

## Depends on
- TICKET-023 — endpoint `/api/formations/import` (ou MSW mock)

## Estimated time
3h

## Status
[ ] To do   [ ] In progress   [ ] Done
