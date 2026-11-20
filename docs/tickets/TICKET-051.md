# TICKET-051 — Backend — POST /users/import (comptes par fichier Excel)

## Story

[US-007/US-008 — extension, NOTES-09 du front] La chargée de formation (SUPER_ADMIN) veut créer
plusieurs comptes, formateurs et stagiaires, d'un coup à partir d'un fichier Excel, au lieu de les
saisir un par un.

## Description

Ajouter `POST /api/users/import` (multipart `file`, SUPER_ADMIN uniquement). Décisions de Manon
(2026-11-20) :

- **Un seul fichier**, colonne `role` (`Formateur` | `Stagiaire`, casse ignorée) ; colonnes A `nom`,
  B `prenom`, C `email`, D `role` ; en-tête ignoré.
- **Ligne par ligne** : les lignes valides créent un compte en attente d'activation (même chemin que
  `createFormateur` / `createStagiaire` : mot de passe aléatoire, `isActive = false`, email
  d'activation après commit) ; les lignes invalides sont renvoyées dans `errors`
  (`{ ligne, colonne, message }`) et ne créent rien.
- **Doublons** : un email déjà utilisé par un compte, ou répété dans le fichier (casse ignorée), est
  une erreur de ligne. Les comptes existants ne sont jamais modifiés.
- **Pas d'inscription** à une formation (NOTES-10, en attente de l'ADAC).
- **Fichier refusé en entier** (400) : pas un `.xlsx` lisible, plus de 2 Mo, plus de 200 lignes
  (plafond plus bas que les 1000 de l'import des formations : un email par compte), ou aucun compte.

La lecture commune des classeurs (contrôle du fichier, ouverture protégée, fermeture, cellules en
texte) est sortie de `ExcelImportUtil` dans `ExcelWorkbooks`, sans changer le comportement de
l'import des formations.

Contrat API (`docs/tech.md`) : `POST /api/users/import`.

## Repo

[ ] front/ [x] back [ ] both

## Files to create or modify

- `utils/ExcelWorkbooks.java` (nouveau), `utils/AccountImportParser.java` (nouveau), `utils/ExcelImportUtil.java`
- `dto/response/AccountImportResponse.java`, `dto/response/ImportErrorResponse.java` (nouveaux)
- `service/UserService.java`, `service/UserServiceImpl.java`, `controller/UserController.java`
- Tests : `AccountImportParserTest` (nouveau), `UserServiceImplTest`, `UserControllerTest`

## Acceptance criteria

- [x] SUPER_ADMIN : 200 avec `created` (UserResponse) et `errors` (ligne, colonne, message)
- [x] ADMIN et STAGIAIRE : 403, le service n'est pas appelé
- [x] Ligne valide → compte inactif du rôle demandé + email d'activation
- [x] Champ manquant, email invalide, rôle inconnu : une erreur par cellule, la ligne ne crée rien
- [x] Email déjà utilisé / répété dans le fichier : erreur de ligne, la première occurrence est gardée
- [x] Lignes vides ignorées
- [x] Pas un `.xlsx`, fichier illisible, plus de 200 lignes, aucun compte : 400
- [x] Import des formations inchangé (`ExcelImportUtilTest` vert)

## Tests

- `AccountImportParserTest` (9) : rôles, erreurs par cellule, email déjà utilisé, email répété,
  lignes vides, fichier non `.xlsx`, illisible, 201 lignes, aucun compte
- `UserServiceImplTest#importAccountsCreatesEachValidRowAndReturnsTheRowErrors`
- `UserControllerTest` : 200 (forme JSON), 400 (fichier refusé), 403 formateur, 403 stagiaire
- Suite complète : `mvn test` → 579 tests, 0 échec

## Revue

Le workflow `review-code` n'est pas installé dans l'environnement de cette session : relecture
manuelle du diff (Java, sécurité, contrat, tests). Aucun BLOCKING ni CRITICAL.

- SUGGESTION (non appliquée) : les emails d'activation partent l'un après l'autre après le commit,
  dans la requête. Un fichier de 200 lignes la tient le temps de 200 envois SMTP. Un échec d'envoi
  est déjà absorbé (`MailException` journalisée), mais la durée peut dépasser le délai du proxy.
  À trancher avec Charlotte : envoi asynchrone (`@Async`) ou plafond plus bas.
- Note : deux imports concurrents du même email passent tous deux la vérification du parseur ;
  la contrainte d'unicité de la base fait alors échouer la transaction entière (cas très rare,
  un seul utilisateur SUPER_ADMIN).
