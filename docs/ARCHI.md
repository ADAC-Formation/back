# Architecture — Portail de Formation ADAC

## Structure du projet — deux repos séparés

Pas un monorepo. `ADAC-Formation/back` (ce repo) et `ADAC-Formation/front` (repo de Manon) sont deux repos
GitHub distincts. Ce repo porte le code backend, toute la documentation partagée et l'infra de déploiement —
`docker-compose.yml` **pull** l'image frontend déjà construite (par le CI du repo front), il ne la build pas.

```
back/ (ce repo — le code Spring Boot est à la racine, pas dans un sous-dossier)
├── src/                         ← API Spring Boot (Charlotte)
├── pom.xml
├── Dockerfile
├── nginx/
│   └── nginx.conf               ← Config reverse proxy
├── docker-compose.yml
├── docker-compose.override.yml  ← overrides locaux (ignoré par Git)
├── .env.example                 ← variables d'environnement à copier
├── .gitignore
├── docs/
│   ├── PRD.md
│   ├── STACK.md
│   ├── DESIGN.md
│   ├── ARCHI.md
│   ├── tech.md                  ← contrat API front ↔ back — seul fichier partagé avec Manon
│   ├── STORIES.md
│   ├── TICKETS.md
│   ├── INFRA_REQUIREMENTS.md
│   ├── INFRASTRUCTURE.md
│   └── tickets/
└── CLAUDE.md

front/ (repo séparé — Manon, structure gérée par elle)
```

---

## Backend — Java Spring Boot 3

### Package racine : `com.adac.portail`

Code à la racine de ce repo (pas de sous-dossier `back/`) :
```
src/
│   ├── main/
│   │   ├── java/com/adac/portail/
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── UserController.java
│   │   │   │   ├── CategoryController.java
│   │   │   │   ├── FormationController.java
│   │   │   │   ├── InscriptionController.java
│   │   │   │   ├── DocumentController.java
│   │   │   │   ├── MessageController.java
│   │   │   │   └── NotificationController.java
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── AuthService.java              ← interface
│   │   │   │   ├── AuthServiceImpl.java
│   │   │   │   ├── ActivationService.java        ← interface — activation compte + reset MDP (TICKET-015)
│   │   │   │   ├── ActivationServiceImpl.java    ← envoie le mail directement via MailSender (SimpleMailMessage,
│   │   │   │   │                                    texte brut) — EmailService/EmailTemplateBuilder plus bas
│   │   │   │   │                                    n'existent pas encore (TICKET-034, qui dépend de ce ticket) ;
│   │   │   │   │                                    à migrer dessus quand ce ticket-là atterrira
│   │   │   │   ├── UserService.java
│   │   │   │   ├── UserServiceImpl.java
│   │   │   │   ├── CategoryService.java
│   │   │   │   ├── CategoryServiceImpl.java
│   │   │   │   ├── FormationService.java
│   │   │   │   ├── FormationServiceImpl.java
│   │   │   │   ├── InscriptionService.java
│   │   │   │   ├── InscriptionServiceImpl.java
│   │   │   │   ├── DocumentService.java
│   │   │   │   ├── DocumentServiceImpl.java
│   │   │   │   ├── MessageService.java
│   │   │   │   ├── MessageServiceImpl.java
│   │   │   │   ├── NotificationService.java
│   │   │   │   ├── NotificationServiceImpl.java
│   │   │   │   ├── EmailService.java
│   │   │   │   ├── EmailServiceImpl.java
│   │   │   │   ├── StorageService.java           ← interface Supabase
│   │   │   │   └── StorageServiceImpl.java
│   │   │   │
│   │   │   ├── repository/
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── CategoryRepository.java
│   │   │   │   ├── FormationRepository.java
│   │   │   │   ├── InscriptionRepository.java
│   │   │   │   ├── DocumentRepository.java
│   │   │   │   ├── MessageRepository.java
│   │   │   │   ├── MessageRecipientRepository.java
│   │   │   │   ├── NotificationRepository.java
│   │   │   │   └── ActivationTokenRepository.java
│   │   │   │
│   │   │   ├── entity/
│   │   │   │   ├── User.java
│   │   │   │   ├── Category.java
│   │   │   │   ├── Formation.java
│   │   │   │   ├── Inscription.java
│   │   │   │   ├── Document.java
│   │   │   │   ├── Message.java
│   │   │   │   ├── MessageRecipient.java
│   │   │   │   ├── Notification.java
│   │   │   │   └── ActivationToken.java
│   │   │   │
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   ├── ActivateAccountRequest.java
│   │   │   │   │   ├── ResendActivationRequest.java
│   │   │   │   │   ├── ForgotPasswordRequest.java
│   │   │   │   │   ├── ResetPasswordRequest.java
│   │   │   │   │   ├── CreateUserRequest.java
│   │   │   │   │   ├── UpdateUserRequest.java
│   │   │   │   │   ├── UpdateProfileRequest.java
│   │   │   │   │   ├── CreateCategoryRequest.java
│   │   │   │   │   ├── UpdateCategoryRequest.java
│   │   │   │   │   ├── CreateFormationRequest.java
│   │   │   │   │   ├── UpdateFormationRequest.java
│   │   │   │   │   ├── SendMessageRequest.java             ← + nested Filter class ; couvre les DEUX formes de
│   │   │   │   │   │                                          body (individuel ET groupé, review TICKET-005 —
│   │   │   │   │   │                                          un seul endpoint POST /messages/send ne peut pas
│   │   │   │   │   │                                          bind sur deux DTO @RequestBody différents)
│   │   │   │   │   └── MessageFilterType.java             ← enum non persisté (FORMATION|MISSING_DOCS|MANUAL)
│   │   │   │   └── response/
│   │   │   │       ├── UserResponse.java
│   │   │   │       ├── CategoryResponse.java
│   │   │   │       ├── FormationResponse.java
│   │   │   │       ├── InscriptionResponse.java
│   │   │   │       ├── DocumentResponse.java
│   │   │   │       ├── MessageResponse.java
│   │   │   │       ├── ConversationResponse.java          ← assemblé en service, pas mappé d'une entité
│   │   │   │       ├── NotificationResponse.java
│   │   │   │       ├── StatusMessageResponse.java         ← record {message} — le corps {"message": "..."} que
│   │   │   │       │                                          renvoient activate/resend-activation/
│   │   │   │       │                                          forgot-password/reset-password (TICKET-015)
│   │   │   │       └── ErrorResponse.java                 ← record {status, message, details} — format
│   │   │   │                                                 d'erreur tech.md ; utilisé à la fois par les
│   │   │   │                                                 filtres de security/ (TICKET-006, avant que
│   │   │   │                                                 exception/GlobalExceptionHandler existe) et par
│   │   │   │                                                 ce dernier (TICKET-015)
│   │   │   │
│   │   │   ├── mapper/
│   │   │   │   ├── UserMapper.java
│   │   │   │   ├── CategoryMapper.java
│   │   │   │   ├── FormationMapper.java
│   │   │   │   ├── DocumentMapper.java
│   │   │   │   ├── MessageMapper.java
│   │   │   │   └── NotificationMapper.java
│   │   │   │
│   │   │   ├── security/
│   │   │   │   ├── filter/
│   │   │   │   │   ├── JwtAuthenticationFilter.java  ← gère POST /auth/login, pose le cookie,
│   │   │   │   │   │                                    répond UserResponse ou ErrorResponse (400/401/403)
│   │   │   │   │   └── JwtAuthorizationFilter.java   ← valide le cookie sur chaque requête ; rejette
│   │   │   │   │                                        (sans 500) un utilisateur supprimé/désactivé
│   │   │   │   ├── AuthenticationConfig.java         ← expose l'AuthenticationManager (DaoAuthenticationProvider
│   │   │   │   │                                        + BCrypt — pas de vérif manuelle : voir sa Javadoc pour
│   │   │   │   │                                        pourquoi CustomAuthenticationManager a été abandonné
│   │   │   │   │                                        en review TICKET-006 — timing attack + statut compte)
│   │   │   │   ├── AdacUserDetails.java              ← UserDetails qui porte l'entité User complète (pas
│   │   │   │   │                                        seulement email+password) — principal de l'Authentication
│   │   │   │   ├── JwtTokenService.java              ← génère et vérifie le token JWT (verify() seulement —
│   │   │   │   │                                        pas de méthode qui décode sans vérifier la signature)
│   │   │   │   ├── JwtCookieFactory.java             ← seule source de vérité pour le cookie jwt (issue/expire) ;
│   │   │   │   │                                        utilisé par JwtAuthenticationFilter (login) et
│   │   │   │   │                                        AuthController (logout) — TICKET-014
│   │   │   │   ├── PasswordEncoderConfig.java        ← bean BCryptPasswordEncoder
│   │   │   │   ├── SecurityConfig.java               ← config Spring Security + CORS + règles
│   │   │   │   │                                        (JWT stateless depuis TICKET-006 ; routes
│   │   │   │   │                                         publiques : /api/auth/**, /swagger-ui/**)
│   │   │   │   └── CustomUserDetailsService.java     ← charge l'utilisateur depuis la DB, renvoie un
│   │   │   │                                            AdacUserDetails
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── SwaggerConfig.java
│   │   │   │   ├── MailConfig.java
│   │   │   │   └── SupabaseConfig.java               ← client HTTP Supabase Storage
│   │   │   │
│   │   │   ├── exception/
│   │   │   │   ├── GlobalExceptionHandler.java       ← @RestControllerAdvice (TICKET-015 — première version,
│   │   │   │   │                                        ne gère pour l'instant que les 3 exceptions ci-dessous ;
│   │   │   │   │                                        login/me restent gérés dans security/, voir leur note)
│   │   │   │   ├── ActivationTokenExpiredException.java  ← 400 (TICKET-015)
│   │   │   │   ├── ActivationTokenInvalidException.java  ← 400, même message que ci-dessus (TICKET-015)
│   │   │   │   ├── RateLimitException.java               ← 429 (TICKET-015)
│   │   │   │   ├── ResourceNotFoundException.java    ← 404 (pas encore créée — arrivera avec un ticket CRUD)
│   │   │   │   ├── UnauthorizedException.java        ← 403 (pas encore créée)
│   │   │   │   └── BadRequestException.java          ← 400 (pas encore créée)
│   │   │   │
│   │   │   ├── utils/
│   │   │   │   ├── EmailTemplateBuilder.java         ← construit le HTML des emails
│   │   │   │   ├── ExcelImportUtil.java              ← parse les fichiers Excel (formations)
│   │   │   │   └── FileValidator.java                ← vérifie type et taille des fichiers
│   │   │   │
│   │   │   └── scheduler/
│   │   │       └── TokenCleanupScheduler.java        ← @Scheduled cron 3h — supprime tokens expirés/utilisés
│   │   │
│   │   └── resources/
│   │       ├── application.yml                    ← config commune (implémenté en YAML, pas .properties)
│   │       ├── application-dev.yml                ← Mailtrap + DB locale
│   │       ├── application-prod.yml                ← Brevo + DB prod
│   │       ├── db/migration/
│   │       │   └── V1__init_schema.sql             ← DDL des 8 tables, géré par Flyway (TICKET-004) — ne jamais
│   │       │                                          éditer une fois appliqué, ajouter V2__... à la place
│   │       │
│   │       │   ⚠️ Si ta DB locale `adac_portail` a été créée avant le TICKET-004 (schéma posé par l'ancien
│   │       │      `schema.sql` intérimaire), Flyway refuse de démarrer : "Found non-empty schema(s) but
│   │       │      no schema history table". Réinitialise une seule fois avec :
│   │       │      `psql -h localhost -U <user> -d adac_portail -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"`
│   │       │      puis relance — Flyway recrée tout proprement via V1. Ne pas utiliser
│   │       │      `spring.flyway.baseline-on-migrate=true` à la place : ça saute V1 sans jamais le vérifier.
│   │       └── templates/email/
│   │           ├── activation.html
│   │           ├── reset-password.html
│   │           └── notification.html
│   │
│   └── test/java/com/adac/portail/
│       ├── controller/                            ← @WebMvcTest
│       ├── service/                               ← @ExtendWith(MockitoExtension)
│       └── repository/                            ← @DataJpaTest
│
├── pom.xml
└── Dockerfile
```

---

## Patterns backend

### Flux standard
```
HTTP Request
  → JwtAuthorizationFilter (valide le cookie)
  → Controller (@RestController)
  → Service (logique métier)
  → Repository (JPA)
  → PostgreSQL
```

### Règles d'architecture
- **Controller** : reçoit la requête, délègue au service, retourne un ResponseEntity. Pas de logique métier.
- **Service** : toute la logique métier. Interface + Impl séparés.
- **Repository** : extends JpaRepository. Requêtes JPQL si besoin.
- **Entity** : annotée @Entity. Jamais exposée directement en réponse HTTP.
- **DTO** : toujours utiliser des DTOs pour les entrées (Request) et sorties (Response).
- **Mapper** : MapStruct fait la conversion Entity ↔ DTO.

---

## Conventions de nommage

| Élément | Convention | Exemple |
|---|---|---|
| Entity | PascalCase | `User.java`, `Formation.java` |
| Controller | `[Entity]Controller` | `FormationController.java` |
| Service | `[Entity]Service` + `[Entity]ServiceImpl` | `UserService.java` |
| Repository | `[Entity]Repository` | `InscriptionRepository.java` |
| DTO entrée | `Create[Entity]Request`, `Update[Entity]Request` | `CreateFormationRequest.java` |
| DTO sortie | `[Entity]Response` | `FormationResponse.java` |
| Mapper | `[Entity]Mapper` | `DocumentMapper.java` |
| Enum | UPPER_CASE | `Role.SUPER_ADMIN`, `Modalite.VISIO` |
| Endpoints | kebab-case | `/api/formations`, `/api/activation-tokens` |

---

## Authentification

- **Mécanisme** : JWT stocké en cookie HttpOnly
- **Cookie** : nom `jwt`, `HttpOnly`, `Secure` (prod), `SameSite=Strict`
- **Filtre** : `JwtAuthorizationFilter` lit `request.getCookies()`, valide le token, alimente le `SecurityContext`
- **Login** : POST `/api/auth/login` → pose le cookie + retourne `UserResponse`
- **Logout** : POST `/api/auth/logout` → expire le cookie (MaxAge=0)
  - **Risque résiduel accepté (TICKET-014, étendu TICKET-015)** : ni le logout ni un changement
    de mot de passe (`activate`/`reset-password`) ne révoquent quoi que ce soit côté serveur — les
    trois se contentent d'expirer/remplacer le cookie ou le hash en base, jamais le JWT lui-même.
    Un token capturé avant coup (poste partagé, log d'un proxy, extension navigateur) reste valide
    jusqu'à expiration naturelle (24h par défaut) même après un reset MDP — alors que le reset MDP
    est justement le geste de remédiation typique d'un compte compromis. Acceptable pour le MVP vu
    le profil du projet (cookie HttpOnly + SameSite=Strict, pas de contenu sensible côté paiement).
    À revoir (claim `token_version`/`passwordChangedAt` sur `User`, comparé dans
    `JwtAuthorizationFilter`, bumpé aux trois endroits ; ou denylist par `jti`) si ce risque devient
    inacceptable — pas de ticket ouvert pour l'instant, décision à prendre par Charlotte.
  - `JwtCookieFactory` (package `security/`) est la seule source de vérité pour la forme du cookie
    `jwt` — login (`JwtAuthenticationFilter`) et logout (`AuthController`) l'utilisent tous les
    deux, pour que le cookie de logout ait exactement les mêmes attributs (path, secure, sameSite)
    que celui posé au login ; sinon le navigateur ne le supprime pas.
- **Durée du token** : 24h (configurable via `JWT_EXPIRATION`)
- **CSRF** : protection Spring Security désactivée — décision de sécurité explicite.
  - Raison réelle : `SameSite=Strict` sur le cookie `jwt` empêche le navigateur de l'envoyer sur toute requête
    cross-site (formulaire ou fetch initié depuis un autre site), ce qui neutralise le vecteur CSRF classique.
    **HTTPS ne protège pas contre le CSRF** (il protège le transport, pas l'origine des requêtes) — à ne plus
    citer comme justification.
  - Risque résiduel accepté : `SameSite=Strict` ne couvre pas les très vieux navigateurs ni les attaques
    same-site (ex. sous-domaine compromis). Acceptable vu le profil du projet (utilisateurs connus, pas de
    contenu tiers). À revoir si un sous-domaine externe rejoint l'écosystème.
- **CORS** : `allowCredentials = true`, origines explicites depuis `.env`

### Activation compte / reset MDP (TICKET-015)

- Code à 6 chiffres (`SecureRandom`), hashé en base (BCrypt, comme les mots de passe) — jamais
  stocké en clair. Expire 30 min après émission.
- Deux limites indépendantes (voir `ActivationServiceImpl`) : max 3 mauvais codes par token
  (`ActivationToken.attempts`, 429) et max 3 nouveaux codes émis par utilisateur / 15 min
  (`ActivationTokenRepository.countByUserAndTypeAndCreatedAtAfter`, 429 aussi).
- `attempts` doit survivre à l'exception qu'il déclenche : les deux méthodes qui l'incrémentent
  (`activate`, `resetPassword`) sont annotées `@Transactional(noRollbackFor =
  ActivationTokenInvalidException.class)` — sans ça, le rollback par défaut de Spring sur une
  `RuntimeException` annule l'incrément en même temps que l'exception, et la limite de 3 ne se
  déclenche jamais. Un test Mockito ne peut pas voir ce bug (aucune vraie transaction) ;
  `ActivationServiceIntegrationTest` (contexte Spring réel) le couvre spécifiquement.
- `User.isActive` sert à la fois "jamais activé" et "suspendu par un admin" (pas de colonne de
  statut séparée) — `resendActivation` distingue les deux via
  `ActivationTokenRepository.existsByUserAndTypeAndUsedAtIsNotNull` (un token déjà consommé =
  compte déjà activé une fois = suspension, pas primo-activation) pour qu'un compte suspendu ne
  puisse pas se réactiver lui-même en redemandant un code.
- `forgot-password` avale silencieusement son propre `RateLimitException` (log seulement, réponse
  200 inchangée) — c'est le seul moyen de tenir la promesse tech.md "même réponse si email connu
  ou inconnu" une fois la limite de 3/15min atteinte. `resend-activation` ne fait pas ça : son
  critère d'acceptation veut explicitement le 429 visible, donc cet endpoint-là reste un oracle
  d'énumération de compte pour un attaquant patient (accepté, cohérent avec le profil du projet).
- **Risques résiduels accepté, non traités ici** (pas de ticket ouvert) :
  - Le plafond de 3 tentatives est par token, pas par utilisateur — un attaquant qui enchaîne les
    codes (3/15min × 3 essais) reste sous ~9 tentatives/15min sans jamais déclencher d'alerte ni
    de blocage compte. À revoir si le profil de risque change (compteur cumulé, notification à
    l'utilisateur sur tentatives répétées).
  - `TokenCleanupScheduler` charge tous les tokens dus en mémoire puis les supprime un par un
    (pas de requête `DELETE` en masse) — non-problème à l'échelle actuelle (~50 utilisateurs),
    à revoir si le volume change significativement.

---

## Endpoints principaux

| Méthode | Endpoint | Rôle requis | Description |
|---|---|---|---|
| POST | `/api/auth/login` | — | Connexion |
| POST | `/api/auth/logout` | Tous | Déconnexion |
| POST | `/api/auth/activate` | — | Activation de compte |
| POST | `/api/auth/forgot-password` | — | Demande de réinitialisation |
| POST | `/api/auth/reset-password` | — | Nouveau mot de passe |
| GET | `/api/users` | SUPER_ADMIN | Liste des utilisateurs |
| POST | `/api/users` | SUPER_ADMIN | Créer un utilisateur |
| PATCH | `/api/users/{id}/deactivate` | SUPER_ADMIN | Désactiver |
| GET | `/api/categories` | Tous | Liste des catégories |
| POST | `/api/categories` | SUPER_ADMIN | Créer une catégorie |
| PUT | `/api/categories/{id}` | SUPER_ADMIN | Modifier nom/couleur |
| PATCH | `/api/categories/{id}/activate` | SUPER_ADMIN | Réactiver |
| PATCH | `/api/categories/{id}/deactivate` | SUPER_ADMIN | Désactiver |
| GET | `/api/formations` | Tous | Liste des formations |
| POST | `/api/formations` | SUPER_ADMIN | Créer une formation |
| POST | `/api/formations/import` | SUPER_ADMIN | Import Excel |
| PUT | `/api/formations/{id}` | SUPER_ADMIN | Modifier |
| PATCH | `/api/formations/{id}/archive` | SUPER_ADMIN | Archiver |
| POST | `/api/formations/{id}/inscriptions` | SUPER_ADMIN | Inscrire un stagiaire |
| POST | `/api/documents` | SUPER_ADMIN, ADMIN | Upload document |
| GET | `/api/documents/{id}` | Tous (selon droits) | Télécharger |
| GET | `/api/messages` | Tous | Boîte de réception |
| POST | `/api/messages` | Tous | Envoyer un message |
| GET | `/api/notifications` | Tous | Liste notifications |
| PATCH | `/api/notifications/{id}/read` | Tous | Marquer comme lue |
| DELETE | `/api/notifications/{id}` | Tous | Supprimer (cloche) |

> Détail complet des requêtes/réponses → `tech.md`

---

## Base de données

- **SGBD** : PostgreSQL 16
- **Schéma** : voir `tech.md` (contrat API) et `DB_MODEL.mmd` (ERD)
- **Profil dev** : PostgreSQL local (port 5432)
- **Profil prod** : PostgreSQL dans le container Docker

---

## Variables d'environnement

### `.env` (à copier depuis `.env.example`)
```properties
# Base de données
DB_URL=jdbc:postgresql://localhost:5432/adac_portail
DB_USERNAME=adac_user
DB_PASSWORD=adac_password

# JWT
JWT_SECRET=your_secret_key_min_256_bits
JWT_EXPIRATION=86400000
# Pas de JWT_COOKIE_SECURE ici : forcé à true en prod (application-prod.yml, non surchargeable
# par .env) et à false en dev (application.yml) — voir TICKET-006.

# Email (dev — Mailtrap)
MAIL_HOST=sandbox.smtp.mailtrap.io
MAIL_PORT=2525
MAIL_USERNAME=your_mailtrap_username
MAIL_PASSWORD=your_mailtrap_password

# Email (prod — Brevo)
# MAIL_HOST=smtp-relay.brevo.com
# MAIL_PORT=587
# MAIL_USERNAME=your_brevo_login
# MAIL_PASSWORD=your_brevo_smtp_key

# Supabase Storage
SUPABASE_URL=https://xxxx.supabase.co
SUPABASE_KEY=your_service_role_key
SUPABASE_BUCKET=adac-documents

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:5173,https://portail.adac.asso.fr
```

---

## Infrastructure Docker

### `docker-compose.yml`
Testé de bout en bout (TICKET-011, 2026-08-31) : `db` et `backend` ne publient **aucun** port vers
l'hôte (seul `frontend` le fait, sur 80) ; `backend` attend réellement que `db` soit *healthy*
(`condition: service_healthy`, pas juste `depends_on` seul — `depends_on` seul ne garantit pas que
Postgres accepte déjà les connexions) ; `nginx/nginx.conf` (ce repo) est monté en volume par-dessus
la config nginx de l'image frontend pullée, pour ajouter le reverse-proxy `/api/` sans que le
Dockerfile du repo front ait besoin de connaître le backend.
```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: adac_portail
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME} -d adac_portail"]
      interval: 5s
      timeout: 5s
      retries: 10
      start_period: 10s
    restart: unless-stopped
    networks:
      - app_network
    # Pas de `ports:` — joignable uniquement par les autres services du réseau app_network.

  backend:
    build: .
    depends_on:
      db:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: jdbc:postgresql://db:5432/adac_portail
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      JWT_EXPIRATION: ${JWT_EXPIRATION}
      MAIL_HOST: ${MAIL_HOST}
      MAIL_PORT: ${MAIL_PORT}
      MAIL_USERNAME: ${MAIL_USERNAME}
      MAIL_PASSWORD: ${MAIL_PASSWORD}
      SUPABASE_URL: ${SUPABASE_URL}
      SUPABASE_KEY: ${SUPABASE_KEY}
      SUPABASE_BUCKET: ${SUPABASE_BUCKET}
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS}
    # HEALTHCHECK déjà intégré à l'image (Dockerfile, TICKET-009) — pas besoin de le redéclarer.
    restart: unless-stopped
    networks:
      - app_network
    # Pas de `ports:` — atteint uniquement via le reverse proxy nginx de `frontend` (/api/).

  frontend:
    image: ghcr.io/adac-formation/front:${FRONT_IMAGE_TAG:-latest}  # PULL, pas de build local — image
    depends_on:                                                     # construite par le CI du repo front
      backend:
        condition: service_healthy
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/conf.d/default.conf:ro
    ports:
      - "80:80"
    restart: unless-stopped
    networks:
      - app_network

networks:
  app_network:
    driver: bridge

volumes:
  postgres_data:
```

### `Dockerfile`
Multi-stage : le stage `build` (Maven + JDK complet) compile le `.jar` ; le stage final repart d'un JRE
minimal et ne récupère que le `.jar` (`COPY --from=build`) — pas de sources, pas de Maven, pas de `.m2`
dans l'image livrée. Process non-root (`USER spring`), healthcheck intégré sur `/actuator/health`
(TICKET-007 + TICKET-009 — voir `management.endpoints.web.exposure.include: health` dans `application.yml`
et `/actuator/health` dans `SecurityConfig.PUBLIC_ROUTES`), arrêt gracieux (`server.shutdown: graceful`).
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# curl : uniquement pour le HEALTHCHECK ci-dessous
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system spring && adduser --system --ingroup spring spring

COPY --from=build /app/target/*.jar app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### `nginx/nginx.conf`
```nginx
server {
    listen 80;

    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        root /usr/share/nginx/html;
        try_files $uri /index.html;
    }
}
```

---

## Swagger

- URL locale : `http://localhost:8080/swagger-ui.html`
- Config : `src/main/java/com/adac/portail/config/SwaggerConfig.java`
- Toutes les routes documentées avec `@Operation` et `@ApiResponse`

---

_Architecture validée le 2026-08-21, révisée le 2026-08-24 (deux repos séparés) — backend uniquement (front dans `ADAC-Formation/front`, géré par Manon)_
