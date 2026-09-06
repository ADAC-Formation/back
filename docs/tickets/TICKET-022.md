# TICKET-022 — Backend — CRUD Formations + archive + formateur

## Story
[US-004] — Créer et gérer une formation

## Description
Créer les endpoints de gestion des formations : CRUD complet pour le SUPER_ADMIN, lecture seule pour les ADMIN. Gestion de l'archivage (ACTIVE → ARCHIVED, irréversible), et auto-assignation du Super Admin si aucun formateur sélectionné.

Contrat API (`docs/tech.md`) :
- `POST /api/formations` — créer (`categoryId` obligatoire, voir TICKET-046/047)
- `GET /api/formations` — liste (avec filtres `status`, `categoryId`)
- `GET /api/formations/{id}` — détail
- `PUT /api/formations/{id}` — modifier (SUPER_ADMIN uniquement) — **PUT, pas PATCH** : ce ticket
  disait initialement PATCH, mais `docs/tech.md` et `docs/ARCHI.md` documentaient déjà PUT (même
  convention que `PUT /api/categories/{id}`) ; PUT retenu comme source de vérité, ce fichier
  corrigé en conséquence (implémentation)
- `PATCH /api/formations/{id}/archive` — archiver

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `controller/FormationController.java` — tous les endpoints avec autorisations et Swagger
- `service/FormationService.java` (interface) + `FormationServiceImpl.java` — logique CRUD, auto-assignation, validation archivage
- `dto/request/CreateFormationRequest.java` — `title`, `description` (nullable), `startDate`, `endDate`, `modalite`, `categoryId` (obligatoire), `formateurId` (nullable)
- `dto/request/UpdateFormationRequest.java` — mêmes champs, tous optionnels
- `dto/response/FormationResponse.java` — avec `status`, `category`, `formateur`, `inscriptionsCount`
- `mapper/FormationMapper.java`

## Acceptance criteria
- [x] `POST /api/formations` (SUPER_ADMIN) → 201 + `FormationResponse`
- [x] `POST /api/formations` sans `categoryId`, ou avec un `categoryId` introuvable → 400
- [x] Si `formateurId` absent → Super Admin courant auto-assigné comme formateur
- [x] `POST /api/formations` par ADMIN ou STAGIAIRE → 403
- [x] `GET /api/formations` : SUPER_ADMIN voit tout ; ADMIN voit **uniquement ses formations** (tous
      statuts) ; STAGIAIRE voit ses inscriptions — review : le détail par id (`GET /{id}`) applique
      le même périmètre pour ADMIN (404 si ce n'est pas sa formation), pas seulement la liste
- [x] `GET /api/formations?status=ACTIVE` → ne retourne que les actives
- [x] `GET /api/formations?categoryId=1` → ne retourne que les formations de cette catégorie (accessible SUPER_ADMIN et ADMIN)
- [x] Une catégorie désactivée après coup reste affichée telle quelle sur les formations qui la référencent déjà
- [x] `PATCH /api/formations/{id}/archive` → status passe à `ARCHIVED`, formation en lecture seule
- [x] `PUT /api/formations/{id}` sur une formation archivée → 400 "Formation archivée, modification impossible"
- [x] Tous les endpoints documentés Swagger

**Ajouté en review (hors AC initiales)** :
- `formateurId` (création et modification) validé comme formateur actif (`ADMIN`/`SUPER_ADMIN`) —
  un id de `STAGIAIRE` ou de compte désactivé est rejeté en 400
- Verrou optimiste (`Formation.version`, migration V4) — une modification concurrente ne peut plus
  silencieusement annuler un archivage
- `?mine`/`?formateurId` retirés du contrat `GET /api/formations` (documentés dans `tech.md` mais
  jamais implémentés) ; `PATCH /api/formations/{id}/formateur` documenté comme non implémenté par
  ce ticket (le `PUT` couvre déjà `formateurId`)

## Branch
`feature/formations`
- [x] Create: `git checkout -b feature/formations`
- [ ] Switch to existing: `git checkout feature/formations`

## Write tests first (TDD)
Before writing any implementation code:
- [x] Test 1 (`@WebMvcTest(FormationController.class)`): `POST /api/formations` SUPER_ADMIN, sans formateurId → 201, formateurId = SA
- [x] Test 2 : `POST /api/formations` par ADMIN → 403
- [x] Test 3 (`@ExtendWith(MockitoExtension)`): `archiveFormation` → status = ARCHIVED
- [x] Test 4 : `updateFormation` sur une formation ARCHIVED → `FormationArchivedException`
- [x] Test 5 (`@DataJpaTest`): `findAllByStatus(ACTIVE)` ne retourne pas les archivées

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

Au-delà des 5 tests minimum ci-dessus : couverture complète par rôle du GET liste/détail, de la
validation `formateurId`, et du verrou optimiste — voir `FormationControllerTest`,
`FormationServiceImplTest`, `FormationRepositoryTest`, `InscriptionRepositoryTest`.

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
- TICKET-046, TICKET-047 — `Category` entity + `CategoryRepository` (FK `category_id` NOT NULL sur `formations`)

## Estimated time
3h

## Status
[ ] To do   [ ] In progress   [x] Done
