# Portail de Formation ADAC — Contexte projet

## Vue d'ensemble

Portail de gestion des formations pour l'ADAC, remplaçant les échanges email individuels par une plateforme centralisée. La chargée de formation y publie les formations, dépose les documents et communique avec les stagiaires, en conformité avec les critères Qualiopi.

## Équipe

- **Charlotte** → backend (`back/`)
- **Manon** → frontend (`front/`)

## Structure du projet — deux repos séparés

Pas un monorepo : `front` et `back` sont deux repos GitHub distincts sous l'organisation `ADAC-Formation`.
Ce repo (`ADAC-Formation/back`) porte le code backend **et** toute la documentation/infra partagée, puisque
c'est Charlotte qui gère le déploiement. Le seul fichier que Manon doit lire ici est `docs/tech.md`.

```
back/ (ce repo)                 front/ (repo séparé — Manon)
├── back/                       ├── src/
│   └── ... Spring Boot         └── ... React + Vite
├── docs/               ← toute la documentation projet
│   ├── PRD.md
│   ├── STACK.md
│   ├── ARCHI.md
│   ├── tech.md         ← seul fichier que Manon doit lire ici
│   ├── DESIGN.md
│   ├── DB_MODEL.mmd
│   ├── DB_MODEL.md
│   ├── USERFLOW.mmd
│   ├── USERFLOW.md
│   ├── STORIES.md
│   ├── TICKETS.md
│   ├── INFRA_REQUIREMENTS.md
│   ├── INFRASTRUCTURE.md
│   └── tickets/
├── nginx/              ← Config reverse proxy (image frontend PULLED, pas buildée ici)
├── docker-compose.yml
├── .env.example
└── CLAUDE.md           ← ce fichier
```

## Fichiers de référence

| Fichier             | Rôle                                                                       |
| ------------------- | -------------------------------------------------------------------------- |
| `docs/PRD.md`       | Fonctionnalités V1, hors périmètre, contraintes                            |
| `docs/STACK.md`     | Technologies choisies — source de vérité pour les tests et les dépendances |
| `docs/ARCHI.md`     | Structure des packages, conventions de nommage, Docker                     |
| `docs/tech.md`      | Contrat API complet — endpoints, DTOs, codes HTTP                          |
| `docs/DESIGN.md`    | Palette ADAC, typographie Manrope, composants                              |
| `docs/DB_MODEL.mmd` | Schéma PostgreSQL — 8 tables en 3NF                                        |

## Rôles utilisateurs

- `SUPER_ADMIN` — Chargée de Formation : accès total
- `ADMIN` — Formateurs : lecture + messagerie + ajout docs
- `STAGIAIRE` — Apprenants : lecture de leurs formations + messagerie

## Règle TDD — obligatoire sur chaque ticket

1. Lire `STACK.md` pour identifier les frameworks de test
2. Écrire les tests **avant** le code (ils doivent être rouges)
3. Lancer les tests → confirmer RED
4. Écrire le minimum de code pour les faire passer
5. Lancer les tests → confirmer GREEN
6. Refactoriser si nécessaire

**Ne jamais écrire de code d'implémentation avant que les tests du ticket existent.**

## Commandes utiles

### Backend (`back/`)

```bash
mvn spring-boot:run          # démarrer
mvn test                     # lancer les tests
mvn package -DskipTests      # build JAR
```

Swagger UI : `http://localhost:8080/swagger-ui.html`

### Frontend (repo séparé `ADAC-Formation/front`, géré par Manon)

```bash
npm run dev                  # démarrer
npm run test                 # lancer les tests
npm run build                # build production
```

App : `http://localhost:5173`
Pas dans ce repo — cloner `ADAC-Formation/front` séparément si besoin de le lancer localement.

### Docker

```bash
docker-compose up --build    # démarrer tous les services
docker-compose down          # arrêter
```

## Auth — point clé

JWT stocké en **cookie HttpOnly** (pas de header Authorization).
Axios doit avoir `withCredentials: true` sur toutes les requêtes.
Voir `tech.md` section "Configuration" pour les détails.

```java
// Le filtre lit le cookie sur chaque requête :
Arrays.stream(request.getCookies())
    .filter(c -> "jwt".equals(c.getName()))
    .findFirst()
    .ifPresent(c -> validateAndSetContext(c.getValue()));

// Login → poser le cookie :
ResponseCookie cookie = ResponseCookie.from("jwt", token)
    .httpOnly(true)
    .secure(true)   // false en dev
    .sameSite("Strict")
    .maxAge(Duration.ofHours(24))
    .path("/")
    .build();
response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
```

CORS configuré avec `allowCredentials = true` + origines explicites.

## Backend — package racine `com.adac.portail`

```
Requête HTTP
  → JwtAuthorizationFilter (lit et valide le cookie)
  → Controller (@RestController)
  → Service (interface + ServiceImpl)
  → Repository (JpaRepository)
  → PostgreSQL
```

Structure des packages, conventions de nommage complètes → `docs/ARCHI.md`.

## Tests backend (annotations)

| Couche | Annotation | Ce qu'on teste |
|---|---|---|
| Controller | `@WebMvcTest` | Routes, status HTTP, sérialisation JSON |
| Service | `@ExtendWith(MockitoExtension)` | Logique métier, cas d'erreur |
| Repository | `@DataJpaTest` | Requêtes JPQL custom |

```bash
mvn test -Dtest=NomTest       # un test spécifique
```

## Variables d'environnement (`back/.env`, copier depuis `.env.example`)

| Variable | Rôle |
|---|---|
| `DB_URL` | JDBC URL PostgreSQL |
| `DB_USERNAME` / `DB_PASSWORD` | Credentials DB |
| `JWT_SECRET` | Clé secrète JWT (min 256 bits) |
| `JWT_EXPIRATION` | Durée du token en ms (86400000 = 24h) |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP |
| `SUPABASE_URL` / `SUPABASE_KEY` / `SUPABASE_BUCKET` | Supabase Storage |
| `CORS_ALLOWED_ORIGINS` | Origines autorisées (ex: http://localhost:5173) |

Profils Spring : `dev` (Mailtrap) et `prod` (Brevo) — configurer via `SPRING_PROFILES_ACTIVE`.
