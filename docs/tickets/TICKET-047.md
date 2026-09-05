# TICKET-047 — Backend — CRUD Catégories (create, edit, list, activate/deactivate)

## Story
[US-017] — Gérer les catégories de formation

## Description
Endpoints de gestion des catégories, SUPER_ADMIN uniquement. Pas de suppression — seulement
activation/désactivation. `PUT` permet de corriger nom/couleur sur une catégorie existante sans
impacter les formations qui la référencent (simple FK, pas de duplication).

Contrat API (`docs/tech.md`) :
- `GET /api/categories` — liste (`?active=true`)
- `POST /api/categories` — créer
- `PUT /api/categories/{id}` — modifier nom/couleur
- `PATCH /api/categories/{id}/activate`
- `PATCH /api/categories/{id}/deactivate`

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `controller/CategoryController.java` — tous les endpoints, autorisations, Swagger
- `service/CategoryService.java` (interface) + `CategoryServiceImpl.java` — logique CRUD, validation
  unicité du nom, activation/désactivation idempotente
- `dto/request/CreateCategoryRequest.java` — `nom` (NotBlank), `couleur` (`@Pattern` `^#[0-9A-Fa-f]{6}$`)
- `dto/request/UpdateCategoryRequest.java` — mêmes champs
- `dto/response/CategoryResponse.java` — `id`, `nom`, `couleur`, `isActive`, `createdAt`
- `mapper/CategoryMapper.java`
- `dto/response/FormationResponse.java` + `mapper/FormationMapper.java` — inclure `category` (dépend
  de TICKET-022 pour l'intégration complète, mais le mapper de base peut être posé ici)

## Acceptance criteria
- [x] `POST /api/categories` (SUPER_ADMIN) → 201 + `CategoryResponse`, `isActive = true` par défaut
- [x] `POST /api/categories` avec un nom déjà utilisé (insensible à la casse) → 409
- [x] `POST /api/categories` par ADMIN ou STAGIAIRE → 403
- [x] `POST /api/categories` avec `couleur` qui ne matche pas `^#[0-9A-Fa-f]{6}$` → 400
- [x] `GET /api/categories` → toutes les catégories, actives et désactivées
- [x] `GET /api/categories?active=true` → uniquement les actives
- [x] `PUT /api/categories/{id}` → nom et/ou couleur mis à jour, `isActive` inchangé
- [x] `PUT /api/categories/{id}` avec un nom déjà pris par une **autre** catégorie → 409
- [x] `PATCH /api/categories/{id}/deactivate` → `isActive = false` ; les formations existantes
      référençant cette catégorie ne sont pas modifiées
- [x] `PATCH /api/categories/{id}/activate` sur une catégorie déjà active → 200, no-op (idempotent)
- [x] Aucun endpoint `DELETE /api/categories/{id}` n'existe
- [x] Tous les endpoints documentés Swagger

## Branch
`feature/categories`
- [ ] Create: `git checkout -b feature/categories` (déjà créée par TICKET-046)
- [x] Switch to existing: `git checkout feature/categories`

## Write tests first (TDD)
Before writing any implementation code:
- [x] Test 1 (`@WebMvcTest(CategoryController.class)`) : `POST /api/categories` SUPER_ADMIN, body
      valide → 201
- [x] Test 2 : `POST /api/categories` par ADMIN → 403
- [x] Test 3 (`@ExtendWith(MockitoExtension)`, `CategoryServiceImplTest`) : `createCategory` avec un
      nom déjà pris (`existsByNomIgnoreCase` = true) → `CategoryAlreadyExistsException`
- [x] Test 4 (`@WebMvcTest`) : `POST /api/categories` avec `couleur = "rouge"` → 400
- [x] Test 5 (Mockito) : `deactivateCategory` puis `activateCategory` → `isActive` repasse à `true`,
      sans toucher aux formations (pas d'appel à `FormationRepository`)
- [x] Test 6 (`@WebMvcTest`) : `GET /api/categories?active=true` → ne retourne que les catégories actives

Plus, hors liste minimale du ticket : 403 STAGIAIRE, 403 sur PUT/activate/deactivate, 404 sur PUT
et activate avec id inconnu, blank `nom` → 400, validation PUT, `DELETE` absent, contrat JSON
`isActive`, race TOCTOU sur l'unicité (create + rename) — voir la review branch-wide (21 tests
contrôleur + 14 tests service + 2 repository + 1 contrat JSON, tous verts).

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(categories): add CRUD endpoints with activate/deactivate; update docs`

## PR (only on last ticket of this branch)
- [x] This IS the last backend ticket on `feature/categories` — open PR once GREEN (front tickets
      TICKET-048/049 live in the separate front repo)

## Skills to invoke
- [x] /java-springboot
- [x] /spring-boot-test-patterns
- [x] /jpa-patterns

## Depends on
- TICKET-046 — `Category` entity, migration, `CategoryRepository`
- TICKET-005 — conventions DTO/mapper (MapStruct) établies

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [x] Done
