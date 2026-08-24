# TICKET-007 — Config Swagger + Mail + Supabase

## Story
Foundation — configuration des services externes

## Description
Configurer les beans de configuration pour Swagger (springdoc-openapi), JavaMailSender (profils dev/prod) et Supabase Storage. Ces beans sont injectés dans les services qui en ont besoin.

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `config/SwaggerConfig.java` — `OpenAPI` bean avec titre, version, description ADAC ; `SecurityScheme` cookie si besoin
- `config/MailConfig.java` — `JavaMailSender` bean configuré depuis les variables d'env `MAIL_*` ; commentaire sur profil dev (Mailtrap) vs prod (Brevo)
- `config/SupabaseConfig.java` — bean avec `SUPABASE_URL`, `SUPABASE_KEY`, `SUPABASE_BUCKET` ; méthode helper pour construire les URLs d'upload/download

## Acceptance criteria
- [ ] Swagger UI accessible sur `http://localhost:8080/swagger-ui.html` avec le titre "Portail de Formation ADAC"
- [ ] `JavaMailSender` s'injecte correctement dans les services (`@Autowired` / constructeur)
- [ ] Les variables Supabase sont lues depuis l'environnement (pas en dur dans le code)
- [ ] Aucune clé secrète en clair dans le code source

## Branch
`feature/setup`
- [ ] Create: `git checkout -b feature/setup`
- [x] Switch to existing: `git checkout feature/setup`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@SpringBootTest`): le contexte se charge correctement avec toutes les configs — pas d'exception au démarrage
- [ ] Test 2 (`MockMvc`): `GET /swagger-ui.html` retourne 302 ou 200 (redirect vers `/swagger-ui/index.html`)

> Note : ne pas tester les configs elles-mêmes (ce sont des beans simples). Tester que le contexte démarre.

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `chore(config): add Swagger, Mail and Supabase configuration beans`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/setup` — see TICKET-008

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot

## Depends on
- TICKET-001 — le pom.xml doit inclure springdoc-openapi et Spring Mail

## Estimated time
1h

## Status
[ ] To do   [ ] In progress   [ ] Done
