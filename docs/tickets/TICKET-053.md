# TICKET-053 — Backend — Inscriptions : stagiaire jamais activé, désinscription avec documents

## Story

[US-006 — extension, NOTES-10 du front] La chargée de formation (SUPER_ADMIN) inscrit un stagiaire à
ses formations dès la création de son compte, et peut le désinscrire même s'il a déjà des documents.

## Description

Décisions de Manon (2026-11-23, `front/docs/decisions/decisions-inscriptions.md`, J2, J6, J7) :

- **J2** — `POST /api/formations/{id}/inscriptions` accepte un stagiaire **jamais activé** (compte
  créé, `isActive = false`, aucune activation passée : `ActivationService.hasEverActivated` faux).
  Un stagiaire **suspendu** (inactif après avoir été activé) reste refusé : 404 « Stagiaire
  introuvable », comme aujourd'hui. Sans ce changement, un compte qui vient d'être créé ne peut
  jamais être inscrit.
- **J6** — `DELETE /api/formations/{id}/inscriptions/{stagiaireId}` supprime d'abord les documents
  de l'inscription, puis l'inscription, dans la même transaction ; les fichiers du stockage sont
  supprimés ensuite, comme `DocumentServiceImpl.deleteDocument` (un échec du stockage est journalisé
  et ne fait pas échouer la désinscription). Aujourd'hui, la clé étrangère `documents.inscription_id`
  fait échouer la suppression (500 « Erreur interne »).
- **J7** — Sur une formation archivée, la désinscription répond 409 « Formation archivée » (même
  règle que la suppression d'un document) ; l'inscription y est déjà refusée (400).

- **J10** — `POST /api/users/stagiaires` avec `formationIds` (création et inscriptions dans la même
  transaction) refuse une formation archivée : 400 « Formation archivée, inscription impossible »,
  rien n'est créé. Le contrôle manquait ; le front envoie désormais les formations choisies à la
  création par ce champ.

Inchangé : rôle SUPER_ADMIN, idempotence de la désinscription, réponses du `POST`. Pas de migration.

Contrat API (`docs/tech.md`) : section Inscriptions.

## Repo

[ ] front/ [x] back [ ] both

## Files to create or modify

- `service/InscriptionServiceImpl.java`, `controller/InscriptionController.java` (documentation Swagger)
- `service/UserServiceImpl.java` (`enroll` : formation archivée refusée, J10)
- `repository/InscriptionRepository.java` : `findByStagiaire_IdAndFormation_Id` remplace
  `deleteByStagiaire_IdAndFormation_Id` (plus utilisé : la désinscription passe par l'inscription
  pour retrouver ses documents) ; `DocumentRepository.findAllByInscription` existait déjà
- Tests : `InscriptionServiceImplTest`, `InscriptionControllerTest`,
  `InscriptionDeletionIntegrationTest` (nouveau, base réelle)

## Acceptance criteria

- [x] Stagiaire jamais activé : 201
- [x] Stagiaire suspendu : 404
- [x] Désinscription d'un stagiaire avec documents : 204, documents et fichiers supprimés
- [x] Échec de suppression d'un fichier dans le stockage : 204 quand même, erreur journalisée
- [x] Formation archivée : 409 à la désinscription
- [x] Désinscription d'un stagiaire non inscrit : 204 (idempotent)
- [x] Création d'un stagiaire avec une formation archivée dans `formationIds` : 400, rien n'est créé

## Mise en œuvre

- « Suspendu » = `!isActive && ActivationService.hasEverActivated`, la même définition que le champ
  `activated` de `UserResponse` : un compte jamais activé (même désactivé par la chargée avant son
  activation) est inscriptible, comme le front l'affiche « Jamais activé ».
- Désinscription : documents puis inscription supprimés, puis `flush()` avant de toucher au stockage :
  si la base refuse, aucun fichier n'a été supprimé. Les chemins vides (lignes antérieures à V5,
  remplies avec `''`) ne sont jamais envoyés au stockage.

## Tests

- RED constaté : `InscriptionDeletionIntegrationTest` échouait sur la clé étrangère
  `documents_inscription_id_fkey` (le 500 de la désinscription d'un stagiaire avec document) ;
  compte jamais activé → « Stagiaire introuvable » ; suppressions attendues non appelées.
- `InscriptionServiceImplTest` : jamais activé inscrit, suspendu refusé (le test « désactivé »
  existant précise désormais qu'il a déjà été activé), documents puis inscription puis fichiers,
  échec du stockage absorbé, chemin vide ignoré, idempotence, formation archivée 409, formation
  inconnue 404
- `InscriptionControllerTest#deleteInscriptionOnArchivedFormationReturnsConflict` (contrat : vert
  d'emblée, le gestionnaire de `ConflictException` existait)
- `InscriptionDeletionIntegrationTest` : désinscription sur une vraie base avec un document
- `UserServiceImplTest#createStagiaireInAnArchivedFormationThrowsAndEnrollsNothing` (RED constaté :
  aucune exception)
- Suite complète : `mvn clean test` → 597 tests, 0 échec (PostgreSQL 16 jetable, `JWT_SECRET` aléatoire)

## Revue

Le workflow `review-code` et les skills `java-springboot` / `spring-boot-test-patterns` ne sont pas
installés dans l'environnement de cette session : relecture manuelle du diff (Java, sécurité, contrat,
tests, documentation). Aucun BLOCKING ni CRITICAL.

- Appliqué : chemins de stockage vides ignorés (voir Mise en œuvre).
- SUGGESTION (non appliquée, hors ticket) : `DocumentServiceImpl.deleteDocument` envoie lui aussi
  un chemin vide au stockage pour une ligne antérieure à V5.
- Risque métier à connaître : la désinscription supprime définitivement des documents qui peuvent
  servir de preuves Qualiopi (décision J6 de Manon). Un fichier qui ne se supprime pas du stockage
  reste orphelin (journalisé).
