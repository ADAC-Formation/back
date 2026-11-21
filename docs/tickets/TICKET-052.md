# TICKET-052 — Backend — Catalogue des types de document (CRUD, SUPER_ADMIN)

## Story

[Extension de US-009 / US-010 / US-012, décidée avec la chargée de formation] La chargée de formation doit
pouvoir définir elle-même les pièces que les stagiaires fournissent (pièce d'identité, certificat médical…)
au lieu de les attendre d'un développeur. Aujourd'hui un `Document` n'a ni type ni libellé : le portail ne sait
pas quels documents sont attendus, donc ne peut pas dire « il manque la pièce d'identité ». Ce ticket pose le
**catalogue** ; les tickets suivants relieront les dépôts à un type et calculeront la checklist.

## Description

Ajouter la table `document_types` et son CRUD `/api/document-types`, réservé SUPER_ADMIN, sur le modèle des
catégories (liste, création, `PUT`, activation, désactivation, jamais de suppression).

Un type de document porte :

- `nom` (unique sans tenir compte de la casse, 255 au plus) et un texte d'aide optionnel (500 au plus) ;
- **sensible** (`isSensitive`) — il n'y a pas d'« obligatoire / facultatif » : tout type qui s'applique à une formation
  est une pièce à envoyer (utilisé par les tickets suivants :
  le formateur verra l'état mais pas les fichiers) ;
- sa **portée** : toutes les formations (`appliesToAllFormations`), ou une ou plusieurs catégories
  (`categoryIds`, au moins une) — jamais les deux ;
- un **délai** optionnel relatif aux dates de la formation : `deadlineDays` (0 à 365) avec `deadlineAnchor`
  (`BEFORE_START` ou `AFTER_END`), les deux ensemble ou aucun ;
- des **formats acceptés** optionnels (sous-ensemble de `pdf, jpg, jpeg, png, docx, xlsx`) ; vide = règle
  générale du dépôt de documents ;
- un état actif ou désactivé.

Règles retenues (décisions de Manon, 2026-11-20, `decisions-documents.md` G19 à G32 du repo front) :

- le catalogue s'applique **en direct** aux formations : aucune copie par formation ;
- la **migration ne contient aucune donnée** : la liste réelle viendra de l'ADAC, la liste fictive de test n'existe
  que dans le script de développement du front ;
- les catégories **désactivées** restent liables (elles portent encore des formations) ;
- pas de verrou optimiste (`version`) : une seule chargée modifie le catalogue, comme pour les catégories ;
- `GET` est réservé à SUPER_ADMIN dans ce ticket : les autres rôles liront ce dont ils ont besoin par les
  endpoints de checklist des tickets suivants.

Contrat API (`docs/tech.md`, section « Types de document ») :

- `GET /api/document-types` (`?active=true`), `POST /api/document-types`, `PUT /api/document-types/{id}`,
  `PATCH /api/document-types/{id}/activate`, `PATCH /api/document-types/{id}/deactivate`

## Repo

[ ] front/ [x] back [ ] both

## Files to create or modify

- `db/migration/V6__add_document_types.sql` — tables `document_types` et `document_type_categories`, index unique
  sur `UPPER(nom)`, contraintes CHECK (délai : paire et bornes ; repère)
- `entity/DocumentType.java`, `entity/enums/DeadlineAnchor.java` — nouveaux
- `repository/DocumentTypeRepository.java` — nouveau
- `dto/request/DocumentTypeRequest.java`, `dto/response/DocumentTypeResponse.java` — nouveaux
- `exception/DocumentTypeAlreadyExistsException.java` + handler 409 dans `GlobalExceptionHandler.java`
- `mapper/DocumentTypeMapper.java` — nouveau (`unmappedTargetPolicy = ERROR`)
- `service/DocumentTypeService.java` + `DocumentTypeServiceImpl.java` — nouveaux
- `controller/DocumentTypeController.java` — nouveau
- `docs/tech.md`, `docs/DB_MODEL.md`, `docs/DB_MODEL.mmd` — contrat et modèle

## Acceptance criteria

- [x] `POST /api/document-types` en SUPER_ADMIN → 201 + `DocumentTypeResponse` ; ADMIN et STAGIAIRE → 403 ;
      sans session → 401 (test end-to-end dans `JwtAuthenticationIntegrationTest`)
- [x] nom déjà utilisé, casse ignorée → 409 (contrôle du service **et** index unique de la base, pour deux
      `POST` simultanés)
- [x] `PUT` : même nom autorisé pour le type lui-même, 409 s'il appartient à un autre ; remplace tous les champs
      sans toucher à `isActive` ; 404 si le type est inconnu
- [x] portée : « toutes » avec des catégories → 400 ; catégories sans aucune catégorie → 400 ; catégorie inconnue → 404
- [x] délai : jours sans repère ou repère sans jours → 400 ; jours hors de 0 à 365 → 400 (service, bean
      validation **et** contraintes CHECK de la base)
- [x] formats : valeurs hors de `pdf, jpg, jpeg, png, docx, xlsx` → 400 ; normalisés en minuscules sans doublon ;
      liste vide stockée comme absente (règle générale)
- [x] `GET` (SUPER_ADMIN) → types triés par nom sans casse ni accents ; `?active=true` écarte les désactivés ;
      catégories de chaque type triées par nom
- [x] `PATCH …/activate` et `…/deactivate` : idempotents, 404 si le type est inconnu, 403 pour ADMIN
- [x] aucun endpoint `DELETE` (405)
- [x] la réponse remplit tous les champs (dont `appliesToAllFormations`, `isSensitive`, `isActive`)
- [x] la migration n'insère aucune ligne

## Branch

`feature/document-types`

- [x] Create: `git checkout -b feature/document-types origin/dev` (depuis `origin/dev` à jour)
- [ ] Switch to existing: `git checkout feature/document-types`

## Write tests first (TDD)

Before writing any implementation code:

- [x] Test 1 (`@DataJpaTest`, `DocumentTypeRepositoryTest`) : enregistrement avec catégories relu ; unicité
      insensible à la casse ; contraintes de délai ; `existsByNom…` ; `findAllByActiveTrue`
- [x] Test 2 (`@ExtendWith(MockitoExtension)`, `DocumentTypeServiceImplTest`) : création, règles de portée, de
      délai et de formats, 409 (contrôle et course sur l'index), modification, activation, liste triée, catégories
      triées dans la réponse
- [x] Test 3 (`@WebMvcTest`, `DocumentTypeControllerTest`) : chaque endpoint, rôles, validation 400, 404, 409, noms
      de champs du contrat, absence de `DELETE`
- [x] Test 4 (`@SpringBootTest`, `DocumentTypeIntegrationTest`, base réelle et mapper réel) : réponse construite
      depuis l'entité enregistrée, doublon de casse, remplacement des catégories, liste active
- [x] Test 5 (`JwtAuthenticationIntegrationTest`) : `GET /api/document-types` sans cookie → 401 (passe dès
      l'ajout du contrôleur : la sécurité protège déjà tout `/api`, c'est un test de non-régression)
- [x] `FlywayMigrationTest` : version courante 6

## Notes de mise en œuvre

- Le test d'intégration (Test 4) a révélé deux défauts invisibles aux tests unitaires, corrigés dans le même ticket :
  `appliesToAllFormations` n'était jamais rempli (l'entité l'appelle `appliesToAll`, MapStruct n'avait aucune
  source) et le tri des catégories du mapper était ignoré (MapStruct construit la réponse par son builder Lombok,
  donc la méthode `@AfterMapping` sur la réponse ne s'exécute pas). Le mapper est maintenant en
  `unmappedTargetPolicy = ERROR` et le tri est fait par le service.
- Tests de l'ensemble du dépôt : les échecs de `CategoryRepositoryTest` (5 catégories au lieu de 6) et de
  `ActivationTokenRepositoryTest` dépendent des données de la base de développement locale ; ils ne viennent pas de
  ce ticket.

## Revue (`/code-review` niveau medium, `origin/dev..HEAD`)

9 constats, aucun `BLOCKING`. Corrigés (tests d'abord, RED confirmé) :

- `CRITICAL` — toute violation d'intégrité devenait un 409 « nom déjà pris » (clé étrangère d'une catégorie
  supprimée entre-temps, CHECK…) : seule la violation de `uk_document_types_nom_upper` donne 409, les autres sont
  relancées.
- `CRITICAL` — `"allowedFormats": ["pdf", null]` donnait un 500 (NPE) : `List<@NotNull String>` → 400.
- `SUGGESTION` — `categoryIds` (100 au plus) et `allowedFormats` (20 au plus) n'étaient pas bornés.
- `SUGGESTION` — `splitFormats` ne rognait pas les espaces et rendait une liste à taille fixe : `trim` + `toList()`.

Non appliqués, avec la raison :

- Catégories **désactivées** liables : décision voulue (une catégorie désactivée porte encore des formations,
  `design.md` point 6 du change front) ; documentée dans la description du ticket.
- Contrôle 0–365 répété (DTO, service, CHECK SQL) : le service le garde pour les appelants hors HTTP ; la base
  reste la dernière barrière. Le coût de la redondance est nul.
- Tri en mémoire recalculant la clé : un catalogue de quelques dizaines de types, sans effet mesurable.
- Pliage de casse Java (`upper()` Hibernate) et SQL (`UPPER`) pouvant différer sur certains accents : le résultat
  reste correct, l'index tranche et donne le même 409.
- `activate`/`deactivate` sauvegardent à chaque appel : même comportement que les catégories (idempotent).
