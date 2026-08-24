# Portail de Formation ADAC — Contexte projet

## Vue d'ensemble

Portail de gestion des formations pour l'ADAC, remplaçant les échanges email individuels par une plateforme centralisée. La chargée de formation y publie les formations, dépose les documents et communique avec les stagiaires, en conformité avec les critères Qualiopi.

## Équipe

- **Charlotte** → backend (`back/`)
- **Manon** → frontend (`front/`)

## Structure du monorepo

```
adac-portail/
├── front/              ← React + Vite (Manon)
├── back/               ← Java Spring Boot 3 (Charlotte)
├── docs/               ← toute la documentation projet
│   ├── PRD.md
│   ├── STACK.md
│   ├── ARCHI.md
│   ├── tech.md
│   ├── DESIGN.md
│   ├── DB_MODEL.mmd
│   ├── DB_MODEL.md
│   ├── USERFLOW.mmd
│   ├── USERFLOW.md
│   ├── STORIES.md
│   └── TICKETS.md
├── nginx/              ← Config reverse proxy
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

### Frontend (`front/`)

```bash
npm run dev                  # démarrer
npm run test                 # lancer les tests
npm run build                # build production
```

App : `http://localhost:5173`

### Docker

```bash
docker-compose up --build    # démarrer tous les services
docker-compose down          # arrêter
```

## Auth — point clé

JWT stocké en **cookie HttpOnly** (pas de header Authorization).
Axios doit avoir `withCredentials: true` sur toutes les requêtes.
Voir `tech.md` section "Configuration" pour les détails.
