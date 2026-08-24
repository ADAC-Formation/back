# TICKET-023 — Backend — Import Excel + Inscriptions

## Story
[US-005] — Importer une formation via Excel
[US-006] — Inscrire des stagiaires à une formation

## Description
Implémenter l'import de formations depuis un fichier `.xlsx` (Apache POI) et les endpoints d'inscription de stagiaires à une formation. Un stagiaire ne peut être inscrit qu'une fois par formation.

Contrat API (`docs/tech.md`) :
- `POST /api/formations/import` — fichier xlsx multipart
- `GET /api/formations/{id}/inscriptions` — liste des inscrits
- `POST /api/formations/{id}/inscriptions` — inscrire un stagiaire
- `DELETE /api/formations/{id}/inscriptions/{userId}` — désinscrire

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `controller/FormationController.java` — ajouter `POST /formations/import`
- `controller/InscriptionController.java` — CRUD inscriptions
- `service/InscriptionService.java` (interface) + `InscriptionServiceImpl.java`
- `utils/ExcelImportUtil.java` — parser le fichier .xlsx avec Apache POI, retourner une liste de `CreateFormationRequest`
- `dto/request/InscriptionRequest.java` — `userId`
- `dto/response/InscriptionResponse.java`
- `exception/DuplicateInscriptionException.java` → 409

## Acceptance criteria
- [ ] `POST /api/formations/import` avec fichier `.xlsx` valide → formations créées, liste retournée
- [ ] Format incorrect (non-xlsx) → 400 "Format invalide, seuls les fichiers .xlsx sont acceptés"
- [ ] Erreur de colonne dans le fichier → 400 avec message indiquant la ligne/colonne problématique
- [ ] `POST /api/formations/{id}/inscriptions` → 201, stagiaire inscrit
- [ ] Inscription en doublon → 409 "Stagiaire déjà inscrit à cette formation"
- [ ] `GET /api/formations/{id}/inscriptions` → liste des inscrits avec `UserResponse`
- [ ] `DELETE /api/formations/{id}/inscriptions/{userId}` (SUPER_ADMIN) → 204
- [ ] Inscription sur formation archivée → 400

## Branch
`feature/formations`
- [ ] Create: `git checkout -b feature/formations`
- [x] Switch to existing: `git checkout feature/formations`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@WebMvcTest(FormationController.class)`): `POST /api/formations/import` avec fichier xlsx valide → 201
- [ ] Test 2 : `POST /api/formations/import` avec fichier pdf → 400
- [ ] Test 3 (`@ExtendWith(MockitoExtension)`): `ExcelImportUtil` parse correctement un fichier de test
- [ ] Test 4 : `createInscription` doublon → `DuplicateInscriptionException`
- [ ] Test 5 : inscription sur formation archivée → exception

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(formations): add Excel import and inscription management endpoints`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/formations` — see TICKET-025

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code

## Depends on
- TICKET-022 — `FormationService` et entités Formation/Inscription

## Estimated time
3h

## Status
[ ] To do   [ ] In progress   [ ] Done
