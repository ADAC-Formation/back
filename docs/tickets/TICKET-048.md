# TICKET-048 — Frontend — Sélecteur de catégorie + création à la volée

## Story
[US-004] — Créer et gérer une formation
[US-017] — Gérer les catégories de formation

## Description
Dans le formulaire de création/modification de formation (`FormationForm`, TICKET-025), ajouter le
champ catégorie : bouton "Catégorie" ouvrant la liste des catégories actives (nom + pastille de
couleur), et bouton "Créer nouvelle catégorie" juste à côté ouvrant une petite modale (nom + color
picker hex `#RRGGBB`) sans quitter le formulaire — la catégorie créée est immédiatement sélectionnée.

Aussi : filtre par catégorie sur la liste des formations (TICKET-024), disponible pour Super Admin
et Formateur.

## Repo
[x] front/   [ ] back   [ ] both

## Files to create or modify
- `src/components/formations/CategoryPicker.jsx` — bouton + liste déroulante des catégories actives
  (pastille couleur + nom), utilisé dans `FormationForm`
- `src/components/formations/CreateCategoryModal.jsx` — modale nom + color picker hex, submit →
  `POST /api/categories`, sélectionne la nouvelle catégorie à la fermeture
- `src/components/formations/FormationForm.jsx` — intégrer `CategoryPicker` (champ obligatoire)
- `src/components/formations/CategoryFilter.jsx` — filtre par catégorie sur la liste des formations
- `src/services/categoryService.js` — `getCategories({ active })`, `createCategory(data)`
- `src/services/formationService.js` — `getFormations` accepte `categoryId`
- `src/mocks/handlers.js` — MSW handlers `GET /api/categories`, `POST /api/categories`

## Acceptance criteria
- [ ] `CategoryPicker` affiche uniquement les catégories actives (`?active=true`), avec pastille de
      la couleur `couleur` de chacune
- [ ] Champ catégorie obligatoire — submit du formulaire sans catégorie sélectionnée → erreur de
      validation, pas d'appel API
- [ ] Bouton "Créer nouvelle catégorie" ouvre `CreateCategoryModal` sans perdre les autres champs
      déjà saisis dans `FormationForm`
- [ ] `CreateCategoryModal` : nom (requis) + color picker retournant un hex `#RRGGBB` (requis)
- [ ] Création réussie → modale se ferme, nouvelle catégorie apparaît sélectionnée dans `CategoryPicker`
- [ ] Nom déjà utilisé (409 backend) → message d'erreur affiché dans la modale, modale reste ouverte
- [ ] `CategoryFilter` sur la liste des formations → filtre les formations par `categoryId`, visible
      pour Super Admin et Formateur

## Branch
`feature/formations`
- [ ] Create: `git checkout -b feature/formations`
- [x] Switch to existing: `git checkout feature/formations`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `CategoryPicker` rend uniquement les catégories actives retournées par MSW
- [ ] Test 2 : Submit `FormationForm` sans catégorie sélectionnée → message validation, pas d'appel API
- [ ] Test 3 : Clic "Créer nouvelle catégorie" → modale s'ouvre, champs de `FormationForm` déjà
      saisis restent intacts
- [ ] Test 4 : Submit `CreateCategoryModal` valide, MSW 201 → modale fermée, catégorie sélectionnée
      dans `CategoryPicker`
- [ ] Test 5 : Submit `CreateCategoryModal` avec MSW 409 → message d'erreur affiché, modale reste ouverte
- [ ] Test 6 : `CategoryFilter` change la query passée à `getFormations`

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(formations): add category picker, on-the-fly creation and filter`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/formations`

## Skills to invoke
- [x] /react-mui
- [x] /react-vite-best-practices

## Depends on
- TICKET-047 — endpoints `/api/categories` disponibles
- TICKET-025 — `FormationForm` existant à étendre

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
