# TICKET-049 — Frontend — Gestion des catégories (liste, éditer, activer/désactiver)

## Story
[US-017] — Gérer les catégories de formation

## Description
Page dédiée (Super Admin uniquement) listant toutes les catégories, actives et désactivées, avec
leur couleur. Permet d'éditer le nom/la couleur d'une catégorie (ex : faute de frappe) et de
l'activer/la désactiver. **Pas de bouton de suppression.**

## Repo
[x] front/   [ ] back   [ ] both

## Files to create or modify
- `src/pages/categories/CategoriesPage.jsx` — liste (pastille couleur, nom, statut actif/inactif),
  accessible uniquement au Super Admin (route protégée par rôle)
- `src/components/categories/EditCategoryModal.jsx` — édition nom + couleur d'une catégorie existante
- `src/components/categories/CategoryStatusToggle.jsx` — bouton activer/désactiver avec confirmation
- `src/services/categoryService.js` — ajouter `updateCategory(id, data)`, `activateCategory(id)`,
  `deactivateCategory(id)`
- `src/mocks/handlers.js` — MSW handlers `PUT /api/categories/:id`, `PATCH /api/categories/:id/activate`,
  `PATCH /api/categories/:id/deactivate`

## Acceptance criteria
- [ ] `CategoriesPage` liste toutes les catégories (actives ET désactivées), avec pastille de couleur
- [ ] Route accessible uniquement au rôle SUPER_ADMIN (redirection sinon)
- [ ] Clic "Éditer" → `EditCategoryModal` pré-remplie, submit → `PUT /api/categories/{id}`
- [ ] Nom déjà pris par une autre catégorie (409) → message d'erreur, modale reste ouverte
- [ ] Toggle sur une catégorie active → confirmation puis `PATCH .../deactivate`, statut mis à jour
      dans la liste sans recharger la page
- [ ] Toggle sur une catégorie désactivée → `PATCH .../activate`, statut mis à jour
- [ ] Aucun bouton ou action de suppression n'est présent sur la page

## Branch
`feature/formations`
- [ ] Create: `git checkout -b feature/formations`
- [x] Switch to existing: `git checkout feature/formations`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 : `CategoriesPage` rend actives et désactivées, avec la bonne pastille de couleur
- [ ] Test 2 : accès à `/categories` avec un rôle non-SUPER_ADMIN → redirection
- [ ] Test 3 : `EditCategoryModal` submit valide, MSW 200 → liste mise à jour avec le nouveau nom/couleur
- [ ] Test 4 : `EditCategoryModal` submit avec MSW 409 → message d'erreur affiché
- [ ] Test 5 : `CategoryStatusToggle` sur catégorie active → confirmation puis appel `deactivate`,
      statut passe à "Désactivée" dans la liste
- [ ] Test 6 : aucun élément avec le texte/rôle "Supprimer" n'est présent sur la page

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(categories): add category management page with edit and toggle`

## PR (only on last ticket of this branch)
- [ ] This is NOT necessarily the last ticket on `feature/formations` — check TICKET-022 to 025/048
      status before opening a PR

## Skills to invoke
- [x] /react-mui
- [x] /react-vite-best-practices

## Depends on
- TICKET-047 — endpoints `/api/categories` disponibles

## Estimated time
2h

## Status
[ ] To do   [ ] In progress   [ ] Done
