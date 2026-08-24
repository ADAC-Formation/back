# TICKET-001 — Setup Spring Boot + pom.xml

## Story
Foundation — structure de départ du projet backend

## Description
Initialiser le projet Spring Boot : pom.xml avec toutes les dépendances, structure de packages, configuration application.yml pour les profils dev et prod, et classe principale avec `@EnableScheduling`.

## Repo
[ ] front/   [x] back/   [ ] both

## Files to create or modify
- `back/pom.xml` — dépendances Spring Boot 3, Security, JPA, Validation, Lombok, MapStruct, Swagger (springdoc-openapi), auth0 java-jwt, Mail, POI (Excel), PostgreSQL driver
- `back/src/main/java/com/adac/portail/PortailAdacApplication.java` — `@SpringBootApplication` + `@EnableScheduling`
- `back/src/main/resources/application.yml` — config commune (port 8080, JPA DDL-auto=validate)
- `back/src/main/resources/application-dev.yml` — DB locale, Mailtrap, CORS localhost:5173
- `back/src/main/resources/application-prod.yml` — DB prod, Brevo, CORS domaine prod
- `back/.env.example` — toutes les variables d'env documentées

## Acceptance criteria
- [ ] `mvn spring-boot:run` démarre sans erreur sur le profil `dev`
- [ ] Swagger UI accessible sur `http://localhost:8080/swagger-ui.html`
- [ ] Tous les packages vides créés (`controller/`, `service/`, `repository/`, `entity/`, `dto/request/`, `dto/response/`, `mapper/`, `security/filter/`, `config/`, `exception/`, `utils/`, `scheduler/`)
- [ ] `.env.example` liste toutes les variables requises

## Branch
`feature/setup`
- [x] Create: `git checkout -b feature/setup`
- [ ] Switch to existing: `git checkout feature/setup`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`PortailAdacApplicationTests`): `@SpringBootTest` — vérifier que le contexte Spring se charge sans exception
- [ ] Test 2: Vérifier que `swagger-ui.html` répond 200 (MockMvc + `@AutoConfigureMockMvc`)

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `chore(setup): initialize Spring Boot project with all dependencies`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/setup` — see TICKET-008

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code

## Depends on
— (premier ticket)

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
