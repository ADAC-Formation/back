# Modèle de base de données — Portail de Formation ADAC

Fichier diagramme : `DB_MODEL.mmd` (ouvrir avec Mermaid Preview dans VS Code)
Forme normale : **3NF** (Troisième Forme Normale)
Base de données : PostgreSQL 16

---

## Tables

### users
Tous les utilisateurs de la plateforme, quel que soit leur rôle.
- PK : `id` (BIGSERIAL)
- `role` : enum stocké en VARCHAR — `SUPER_ADMIN`, `ADMIN`, `STAGIAIRE`
- `is_active` : false = compte suspendu/désactivé (conservé pour l'historique)
- `email_notifications_enabled` : toggle du profil utilisateur
- `password_hash` : BCrypt — jamais le mot de passe en clair
- `version` : verrou optimiste JPA (`@Version`) — sans ça, un `PATCH /api/users/me` concurrent à
  une suspension/réactivation/reset MDP peut réécrire silencieusement l'ancienne valeur de
  `is_active`/`password_hash` (TICKET-019, review croisée)

### categories
Catégorie de classement d'une formation (ex : "Estime de soi en travail social").
- PK : `id` (BIGSERIAL)
- `nom` : UNIQUE, NOT NULL
- `couleur` : NOT NULL, format `#RRGGBB` — choisie par le Super Admin à la création
- `is_active` : false = catégorie désactivée, masquée du sélecteur de création de formation, mais
  conservée telle quelle sur les formations qui la référencent déjà (y compris archivées)
- **Pas de suppression** : uniquement activation/désactivation (pas d'endpoint DELETE) — une
  formation, même archivée, doit toujours pouvoir résoudre sa catégorie

### formations
Une session de formation planifiée.
- PK : `id` (BIGSERIAL)
- FK `category_id → categories.id` : NOT NULL — chaque formation appartient à exactement une catégorie
- FK `formateur_id → users.id` : nullable — si NULL, le Super Admin est l'intervenant
- FK `created_by → users.id` : toujours le Super Admin
- `status` : `ACTIVE` (default) ou `ARCHIVED` (lecture seule)
- Contrainte CHECK : `date_fin >= date_debut`

### inscriptions
Table de jonction entre un stagiaire et une formation. Porte la date d'inscription.
- PK : `id` (BIGSERIAL)
- FK `stagiaire_id → users.id` + FK `formation_id → formations.id`
- Contrainte UNIQUE sur (stagiaire_id, formation_id) — un stagiaire ne peut être inscrit qu'une fois

### documents
Fichiers uploadés sur Supabase Storage — métadonnées stockées en base.
- PK : `id` (BIGSERIAL)
- FK `formation_id` nullable : document visible par tous les inscrits à la formation
- FK `inscription_id` nullable : document ciblé pour un stagiaire spécifique
- **Contrainte CHECK : exactement un des deux FK est non-null**
- `uploaded_by` : l'auteur de l'upload (admin, formateur, ou stagiaire pour ses propres docs)

### messages
Un message envoyé par un expéditeur à un ou plusieurs destinataires.
- PK : `id` (BIGSERIAL)
- `is_group` : true = message broadcast (filtré par formation ou sélection libre)
- Les destinataires sont dans `message_recipients`

### message_recipients
Table de jonction Message ↔ Destinataire, porte l'horodatage de lecture.
- PK : `id` (BIGSERIAL)
- FK `message_id → messages.id` + FK `recipient_id → users.id`
- `read_at` : NULL = non lu, valeur = lu le (date)
- Contrainte UNIQUE sur (message_id, recipient_id)

### notifications
Une notification générée pour un utilisateur suite à un événement.
- PK : `id` (BIGSERIAL)
- `type` : `NEW_MESSAGE`, `DOCUMENT_UPLOADED`, `FORMATION_UPDATED`
- `entity_type` + `entity_id` : référence polymorphe pour la navigation (ex : clic → formation 5) —
  `entity_type` est un enum (`FORMATION`, `MESSAGE`), pas du texte libre
- `is_read` : marquer comme lu (cloche + page)
- `deleted_from_bell` : true = ne plus apparaître dans la cloche (reste dans l'historique)

### activation_tokens
Tokens d'activation de compte et de réinitialisation de mot de passe.
- PK : `id` (BIGSERIAL)
- `code_hash` : hash du code à 6 chiffres généré aléatoirement — jamais stocké en clair (un code à 6
  chiffres n'a que ~20 bits d'entropie ; le stocker en clair rendrait toute lecture de la table directement
  exploitable pour un takeover de compte)
- `attempts` : compteur de tentatives de vérification échouées, pour invalider le token après quelques essais
  (complète le rate limit à la création, voir ci-dessous — celui-ci ne borne pas le nombre de tentatives sur
  un token déjà émis)
- `type` : `ACCOUNT_ACTIVATION` ou `PASSWORD_RESET`
- `expires_at` : NOW() + 30 min à la création
- `used_at` : NULL = token encore valide ; valeur = déjà utilisé (invalide)
- Rate limit : max 3 tokens actifs par user sur 15 min (vérifié dans AuthServiceImpl)

---

## Relations

| Table A | Relation | Table B | Description |
|---|---|---|---|
| categories | 1:N | formations | Une catégorie classe plusieurs formations |
| users | 1:N | formations | Un formateur est assigné à plusieurs formations |
| users | 1:N | formations | Un Super Admin crée plusieurs formations |
| users | 1:N | inscriptions | Un stagiaire a plusieurs inscriptions |
| formations | 1:N | inscriptions | Une formation a plusieurs stagiaires inscrits |
| formations | 1:N | documents | Une formation a plusieurs documents |
| inscriptions | 1:N | documents | Une inscription a des documents ciblés |
| users | 1:N | documents | Un utilisateur uploade plusieurs documents |
| users | 1:N | messages | Un utilisateur envoie plusieurs messages |
| messages | 1:N | message_recipients | Un message a plusieurs destinataires |
| users | 1:N | message_recipients | Un utilisateur reçoit plusieurs messages |
| users | 1:N | notifications | Un utilisateur reçoit plusieurs notifications |
| users | 1:N | activation_tokens | Un utilisateur a plusieurs tokens (activation + reset) |

---

## Décisions 3NF

- **Rôles en VARCHAR** : seulement 3 valeurs fixes sans attributs propres → pas de table `roles` séparée (serait sur-ingénierie pour ce cas)
- **Modalité et status en VARCHAR** : idem — valeurs fixes, pas d'attributs associés
- **Pas de table `address`** : les formations ont un lieu implicite (présentiel/visio/mixte) mais pas d'adresse structurée en V1
- **`message_recipients` comme table séparée** : évite la répétition et permet de stocker `read_at` par destinataire — indispensable pour la messagerie groupée
- **Pas de table `conversations`** : chaque fil est strictement une paire (utilisateur courant, autre participant) —
  confirmé par les stories (messagerie individuelle 1:1, messagerie groupée = diffusion sans fil partagé). Un
  fil est calculé à la volée en filtrant `messages`/`message_recipients` sur cette paire, pas besoin de le
  persister. `conversationId` côté API = l'`id` de l'autre participant (voir `tech.md`).
- **Deux FK nullables dans `documents`** : pattern "polymorphic association" — `formation_id` XOR `inscription_id` non-null, garanti par une contrainte CHECK en SQL
- **`deleted_from_bell` dans `notifications`** : évite une table de jonction — simple flag pour distinguer la vue cloche (non lues, supprimables) de l'historique (tout conservé)
- **`categories.is_active` plutôt que suppression** : même pattern que `users.is_active` — une catégorie
  utilisée par une formation archivée ne doit jamais devenir orpheline ; la désactivation la retire du
  sélecteur de création sans casser l'historique. Pas de table de jonction : `category_id` est directement
  sur `formations` (relation 1:N simple, une formation n'a qu'une seule catégorie)

> **À trancher (review TICKET-003)** : aucune politique `ON DELETE` n'est définie sur les FK. Supprimer une
> `inscription` échouera tant qu'un `document` la référence ; supprimer un `message` échouera tant qu'un
> `message_recipients` le référence. `message_recipients` n'a pas de sens sans son message (candidat naturel
> à `ON DELETE CASCADE`) ; pour `documents.inscription_id`, la question est produit (cascade, ou
> désinscription = opération volontairement bloquée si des docs existent ?) — à statuer avant TICKET-023
> (désinscription) / TICKET-029-030 (suppression de message).

---

## Notes PostgreSQL

- Tous les timestamps : `TIMESTAMP WITH TIME ZONE` (timestamptz)
- IDs : `BIGSERIAL` (auto-increment)
- Index à créer sur toutes les colonnes FK
- Index supplémentaires recommandés :
  - `users(email)` — connexion
  - `activation_tokens(user_id, created_at)` — rate limit
  - `notifications(recipient_id, is_read)` — cloche
  - `message_recipients(recipient_id, read_at)` — boîte de réception

_Généré le 2026-08-21 avec /new-project_
