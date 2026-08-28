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
│   │   │   │   ├── FormationController.java
│   │   │   │   ├── InscriptionController.java
│   │   │   │   ├── DocumentController.java
│   │   │   │   ├── MessageController.java
│   │   │   │   └── NotificationController.java
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── AuthService.java              ← interface
│   │   │   │   ├── AuthServiceImpl.java
│   │   │   │   ├── UserService.java
│   │   │   │   ├── UserServiceImpl.java
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
│   │   │   │   │   ├── ForgotPasswordRequest.java
│   │   │   │   │   ├── ResetPasswordRequest.java
│   │   │   │   │   ├── CreateUserRequest.java
│   │   │   │   │   ├── UpdateUserRequest.java
│   │   │   │   │   ├── CreateFormationRequest.java
│   │   │   │   │   ├── UpdateFormationRequest.java
│   │   │   │   │   └── SendMessageRequest.java
│   │   │   │   └── response/
│   │   │   │       ├── UserResponse.java
│   │   │   │       ├── FormationResponse.java
│   │   │   │       ├── DocumentResponse.java
│   │   │   │       ├── MessageResponse.java
│   │   │   │       └── NotificationResponse.java
│   │   │   │
│   │   │   ├── mapper/
│   │   │   │   ├── UserMapper.java
│   │   │   │   ├── FormationMapper.java
│   │   │   │   ├── DocumentMapper.java
│   │   │   │   ├── MessageMapper.java
│   │   │   │   └── NotificationMapper.java
│   │   │   │
│   │   │   ├── security/
│   │   │   │   ├── filter/
│   │   │   │   │   ├── JwtAuthenticationFilter.java  ← gère POST /auth/login, pose le cookie
│   │   │   │   │   └── JwtAuthorizationFilter.java   ← valide le cookie sur chaque requête
│   │   │   │   ├── CustomAuthenticationManager.java  ← vérifie email + password
│   │   │   │   ├── JwtTokenService.java              ← génère et valide le token JWT
│   │   │   │   ├── PasswordEncoderConfig.java        ← bean BCryptPasswordEncoder
│   │   │   │   ├── SecurityConfig.java               ← config Spring Security + CORS + règles
│   │   │   │   │                                        (placeholder permitAll depuis TICKET-001 —
│   │   │   │   │                                         remplacé par la vraie config JWT en TICKET-006)
│   │   │   │   └── CustomUserDetailsService.java     ← charge l'utilisateur depuis la DB
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── SwaggerConfig.java
│   │   │   │   ├── MailConfig.java
│   │   │   │   └── SupabaseConfig.java               ← client HTTP Supabase Storage
│   │   │   │
│   │   │   ├── exception/
│   │   │   │   ├── GlobalExceptionHandler.java       ← @ControllerAdvice
│   │   │   │   ├── ResourceNotFoundException.java    ← 404
│   │   │   │   ├── UnauthorizedException.java        ← 403
│   │   │   │   └── BadRequestException.java          ← 400
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
│   │       ├── application.properties             ← config commune
│   │       ├── application-dev.properties         ← Mailtrap + DB locale
│   │       ├── application-prod.properties        ← Brevo + DB prod
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
    ports:
      - "5432:5432"

  backend:
    build: ./back
    depends_on:
      - db
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
    ports:
      - "8080:8080"

  frontend:
    image: ghcr.io/adac-formation/front:${FRONT_IMAGE_TAG:-latest}  # PULL, pas de build local — image
    depends_on:                                                     # construite par le CI du repo front
      - backend
    ports:
      - "80:80"
      - "443:443"

volumes:
  postgres_data:
```

### `Dockerfile`
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
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
