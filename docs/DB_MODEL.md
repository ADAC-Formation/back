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

### formations
Une session de formation planifiée.
- PK : `id` (BIGSERIAL)
- FK `formateur_id → users.id` : nullable — si NULL, le Super Admin est l'intervenant
- FK `created_by → users.id` : toujours le Super Admin
- `status` : `ACTIVE` (default) ou `ARCHIVED` (lecture seule)

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
- `entity_type` + `entity_id` : référence polymorphe pour la navigation (ex : clic → formation 5)
- `is_read` : marquer comme lu (cloche + page)
- `deleted_from_bell` : true = ne plus apparaître dans la cloche (reste dans l'historique)

### activation_tokens
Tokens d'activation de compte et de réinitialisation de mot de passe.
- PK : `id` (BIGSERIAL)
- `code` : 6 chiffres générés aléatoirement
- `type` : `ACCOUNT_ACTIVATION` ou `PASSWORD_RESET`
- `expires_at` : NOW() + 30 min à la création
- `used_at` : NULL = token encore valide ; valeur = déjà utilisé (invalide)
- Rate limit : max 3 tokens actifs par user sur 15 min (vérifié dans AuthServiceImpl)

---

## Relations

| Table A | Relation | Table B | Description |
|---|---|---|---|
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
