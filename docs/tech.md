# Contrat API — Portail de Formation ADAC

> Source de vérité entre le front (Manon) et le back (Charlotte).
> Toute modification ici doit être répercutée dans les deux repos.
> Swagger UI : `http://localhost:8080/swagger-ui.html`

---

## Configuration

| | Valeur |
|---|---|
| Backend base URL | `http://localhost:8080/api` |
| Frontend base URL | `http://localhost:5173` |
| Auth | Cookie HttpOnly `jwt` — **pas de header Authorization** |
| Content-Type | `application/json` (sauf upload : `multipart/form-data`) |
| Axios config | `withCredentials: true` sur toutes les requêtes |

---

## Format d'erreur standard

```json
{
  "status": 400,
  "message": "Description de l'erreur",
  "details": ["détail 1", "détail 2"]
}
```

## Codes HTTP

| Situation | Code |
|---|---|
| Lecture réussie | 200 |
| Création réussie | 201 |
| Succès sans contenu | 204 |
| Données invalides | 400 |
| Non authentifié | 401 |
| Non autorisé (rôle insuffisant) | 403 |
| Ressource introuvable | 404 |
| Erreur serveur | 500 |

---

## 1. Authentification

### POST /api/auth/login
Connexion — pose le cookie HttpOnly `jwt` sur succès.
```json
// Body
{ "email": "string", "password": "string" }

// 200 OK
{ "id": 1, "email": "string", "prenom": "string", "nom": "string",
  "role": "SUPER_ADMIN | ADMIN | STAGIAIRE", "isActive": true,
  "emailNotificationsEnabled": true }

// 401 — identifiants invalides
{ "status": 401, "message": "Identifiants invalides" }

// 403 — compte non activé
{ "status": 403, "message": "Compte non activé. Veuillez consulter vos emails." }
```

### POST /api/auth/logout
Déconnexion — expire le cookie.
```json
// 204 No Content
```

### POST /api/auth/activate
Activation du compte (premier accès après création par l'admin).
```json
// Body
{ "email": "string", "code": "string", "newPassword": "string" }

// 200 OK
{ "message": "Compte activé avec succès" }

// 400 — code invalide ou expiré
{ "status": 400, "message": "Code invalide ou expiré" }

// 429 — trop de tentatives
{ "status": 429, "message": "Trop de demandes. Réessayez dans 15 minutes." }
```

### POST /api/auth/forgot-password
Demande de réinitialisation — envoie un email avec code.
```json
// Body
{ "email": "string" }

// 200 OK (même réponse si email connu ou inconnu — sécurité)
{ "message": "Si cet email existe, un code vous a été envoyé." }
```

### POST /api/auth/reset-password
Réinitialisation du mot de passe.
```json
// Body
{ "email": "string", "code": "string", "newPassword": "string" }

// 200 OK
{ "message": "Mot de passe mis à jour avec succès" }

// 400
{ "status": 400, "message": "Code invalide ou expiré" }
```

### GET /api/auth/me
Récupère l'utilisateur courant (vérifie si le cookie est valide).
```json
// 200 OK → UserResponse
// 401 → non authentifié
```

---

## 2. Utilisateurs

### GET /api/users/formateurs
Liste des formateurs.
- SUPER_ADMIN : tous (actifs + suspendus)
- ADMIN : actifs uniquement
```json
// Query params optionnels : ?active=true
// 200 OK → UserResponse[]
```

### POST /api/users/formateurs
Créer un formateur (SUPER_ADMIN uniquement). Déclenche l'email d'activation.
```json
// Body
{ "nom": "string", "prenom": "string", "email": "string" }

// 201 Created → UserResponse
// 409 — email déjà utilisé
{ "status": 409, "message": "Cet email est déjà utilisé" }
```

### GET /api/users/stagiaires
Liste des stagiaires.
- SUPER_ADMIN : tous (actifs + désactivés)
- ADMIN : actifs uniquement (filtre par défaut : ses formations)
```json
// Query params : ?active=true | ?formationId=1
// 200 OK → UserResponse[]
```

### POST /api/users/stagiaires
Créer un stagiaire (SUPER_ADMIN uniquement). Déclenche l'email d'activation.
```json
// Body
{ "nom": "string", "prenom": "string", "email": "string", "formationIds": [1, 2] }

// 201 Created → UserResponse
// 409 — email déjà utilisé
```

### GET /api/users/{id}
Profil d'un utilisateur.
```json
// 200 OK → UserResponse
// 404
```

### PATCH /api/users/me
Modifier son propre profil (tous les rôles).
```json
// Body (tous les champs optionnels)
{ "emailNotificationsEnabled": true }

// 200 OK → UserResponse
```

### PATCH /api/users/{id}/deactivate
Désactiver / suspendre un compte (SUPER_ADMIN uniquement).
```json
// 200 OK → UserResponse
```

### PATCH /api/users/{id}/reactivate
Réactiver un compte (SUPER_ADMIN uniquement).
```json
// 200 OK → UserResponse
```

---

## 3. Formations

### GET /api/formations
Liste des formations.
- SUPER_ADMIN : toutes (actives + archivées)
- ADMIN : toutes actives (filtre par défaut : ses formations)
- STAGIAIRE : uniquement ses formations
```json
// Query params : ?status=ACTIVE|ARCHIVED | ?formateurId=1 | ?mine=true
// 200 OK → FormationResponse[]
```

### POST /api/formations
Créer une formation (SUPER_ADMIN uniquement).
```json
// Body
{
  "intitule": "string",
  "description": "string",  // optionnel, nullable
  "dateDebut": "2026-03-10",
  "dateFin": "2026-03-12",
  "modalite": "VISIO | PRESENTIEL | MIXTE",
  "formateurId": 2          // nullable — si null : Super Admin auto-assigné
}

// 201 Created → FormationResponse
```

### POST /api/formations/import
Import Excel (SUPER_ADMIN uniquement).
```
// Content-Type: multipart/form-data
// Body: file (fichier .xlsx)
// 201 Created → FormationResponse[]
// 400 — format fichier invalide
```

### GET /api/formations/{id}
Détail d'une formation.
```json
// 200 OK → FormationResponse (avec liste des inscrits et documents)
// 403 — STAGIAIRE non inscrit
// 404
```

### PUT /api/formations/{id}
Modifier une formation (SUPER_ADMIN uniquement).
```json
// Body (tous les champs optionnels)
{
  "intitule": "string",
  "description": "string",
  "dateDebut": "2026-03-10",
  "dateFin": "2026-03-12",
  "modalite": "VISIO | PRESENTIEL | MIXTE",
  "formateurId": 2
}
// 200 OK → FormationResponse
```

### PATCH /api/formations/{id}/archive
Archiver une formation (SUPER_ADMIN uniquement).
```json
// 200 OK → FormationResponse (status: "ARCHIVED")
```

### PATCH /api/formations/{id}/formateur
Assigner un formateur (SUPER_ADMIN uniquement).
```json
// Body
{ "formateurId": 2 }   // null → Super Admin auto-assigné

// 200 OK → FormationResponse
```

---

## 4. Inscriptions

### GET /api/formations/{id}/inscriptions
Liste des stagiaires inscrits à une formation.
```json
// 200 OK → InscriptionResponse[]
```

### POST /api/formations/{id}/inscriptions
Inscrire un stagiaire (SUPER_ADMIN uniquement).
```json
// Body
{ "stagiaireId": 5 }

// 201 Created → InscriptionResponse
// 409 — déjà inscrit
```

### DELETE /api/formations/{id}/inscriptions/{stagiaireId}
Désinscrire un stagiaire (SUPER_ADMIN uniquement).
```json
// 204 No Content
```

---

## 5. Documents

### GET /api/documents
Liste des documents selon le contexte.
```json
// Query params : ?formationId=1 | ?inscriptionId=3
// 200 OK → DocumentResponse[]
```

### POST /api/documents
Uploader un document.
```
// Content-Type: multipart/form-data
// Body:
//   file: File
//   formationId: number (nullable)
//   inscriptionId: number (nullable)
//   — exactement un des deux doit être renseigné

// Rôles :
//   SUPER_ADMIN → formationId ou inscriptionId
//   ADMIN → formationId uniquement (ses formations)
//   STAGIAIRE → inscriptionId uniquement (ses inscriptions)

// 201 Created → DocumentResponse
// 400 — type de fichier non autorisé ou taille dépassée
// 403 — droits insuffisants
```

### GET /api/documents/{id}/download
Télécharger un fichier.
```
// 200 OK — flux binaire (Content-Type selon mimeType)
// 403 — accès non autorisé
// 404
```

### DELETE /api/documents/{id}
Supprimer un document (SUPER_ADMIN, ADMIN sur ses formations).
```json
// 204 No Content
```

---

## 6. Messagerie

### GET /api/messages
Liste des conversations (derniers messages de chaque fil).
```json
// 200 OK → ConversationResponse[]
// Triées par date DESC (plus récent en premier)
```

### GET /api/messages/{conversationId}
Tous les messages d'un fil de conversation.
```json
// 200 OK → MessageResponse[]
// Triés par date ASC
```
> `conversationId` = l'`id` (userId) de l'autre participant — **pas** une entité `Conversation` distincte,
> aucune table dédiée en base. Un fil = tous les messages échangés entre l'utilisateur courant et cet
> utilisateur (calculé côté backend en filtrant `messages`/`message_recipients` sur cette paire). Voir
> `DB_MODEL.md` — Décisions 3NF.

### POST /api/messages/send
Envoyer un message individuel ou groupé.
```json
// Body — message individuel
{
  "recipientIds": [3],
  "content": "string"
}

// Body — message groupé avec filtre
{
  "content": "string",
  "filter": {
    "type": "FORMATION",         // FORMATION | MISSING_DOCS | MANUAL
    "formationId": 1             // requis si type=FORMATION
  }
}

// 201 Created → MessageResponse
```

### PATCH /api/messages/{id}/read
Marquer un message comme lu.
```json
// 200 OK
```

---

## 7. Notifications

### GET /api/notifications
Toutes les notifications (page plein écran — conservées, non supprimables).
```json
// Query params : ?read=true|false | ?sort=date
// 200 OK → NotificationResponse[]
```

### GET /api/notifications/unread
Notifications non lues (pour la cloche).
```json
// 200 OK
{ "count": 3, "notifications": NotificationResponse[] }
```
> Appelé par **polling** côté frontend (toutes les 30-60s) — pas de WebSocket/SSE. Choix volontaire pour
> rester cohérent avec `INFRASTRUCTURE.md` (pas de connexion persistante à maintenir, pas de complexité
> serveur additionnelle pour ce volume d'utilisateurs).

### PATCH /api/notifications/{id}/read
Marquer une notification comme lue.
```json
// 200 OK → NotificationResponse
```

### PATCH /api/notifications/read-all
Marquer toutes les notifications comme lues.
```json
// 200 OK
```

### DELETE /api/notifications/{id}
Supprimer de la cloche (uniquement — la notification reste dans l'historique).
```json
// 204 No Content
```

---

## DTOs

### UserResponse
| Champ Java | Type Java | Type JS | Notes |
|---|---|---|---|
| id | Long | number | |
| email | String | string | |
| nom | String | string | |
| prenom | String | string | |
| role | Role (enum) | `'SUPER_ADMIN' \| 'ADMIN' \| 'STAGIAIRE'` | |
| isActive | boolean | boolean | |
| emailNotificationsEnabled | boolean | boolean | |
| createdAt | LocalDateTime | string | ISO 8601 |

### FormationResponse
| Champ Java | Type Java | Type JS | Notes |
|---|---|---|---|
| id | Long | number | |
| intitule | String | string | |
| description | String (nullable) | `string \| null` | |
| dateDebut | LocalDate | string | `"2026-03-10"` |
| dateFin | LocalDate | string | `"2026-03-12"` |
| modalite | Modalite (enum) | `'VISIO' \| 'PRESENTIEL' \| 'MIXTE'` | |
| status | FormationStatus (enum) | `'ACTIVE' \| 'ARCHIVED'` | |
| formateur | UserResponse | UserResponse | peut être le Super Admin |
| inscriptionsCount | int | number | calculé |
| createdAt | LocalDateTime | string | ISO 8601 |

### InscriptionResponse
| Champ Java | Type Java | Type JS | Notes |
|---|---|---|---|
| id | Long | number | |
| stagiaire | UserResponse | UserResponse | |
| formation | FormationResponse | FormationResponse | |
| inscritLe | LocalDateTime | string | ISO 8601 |

### DocumentResponse
| Champ Java | Type Java | Type JS | Notes |
|---|---|---|---|
| id | Long | number | |
| fileName | String | string | nom original |
| fileUrl | String | string | URL Supabase |
| fileSize | Long | number | en octets |
| mimeType | String | string | ex: `application/pdf` |
| uploadedBy | UserResponse | UserResponse | |
| formationId | Long (nullable) | `number \| null` | |
| inscriptionId | Long (nullable) | `number \| null` | |
| createdAt | LocalDateTime | string | ISO 8601 |

### MessageResponse
| Champ Java | Type Java | Type JS | Notes |
|---|---|---|---|
| id | Long | number | |
| content | String | string | |
| sender | UserResponse | UserResponse | |
| recipients | List\<UserResponse\> | UserResponse[] | |
| isGroup | boolean | boolean | |
| readAt | LocalDateTime (nullable) | `string \| null` | ISO 8601 |
| createdAt | LocalDateTime | string | ISO 8601 |

### ConversationResponse
| Champ Java | Type Java | Type JS | Notes |
|---|---|---|---|
| conversationId | Long | number | = `userId` de l'autre participant, pas un ID de table dédiée |
| participant | UserResponse | UserResponse | l'autre personne |
| lastMessage | MessageResponse | MessageResponse | |
| unreadCount | int | number | |

### NotificationResponse
| Champ Java | Type Java | Type JS | Notes |
|---|---|---|---|
| id | Long | number | |
| type | NotificationType (enum) | string | ex: `'NEW_MESSAGE'`, `'DOCUMENT_UPLOADED'` |
| content | String | string | texte lisible ex: "Nouveau message de Marie" |
| entityType | EntityType (enum) | string | `'FORMATION' \| 'MESSAGE'` |
| entityId | Long | number | pour la navigation |
| isRead | boolean | boolean | |
| createdAt | LocalDateTime | string | ISO 8601 |

---

## Conventions

- **Dates** : ISO 8601 — `LocalDate` → `"2026-03-10"` / `LocalDateTime` → `"2026-03-10T14:30:00"`
- **IDs** : `Long` Java → `number` JavaScript
- **Enums** : `UPPER_CASE` Java → string literal JavaScript
- **Listes vides** : retourner `[]` jamais `null`
- **Auth** : cookie HttpOnly `jwt` — Axios doit avoir `withCredentials: true`
- **Pagination** : non prévue en V1 (volumétrie faible)

---

_Contrat API créé le 2026-08-21 — toute modification doit être validée par Charlotte ET Manon_
