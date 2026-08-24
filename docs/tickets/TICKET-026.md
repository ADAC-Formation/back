# TICKET-026 — Backend — Upload Supabase + download

## Story
[US-009] — Déposer des documents sur une formation
[US-010] — Déposer un document ciblé pour un stagiaire

## Description
Créer les endpoints de gestion des documents : upload vers Supabase Storage (URL sauvegardée en base), téléchargement, et suppression. Un document est lié soit à une formation (visible par tous les inscrits), soit à une inscription (document ciblé pour un stagiaire spécifique).

Contrat API (`docs/tech.md`) :
- `POST /api/documents` — upload (multipart, `formationId` XOR `inscriptionId`)
- `GET /api/documents?formationId={id}` — documents d'une formation
- `GET /api/documents/{id}/download` — téléchargement (URL Supabase signée)
- `DELETE /api/documents/{id}` — suppression (SUPER_ADMIN ou auteur)

## Repo
[ ] front/   [x] back/   [ ] both

## Files to create or modify
- `controller/DocumentController.java` — endpoints avec autorisations et Swagger
- `service/DocumentService.java` (interface) + `DocumentServiceImpl.java` — upload vers Supabase, validation, sauvegarde URL
- `utils/FileValidator.java` — types autorisés (pdf, jpg, png, docx) et taille max (10 Mo)
- `config/SupabaseConfig.java` — ajouter helper pour `upload(file, path)` et `getSignedUrl(path)`

## Acceptance criteria
- [ ] `POST /api/documents` avec fichier valide → 201, URL Supabase sauvegardée en base
- [ ] `POST /api/documents` avec `formationId` ET `inscriptionId` → 400 "Un document ne peut pas être lié aux deux"
- [ ] `POST /api/documents` sans aucun des deux → 400
- [ ] Fichier de type non autorisé → 400 "Format non autorisé"
- [ ] Fichier > 10 Mo → 400 "Fichier trop volumineux (max 10 Mo)"
- [ ] ADMIN peut uploader uniquement sur ses formations → 403 sinon
- [ ] `GET /api/documents?formationId={id}` : STAGIAIRE ne voit que les docs de ses formations
- [ ] `DELETE /api/documents/{id}` : SUPER_ADMIN peut tout supprimer ; ADMIN supprime ses uploads ; STAGIAIRE → 403
- [ ] Upload déclenche une notification pour les stagiaires concernés (appel `NotificationService`)

## Branch
`feature/documents`
- [x] Create: `git checkout -b feature/documents`
- [ ] Switch to existing: `git checkout feature/documents`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@WebMvcTest(DocumentController.class)`): `POST /api/documents` fichier valide, `formationId` → 201
- [ ] Test 2 : `POST /api/documents` avec `formationId` ET `inscriptionId` → 400
- [ ] Test 3 : fichier pdf → 201 ; fichier exe → 400
- [ ] Test 4 : ADMIN upload sur formation d'un autre formateur → 403
- [ ] Test 5 (`@ExtendWith(MockitoExtension)`): `uploadDocument` → `SupabaseConfig.upload` appelé, URL sauvegardée, `NotificationService.notify` appelé

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(documents): add Supabase upload, download and delete endpoints`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/documents` — see TICKET-028

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code

## Depends on
- TICKET-022 — `FormationRepository` pour valider l'accès
- TICKET-023 — `InscriptionRepository` pour les docs ciblés

## Estimated time
3h

## Status
[ ] To do   [ ] In progress   [ ] Done
