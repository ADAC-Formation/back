# TICKET-022 — Backend — CRUD Formations + archive + formateur

## Story
[US-004] — Créer et gérer une formation

## Description
Créer les endpoints de gestion des formations : CRUD complet pour le SUPER_ADMIN, lecture seule pour les ADMIN. Gestion de l'archivage (ACTIVE → ARCHIVED, irréversible), et auto-assignation du Super Admin si aucun formateur sélectionné.

Contrat API (`docs/tech.md`) :
- `POST /api/formations` — créer
- `GET /api/formations` — liste (avec filtre `status`)
- `GET /api/formations/{id}` — détail
- `PATCH /api/formations/{id}` — modifier (SUPER_ADMIN uniquement)
- `PATCH /api/formations/{id}/archive` — archiver

## Repo
[ ] front/   [x] back/   [ ] both

## Files to create or modify
- `controller/FormationController.java` — tous les endpoints avec autorisations et Swagger
- `service/FormationService.java` (interface) + `FormationServiceImpl.java` — logique CRUD, auto-assignation, validation archivage
- `dto/request/CreateFormationRequest.java` — `title`, `description` (nullable), `startDate`, `endDate`, `modalite`, `formateurId` (nullable)
- `dto/request/UpdateFormationRequest.java` — mêmes champs, tous optionnels
- `dto/response/FormationResponse.java` — avec `status`, `formateur`, `inscriptionsCount`
- `mapper/FormationMapper.java`

## Acceptance criteria
- [ ] `POST /api/formations` (SUPER_ADMIN) → 201 + `FormationResponse`
- [ ] Si `formateurId` absent → Super Admin courant auto-assigné comme formateur
- [ ] `POST /api/formations` par ADMIN ou STAGIAIRE → 403
- [ ] `GET /api/formations` : SUPER_ADMIN voit tout ; ADMIN voit toutes formations (filtre par défaut : ses formations) ; STAGIAIRE voit ses inscriptions
- [ ] `GET /api/formations?status=ACTIVE` → ne retourne que les actives
- [ ] `PATCH /api/formations/{id}/archive` → status passe à `ARCHIVED`, formation en lecture seule
- [ ] `PATCH /api/formations/{id}` sur une formation archivée → 400 "Formation archivée, modification impossible"
- [ ] Tous les endpoints documentés Swagger

## Branch
`feature/formations`
- [x] Create: `git checkout -b feature/formations`
- [ ] Switch to existing: `git checkout feature/formations`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@WebMvcTest(FormationController.class)`): `POST /api/formations` SUPER_ADMIN, sans formateurId → 201, formateurId = SA
- [ ] Test 2 : `POST /api/formations` par ADMIN → 403
- [ ] Test 3 (`@ExtendWith(MockitoExtension)`): `archiveFormation` → status = ARCHIVED
- [ ] Test 4 : `updateFormation` sur une formation ARCHIVED → `FormationArchivedException`
- [ ] Test 5 (`@DataJpaTest`): `findAllByStatus(ACTIVE)` ne retourne pas les archivées

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(formations): add CRUD endpoints with archive and auto-assign formateur`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/formations` — see TICKET-025

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code
- [x] /jpa-patterns → génère les entités JPA, les repositories et les @Query custom

## Depends on
- TICKET-005 — `FormationRepository`, `FormationMapper`, DTOs
- TICKET-019 — `UserRepository` pour récupérer le Super Admin et les formateurs

## Estimated time
3h

## Status
[ ] To do   [ ] In progress   [ ] Done
