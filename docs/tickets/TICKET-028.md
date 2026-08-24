# TICKET-028 — Frontend — Upload docs stagiaire (profil)

## Story
[US-012] — Déposer ses propres documents (Stagiaire)

## Description
Ajouter la section "Mes documents" sur la page profil du stagiaire : upload de documents personnels par formation (liés à l'inscription). Ces documents sont visibles par le SUPER_ADMIN.

## Repo
[x] front/   [ ] back   [ ] both

## Files to create or modify
- `src/pages/profile/ProfilePage.jsx` — ajouter section "Mes documents" (visible uniquement pour STAGIAIRE)
- `src/components/documents/StagiaireDocSection.jsx` — drag & drop par formation avec liste des docs déjà uploadés
- `src/services/documentService.js` — ajouter `uploadStagiaireDoc(file, inscriptionId)`, `getStagiaireDocuments(inscriptionId)`
- `src/mocks/handlers.js` — MSW handler pour upload ciblé stagiaire

## Acceptance criteria
- [ ] Page profil STAGIAIRE : section "Mes documents" avec une sous-section par formation inscrite
- [ ] Drag & drop actif pour uploader dans chaque sous-section (lié à l'inscription)
- [ ] Liste des documents déjà uploadés pour chaque formation
- [ ] Mêmes validations que TICKET-027 (type + taille)
- [ ] Upload réussi → message de succès + document dans la liste
- [ ] Section non visible pour ADMIN et SUPER_ADMIN sur leur propre profil

## Branch
`feature/documents`
- [ ] Create: `git checkout -b feature/documents`
- [x] Switch to existing: `git checkout feature/documents`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `ProfilePage` STAGIAIRE — section "Mes documents" présente
- [ ] Test 2 : `ProfilePage` SUPER_ADMIN — section "Mes documents" absente
- [ ] Test 3 : `StagiaireDocSection` — upload valide MSW 201 → message succès
- [ ] Test 4 : Fichier type invalide → message erreur, pas d'appel API

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(documents): add stagiaire personal document upload on profile page`

## PR (only on last ticket of this branch)
- [x] This is the last ticket on `feature/documents`
- [x] Run `/review-code` on the full branch before creating the PR
- [x] Run `/create-pr` — generates title + description, pushes, opens PR to `dev`

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /react-vite-best-practices → génère les composants React, les pages et les tests Vitest

## Depends on
- TICKET-027 — composants documents réutilisables déjà créés

## Estimated time
1h

## Status
[ ] To do   [ ] In progress   [ ] Done
