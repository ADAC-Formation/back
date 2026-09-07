# TICKET-026 — Backend — Upload Supabase + download

## Story
[US-009] — Déposer des documents sur une formation
[US-010] — Déposer un document ciblé pour un stagiaire

## Description
Créer les endpoints de gestion des documents : upload vers Supabase Storage (URL sauvegardée en base), téléchargement, et suppression. Un document est lié soit à une formation (visible par tous les inscrits), soit à une inscription (document ciblé pour un stagiaire spécifique).

Contrat API (`docs/tech.md`) :
- `POST /api/documents` — upload (multipart, `formationId` XOR `inscriptionId`)
- `GET /api/documents?formationId={id}` — documents d'une formation
- `GET /api/documents/{id}/download` — téléchargement (proxy binaire depuis Supabase, pas de redirect)
- `DELETE /api/documents/{id}` — suppression (SUPER_ADMIN, ou ADMIN sur ses propres uploads)

> **Écart assumé** : `SupabaseConfig` (TICKET-007) documentait déjà `StorageService`/`StorageServiceImpl`
> comme classe séparée pour l'appel HTTP (cf. `docs/ARCHI.md`, préexistant) — implémenté ainsi plutôt
> que d'ajouter `upload()`/`getSignedUrl()` directement sur `SupabaseConfig` comme décrit plus bas dans
> "Files to create or modify". `SupabaseConfig` reste un pur config/URL-builder testable sans HTTP.

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `controller/DocumentController.java` — endpoints avec autorisations et Swagger
- `service/DocumentService.java` (interface) + `DocumentServiceImpl.java` — upload vers Supabase, validation, sauvegarde URL
- `service/StorageService.java` (interface) + `StorageServiceImpl.java` — HTTP vers Supabase Storage (voir écart ci-dessus)
- `utils/FileValidator.java` — types autorisés (pdf, jpg, png, docx), taille max (10 Mo), et signature magic-bytes
- `exception/StorageException.java` — 502, échec Supabase Storage (ajouté en revue)

## Acceptance criteria
- [x] `POST /api/documents` avec fichier valide → 201, URL Supabase sauvegardée en base
- [x] `POST /api/documents` avec `formationId` ET `inscriptionId` → 400 "Un document ne peut pas être lié aux deux"
- [x] `POST /api/documents` sans aucun des deux → 400
- [x] Fichier de type non autorisé → 400 "Format non autorisé" (extension **et** signature magic-bytes, ajouté en revue)
- [x] Fichier > 10 Mo → 400 "Fichier trop volumineux (max 10 Mo)"
- [x] ADMIN peut uploader uniquement sur ses formations → 403 sinon
- [x] `GET /api/documents?formationId={id}` : STAGIAIRE ne voit que les docs de ses formations
- [x] `DELETE /api/documents/{id}` : SUPER_ADMIN peut tout supprimer ; ADMIN supprime ses uploads ; STAGIAIRE → 403
      (voir note de révision : la formulation `tech.md` a été corrigée pour matcher cette règle, pas l'inverse)
- [x] Upload déclenche une notification pour les stagiaires concernés (appel `NotificationService`, déjà réel depuis TICKET-029)

### Ajouté en revue (branch-wide, 3 agents : sécurité, backend, clean-code)
- [x] `DELETE` supprime aussi l'objet Supabase, pas seulement la ligne en base (fuite RGPD sinon)
- [x] `Content-Disposition` construit via `ContentDisposition` (pas de concaténation — injection de paramètre sinon)
- [x] `mimeType` dérivé serveur-side de l'extension, jamais du header client
- [x] Timeout connect/read sur le `RestTemplate` Supabase
- [x] Upload hors transaction Spring ; échec de persistance après upload réussi → suppression compensatoire de l'objet
- [x] `RestTemplate.exchange(URI, ...)` au lieu de `exchange(String, ...)` (évitait un double-encodage de l'URL déjà encodée)
- [x] `@EntityGraph(attributePaths = "uploadedBy")` sur `DocumentRepository` (N+1 sur la liste)
- [x] ADMIN non-propriétaire sur `GET` → 404 (pas 403), cohérent avec `FormationServiceImpl`
- [x] Pas d'auto-notification quand un stagiaire dépose sur sa propre inscription
- [ ] **Déféré** : `downloadDocument` garde l'appel Supabase à l'intérieur de sa transaction en lecture
      seule (nécessiterait un fetch-graph plus profond ou un second bean pour séparer proprement sans
      risquer une `LazyInitializationException` — le timeout ajouté borne le risque au lieu de l'éliminer)
- [ ] **Déféré** : pas de rate-limiting/quota sur l'upload, pas de streaming sur le download (mêmes
      raisons que la pagination/rate-limiting différés en TICKET-029 — nécessitent une décision produit)

## Branch
`feature/documents`
- [x] Create: `git checkout -b feature/documents`
- [ ] Switch to existing: `git checkout feature/documents`

## Write tests first (TDD)
Before writing any implementation code:
- [x] Test 1 (`@WebMvcTest(DocumentController.class)`): `POST /api/documents` fichier valide, `formationId` → 201
- [x] Test 2 : `POST /api/documents` avec `formationId` ET `inscriptionId` → 400
- [x] Test 3 : fichier pdf → 201 ; fichier exe → 400
- [x] Test 4 : ADMIN upload sur formation d'un autre formateur → 403
- [x] Test 5 (`@ExtendWith(MockitoExtension)`): `uploadDocument` → `StorageService.upload` appelé, URL sauvegardée, `NotificationService.notify` appelé

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
[ ] To do   [ ] In progress   [x] Done
