# Stack — Portail de Formation ADAC

## Frontend _(géré par Manon)_

- **Framework** : React + Vite 5
- **UI Library** : non définie — à choisir par la collègue
- **HTTP** : Axios
- **Forms** : React Hook Form _(probable — à confirmer)_
- **Routing** : React Router v6
- **Tests** : Vitest + @testing-library/react + MSW
- **Langage** : JavaScript

## Backend _(Charlotte)_

- **Framework** : Java Spring Boot 3
- **Base de données** : PostgreSQL
- **ORM** : Spring Data JPA + Hibernate
- **Utilitaires** : Lombok (getters/setters/constructeurs) + MapStruct (mappers DTO ↔ entités)
- **Auth** : Spring Security + JWT (auth0 java-jwt)
  - JWT stocké en **cookie HttpOnly** (inaccessible au JavaScript — protection XSS)
  - Cookie : `HttpOnly`, `Secure` (HTTPS en prod), `SameSite=Strict`
  - CSRF : désactivé côté Spring Security (SameSite=Strict + HTTPS suffisent)
  - CORS : configuré avec `allowCredentials = true` + origines explicites
- **Validation** : @Valid + Bean Validation
- **API docs** : Swagger via springdoc-openapi — UI disponible à `/swagger-ui.html`
- **Build** : Maven

## Services externes

- **Stockage fichiers** : Supabase Storage (EU — RGPD ✓) — PDFs et documents uploadés
  - Free tier : 1 Go de stockage, 2 Go de bande passante/mois (largement suffisant pour un début)
- **Emails transactionnels** :
  - Dev : Mailtrap (SMTP sandbox)
  - Prod : Brevo / Sendinblue (RGPD ✓, 300 emails/jour gratuits)
  - Intégration : JavaMailSender via `application.properties` (profils `dev` / `prod`)

## Tests _(TDD — toujours écrire les tests avant le code)_

> Règle : on teste uniquement ce qui est utile — pas de tests sur des getters/setters triviaux.

### Backend

- **Framework** : JUnit 5
- **Mocks** : Mockito
- **Intégration** : @SpringBootTest, @WebMvcTest, @DataJpaTest

### Frontend

- **Framework** : Vitest
- **Composants** : @testing-library/react
- **Mocks HTTP** : MSW (Mock Service Worker)

## Infrastructure & DevOps

### Docker (3 containers)

| Container  | Image                     | Rôle                                            |
| ---------- | ------------------------- | ----------------------------------------------- |
| `db`       | postgres:16               | Base de données PostgreSQL                      |
| `backend`  | openjdk:21 / image custom | API Spring Boot                                 |
| `frontend` | nginx:alpine              | Sert le build React + reverse proxy vers `/api` |

- Orchestration : **Docker Compose** (suffisant pour un trafic faible)
- Fichiers : `Dockerfile` à la racine de ce repo (backend) et dans le repo front séparé (frontend),
  `docker-compose.yml` à la racine de ce repo
- Variables d'environnement : `.env` (non versionné) + `.env.example`
- Supabase Storage et Brevo sont **externes** — pas de container dédié

### Hébergement

- **VPS** : Hetzner ou Scaleway (EU, data centers France/Allemagne, RGPD ✓, coût faible)
- **Reverse proxy** : Nginx (intégré au container frontend)
- Détails des Dockerfiles et de la configuration Nginx → voir `ARCHI.md`

## Conventions

- Dates : ISO 8601 (string partout)
- IDs : `Long` Java → `number` JavaScript
- Enums : `UPPER_CASE` Java → string literal JavaScript
- Auth : cookie HttpOnly `jwt=<token>` (envoyé automatiquement par le navigateur — pas d'header manuel)
- Commits : conventional commits (`feat` / `fix` / `docs` / `chore`)
- Structure : deux repos GitHub séparés (`ADAC-Formation/back` — ce repo, code + docs + infra —
  et `ADAC-Formation/front` — géré par Manon). Le contrat partagé entre les deux est `docs/tech.md`.

_Stack validée le 2026-08-19_
