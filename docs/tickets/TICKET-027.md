# TICKET-027 — Frontend — Upload + téléchargement docs formation

## Story
[US-009] — Déposer des documents sur une formation
[US-011] — Consulter et télécharger des documents (Stagiaire)

## Description
Ajouter la section documents sur la page de détail d'une formation : drag & drop pour uploader (SUPER_ADMIN et ADMIN formateur), liste des documents avec bouton téléchargement, et suppression.

## Repo
[x] front/   [ ] back   [ ] both

## Files to create or modify
- `src/components/documents/DocumentDropzone.jsx` — drag & drop avec validation type/taille côté client
- `src/components/documents/DocumentList.jsx` — liste avec boutons télécharger et supprimer
- `src/pages/formations/FormationDetailPage.jsx` — ajouter section documents
- `src/services/documentService.js` — `uploadDocument(file, formationId)`, `getDocuments(formationId)`, `deleteDocument(id)`, `downloadDocument(id)`
- `src/mocks/handlers.js` — MSW handlers pour `/api/documents`

## Acceptance criteria
- [ ] Section "Documents" visible sur le détail de chaque formation
- [ ] SUPER_ADMIN et ADMIN (sur ses formations) : drag & drop actif + bouton supprimer
- [ ] STAGIAIRE : liste en lecture seule, pas de drag & drop
- [ ] Validation côté client : type de fichier (pdf, jpg, png, docx) et taille (max 10 Mo) avant l'envoi
- [ ] Upload réussi → document apparaît dans la liste immédiatement
- [ ] Bouton télécharger → déclenche le téléchargement via l'URL signée
- [ ] Message "Aucun document disponible pour le moment" si liste vide
- [ ] Suppression → confirmation dialog → document retiré de la liste

## Branch
`feature/documents`
- [ ] Create: `git checkout -b feature/documents`
- [x] Switch to existing: `git checkout feature/documents`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `DocumentDropzone` rend la zone drag & drop pour SUPER_ADMIN
- [ ] Test 2 : `DocumentDropzone` — fichier trop grand → message erreur, pas d'appel API
- [ ] Test 3 : `DocumentList` rend la liste des documents avec boutons télécharger
- [ ] Test 4 : STAGIAIRE → `DocumentDropzone` non affiché
- [ ] Test 5 : Upload MSW 201 → document ajouté à la liste

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(documents): add document upload and download on formation detail`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/documents` — see TICKET-028

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /react-vite-best-practices → génère les composants React, les pages et les tests Vitest

## Depends on
- TICKET-026 — endpoints `/api/documents` opérationnels (ou MSW mock)

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
