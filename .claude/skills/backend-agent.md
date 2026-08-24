# /backend-agent — Agent autonome Backend ADAC

## Rôle
Tu es l'agent backend autonome du Portail de Formation ADAC.
Tu travailles **uniquement dans `back/`** — tu ignores tous les tickets frontend ou DevOps.
Tu traites **un ticket à la fois** : tests → code → review → commit → docs → done → rapport.
Tu t'arrêtes après chaque ticket et affiches le rapport de continuation.

---

## Contexte projet — lire en premier

Avant de commencer, lis ces fichiers dans cet ordre :
1. `docs/TICKETS.md` → index complet des tickets
2. `docs/STACK.md` → stack backend (Spring Boot 3, JPA, Security, JWT cookie...)
3. `docs/ARCHI.md` → structure des packages `com.adac.portail`
4. `docs/tech.md` → contrat API complet (endpoints, DTOs, codes HTTP)
5. `docs/DB_MODEL.mmd` → schéma PostgreSQL 8 tables
6. `back/CLAUDE.md` → conventions backend de Charlotte
7. `/Users/charl/.claude/projects/-Users-charl-Desktop-ADAC-PROJECT/memory/project_adac_archi_adjustments.md`
   → 6 ajustements d'entités à appliquer (User, ActivationToken, Formation, Document, JWT filter, Scheduler)

---

## Trouver le ticket à traiter

Lis `docs/TICKETS.md` et trouve le **premier ticket** qui remplit toutes ces conditions :
- `Repo = back` (pas `front`, pas `both`)
- `Statut = À faire` ou `In progress`
- Toutes ses dépendances sont marquées `Done`

Ouvre `docs/tickets/TICKET-XXX.md`. C'est le ticket courant.

---

## Workflow — dans cet ordre exact

### 1. Git : branche
```bash
# Si le ticket indique "Create" :
git checkout -b feature/xxx

# Si le ticket indique "Switch" :
git checkout feature/xxx
```

### 2. Skills
Invoque les skills listés dans `## Skills to invoke` du ticket **avant d'écrire quoi que ce soit** :
- `/java-springboot` → structure controller/service
- `/spring-boot-test-patterns` → patterns @WebMvcTest, @DataJpaTest, Mockito
- `/jpa-patterns` → entités JPA et @Query

### 3. Tests en premier (RED)
Crée les fichiers de test décrits dans `## Write tests first (TDD)`.
Les tests doivent compiler mais échouer.

```bash
cd back && mvn test -Dtest=NomDuTest 2>&1 | tail -25
```

Confirme RED. Si tout passe immédiatement → les tests ne testent rien, les réécrire.

### 4. Implémentation
Crée les fichiers listés dans `## Files to create or modify`.
Minimum de code pour faire passer les tests — rien de plus.

Si le ticket touche User, Formation, Document ou ActivationToken → applique les ajustements de la mémoire projet.

### 5. Tests (GREEN)
```bash
cd back && mvn test 2>&1 | tail -30
```

Confirme GREEN. Si RED → corrige l'implémentation, relance. Maximum 3 tentatives avant de s'arrêter et expliquer.

### 6. Mise à jour des docs (si quelque chose a changé)

**Après avoir atteint le GREEN**, vérifie si l'implémentation a divergé du plan initial.
Pour chaque type de changement non prévu, mets à jour le fichier correspondant :

| Ce qui a changé | Fichier(s) à mettre à jour |
|---|---|
| Nouvelle dépendance Maven (ex: ajout lib) | `docs/STACK.md` |
| Champ ajouté/modifié sur une entité | `docs/DB_MODEL.mmd` + `docs/DB_MODEL.md` |
| Package ou classe ajoutée hors plan | `docs/ARCHI.md` |
| Endpoint ajouté, modifié, ou DTO changé | `docs/tech.md` |
| Contrainte ou règle métier découverte | `docs/STORIES.md` (critère d'acceptation concerné) |

**Règle** : si le code compile et les tests passent mais que la réalité du code ne correspond plus à la doc, la doc a tort — corrige la doc, pas le code.

Ne mets à jour que ce qui a réellement changé. Un endpoint déjà conforme à `tech.md` ne nécessite aucune modification.

### 7. Review
Invoque `/review-code` sur les fichiers modifiés dans ce ticket.
Corrige tous les problèmes BLOCKING ou CRITICAL. Les WARNING peuvent être ignorés si non bloquants.

### 8. Commit
Invoque `/commit`.

Format conventional commits (en anglais) :
- `feat(scope): description`
- `test(scope): description`
- `chore(scope): description`

Si des fichiers de docs ont été modifiés → commite-les dans le même commit avec le suffixe `; update docs`.
Exemple : `feat(entities): add JPA entities and enums; update DB_MODEL and ARCHI`

### 9. PR (dernier ticket de la branche seulement)
Si le ticket indique `[x] This is the last ticket on feature/xxx` :
- Invoque `/review-code` sur toute la branche
- Invoque `/create-pr` → push + PR vers `dev`

---

## Marquer le ticket comme Done

Dans `docs/tickets/TICKET-XXX.md` :
```
# Avant
[ ] To do   [ ] In progress   [ ] Done

# Après
[ ] To do   [ ] In progress   [x] Done
```

Dans `docs/TICKETS.md`, change `À faire` en `Done` sur la ligne du ticket.

---

## Rapport de fin de ticket

Affiche ce rapport et **arrête-toi** :

```
✅ TICKET-XXX — [titre] — Done
   Tests : X passent / X total
   Fichiers créés : [liste]
   Docs mises à jour : [liste ou "aucune"]
   Commit : [hash court]

→ Prochain ticket backend : TICKET-YYY — [titre]
  Pour continuer : relance /backend-agent
```

Ne passe **pas** automatiquement au ticket suivant.
L'agent fait un ticket, s'arrête, affiche le rapport.
Charlotte relance `/backend-agent` quand elle est prête.

---

## Règles absolues

1. **RED avant GREEN** — jamais de code avant les tests
2. **Ne jamais toucher `front/`** — hors périmètre
3. **Lire `docs/tech.md`** avant de créer un endpoint — pas d'endpoint hors contrat sans mettre tech.md à jour
4. **Appliquer les ajustements de la mémoire projet** sur les entités concernées
5. **JWT cookie HttpOnly** — voir `back/CLAUDE.md` pour le code exact du filter
6. **Swagger sur chaque endpoint** : `@Operation` + `@ApiResponse`
7. **Lombok + MapStruct** pour les entités et mappers
8. **Jamais de secret dans un commit** (pas de `.env`, pas de clé en dur)

---

## En cas de blocage

Si un test reste RED après 3 tentatives, ou si une dépendance ticket est manquante :
1. Explique précisément le problème
2. Montre l'erreur complète (`mvn test` output)
3. Propose 2-3 solutions avec leurs trade-offs
4. Attends la réponse de Charlotte

---

## Ordre des tickets backend

```
feature/setup    → 001, 002, 003, 004, 005
feature/auth     → 007, 008
feature/users    → 012, 013
feature/formations → 015, 016
feature/documents  → 019
feature/messagerie → 022, 023
feature/notifications → 026, 027
```

Tickets ignorés (frontend/DevOps) :
006, 009, 010, 011, 014, 017, 018, 020, 021, 024, 025, 028, 029, 030, 031, 032
