# Portail ADAC — Backend (Charlotte)

## Stack
- **Framework** : Java Spring Boot 3
- **Base de données** : PostgreSQL 16
- **ORM** : Spring Data JPA + Hibernate
- **Utilitaires** : Lombok + MapStruct
- **Auth** : Spring Security + JWT en cookie HttpOnly (auth0 java-jwt)
- **Validation** : @Valid + Bean Validation
- **API docs** : Swagger (springdoc-openapi) — `http://localhost:8080/swagger-ui.html`
- **Build** : Maven
- **Stockage fichiers** : Supabase Storage
- **Emails** : JavaMailSender (Mailtrap en dev, Brevo en prod)

## Package racine : `com.adac.portail`

## Architecture — flux standard
```
Requête HTTP
  → JwtAuthorizationFilter (lit et valide le cookie)
  → Controller (@RestController)
  → Service (interface + ServiceImpl)
  → Repository (JpaRepository)
  → PostgreSQL
```

## Packages
| Package | Rôle |
|---|---|
| `controller/` | Reçoit les requêtes, appelle le service, retourne ResponseEntity |
| `service/` | Toute la logique métier — interface + Impl séparés |
| `repository/` | JPA — extends JpaRepository, requêtes custom si besoin |
| `entity/` | Tables PostgreSQL + enums (Role, Modalite, FormationStatus...) |
| `dto/request/` | Ce que le frontend envoie (CreateFormationRequest...) |
| `dto/response/` | Ce que le backend retourne (FormationResponse...) |
| `mapper/` | MapStruct — Entity ↔ DTO |
| `security/` | Filtres JWT, JwtTokenService, PasswordEncoderConfig, SecurityConfig |
| `security/filter/` | JwtAuthenticationFilter + JwtAuthorizationFilter |
| `config/` | SwaggerConfig, MailConfig, SupabaseConfig |
| `exception/` | GlobalExceptionHandler (@ControllerAdvice) + exceptions métier |
| `utils/` | EmailTemplateBuilder, ExcelImportUtil, FileValidator |
| `scheduler/` | TokenCleanupScheduler (cron 3h — purge tokens expirés) |

## Contrat API
**Toujours lire `../docs/tech.md` avant de créer un endpoint.**
Ne jamais créer un endpoint qui n'est pas dans `docs/tech.md` sans le mettre à jour d'abord.

Tous les endpoints sont documentés avec `@Operation` et `@ApiResponse`.

## Auth — JWT en cookie HttpOnly
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

## Tests (TDD — toujours écrire les tests avant le code)
> Règle : tester uniquement ce qui est utile. Pas de tests sur les getters/setters triviaux.

| Couche | Annotation | Ce qu'on teste |
|---|---|---|
| Controller | `@WebMvcTest` | Routes, status HTTP, sérialisation JSON |
| Service | `@ExtendWith(MockitoExtension)` | Logique métier, cas d'erreur |
| Repository | `@DataJpaTest` | Requêtes JPQL custom |

```bash
mvn test                      # tous les tests
mvn test -Dtest=NomTest       # un test spécifique
mvn test -pl . -am            # depuis la racine du monorepo
```

## Commandes Maven
```bash
mvn spring-boot:run           # démarrer (port 8080)
mvn test                      # lancer les tests
mvn package -DskipTests       # build JAR
mvn clean install             # clean + install
```

## Variables d'environnement
Fichier `.env` à la racine de `back/` (copier `.env.example` à la racine du monorepo).

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

## Swagger
URL : `http://localhost:8080/swagger-ui.html`
Documenter chaque endpoint :
```java
@Operation(summary = "Créer une formation")
@ApiResponse(responseCode = "201", description = "Formation créée")
@ApiResponse(responseCode = "403", description = "Accès refusé")
```

## Références
- `../docs/tech.md` → contrat API complet (endpoints + DTOs)
- `../docs/ARCHI.md` → structure des packages et conventions de nommage
- `../docs/DB_MODEL.mmd` → schéma PostgreSQL
- `../docs/STACK.md` → stack complète et conventions
