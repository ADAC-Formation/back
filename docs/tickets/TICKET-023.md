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
- `DELETE /api/formations/{id}/inscriptions/{stagiaireId}` — désinscrire — **`stagiaireId`, pas
  `userId`** : ce ticket disait `userId`, mais `docs/tech.md` (contrat partagé avec Manon) utilisait
  déjà `stagiaireId` pour le body du POST et le path du DELETE ; `stagiaireId` retenu comme source
  de vérité (même type d'écart que PUT/PATCH sur TICKET-022)

> **Colonnes Excel** : schéma placeholder, voir mémoire projet
> `ticket-023-excel-import-blocked` — Charlotte n'a pas encore le fichier réel du client. Décision
> (2026-09-06) : implémenter quand même avec des valeurs inventées (`categorie` = nom, `formateur`
> = email), documentées dans `docs/tech.md` § 4, à corriger dès réception du vrai fichier.

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `controller/FormationController.java` — ajouter `POST /formations/import`
- `controller/InscriptionController.java` — CRUD inscriptions
- `service/InscriptionService.java` (interface) + `InscriptionServiceImpl.java`
- `utils/ExcelImportUtil.java` — parser le fichier .xlsx avec Apache POI, retourner une liste de `CreateFormationRequest`
- `dto/request/InscriptionRequest.java` — `stagiaireId` (voir note ci-dessus)
- `dto/response/InscriptionResponse.java`
- `exception/DuplicateInscriptionException.java` → 409

## Acceptance criteria
- [x] `POST /api/formations/import` avec fichier `.xlsx` valide → formations créées, liste retournée
- [x] Format incorrect (non-xlsx) → 400 "Format invalide, seuls les fichiers .xlsx sont acceptés"
- [x] Erreur de colonne dans le fichier → 400 avec message indiquant la ligne/colonne problématique
- [x] `POST /api/formations/{id}/inscriptions` → 201, stagiaire inscrit
- [x] Inscription en doublon → 409 "Stagiaire déjà inscrit à cette formation"
- [x] `GET /api/formations/{id}/inscriptions` → liste des inscrits avec `UserResponse`
- [x] `DELETE /api/formations/{id}/inscriptions/{stagiaireId}` (SUPER_ADMIN) → 204
- [x] Inscription sur formation archivée → 400

**Ajouté en review (hors AC initiales)** :
- `GET .../inscriptions` restreint à SUPER_ADMIN/ADMIN — un STAGIAIRE, même inscrit, ne le voit
  plus (`InscriptionResponse.stagiaire` expose l'email de chaque inscrit)
- `stagiaireId` validé comme STAGIAIRE actif (même garde-fou que `formateurId` sur TICKET-022)
- Import Excel : garde-fous taille fichier (2 Mo) et nombre de lignes (1000), ratio anti zip-bomb
  POI, erreurs plafonnées (50) et valeurs échoées tronquées, réutilisation de la Bean Validation de
  `CreateFormationRequest` (une plage de dates inversée ne finit plus en 500), lookup formateur
  insensible à la casse
- N+1 corrigé sur `GET .../inscriptions` (`@EntityGraph`), `inscriptionsCount` calculé au lieu
  d'être toujours à 0
- `MaxUploadSizeExceededException`/partie multipart manquante → 413/400 au lieu du body par défaut
  de Spring
- Verrou optimiste (`Formation.version`/`User.version`, TICKET-022/019) sans handler jusqu'ici →
  `ObjectOptimisticLockingFailureException` retournait un 500 non loggé au lieu du 409 documenté ;
  handler ajouté dans `GlobalExceptionHandler`
- `formateurId` inconnu renvoyait 404 (`ResourceNotFoundException`) alors que `docs/tech.md`
  documente tous les cas `formateurId` en 400 — uniformisé en `InvalidFormationDataException`

**Suivi identifié en review, non bloquant (pas corrigé dans ce ticket)** :
- `createFormation` exécute un `countByFormation` inutile (toujours 0 sur une formation qui vient
  d'être créée) — amplifié ×N par l'import Excel
- `GET /api/formations` reste un 3N+1 (`countByFormation` + `formateur`/`category` LAZY par ligne)
  — `GET .../inscriptions` a eu le traitement `@EntityGraph`, pas cette liste
- Une catégorie désactivée reste acceptable à la création d'une formation (JSON et Excel) — seul le
  sélecteur front la masque, rien ne l'interdit côté serveur
- Import Excel : pas de garde pour un `.xlsx` valide à 0 feuille (500 au lieu de 400) ; le 413
  (fichier > 20 Mo, config globale) n'est pas documenté dans `tech.md`/Swagger à côté du 400
  (> 2 Mo, cap local)
- `InscriptionServiceImpl.findFormationOrThrow` duplique la logique de
  `FormationServiceImpl.findFormationOrThrow` au lieu de la réutiliser

## Branch
`feature/formations`
- [ ] Create: `git checkout -b feature/formations`
- [x] Switch to existing: `git checkout feature/formations`

## Write tests first (TDD)
Before writing any implementation code:
- [x] Test 1 (`@WebMvcTest(FormationController.class)`): `POST /api/formations/import` avec fichier xlsx valide → 201
- [x] Test 2 : `POST /api/formations/import` avec fichier pdf → 400
- [x] Test 3 (`@ExtendWith(MockitoExtension)`): `ExcelImportUtil` parse correctement un fichier de test
- [x] Test 4 : `createInscription` doublon → `DuplicateInscriptionException`
- [x] Test 5 : inscription sur formation archivée → exception

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

Au-delà des 5 tests minimum : couverture complète par rôle des endpoints inscriptions, validation
`stagiaireId`, et robustesse de l'import (ligne vide, cellule date native, fichier corrompu, plage
de dates inversée, taille limite) — voir `InscriptionControllerTest`, `InscriptionServiceImplTest`,
`ExcelImportUtilTest`, `InscriptionRepositoryTest`, `UserRepositoryTest`.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(formations): add Excel import and inscription management endpoints`

## PR (only on last ticket of this branch)
- [x] This IS the last ticket on `feature/formations` — this ticket's own "see TICKET-025" note was
      stale: TICKET-025 is `Repo = front` (ignored by the backend agent), and `feature/formations`
      has only two backend tickets (022, 023) per `.claude/skills/backend-agent/SKILL.md`'s ordered
      list. PR opened against `dev` after this commit.

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code

## Depends on
- TICKET-022 — `FormationService` et entités Formation/Inscription

## Estimated time
3h

## Status
[ ] To do   [ ] In progress   [x] Done
