# User Stories — Portail de Formation ADAC

---

## US-001 — Connexion

**Story** : En tant qu'utilisateur (Super Admin, Formateur ou Stagiaire), je veux me connecter avec mon email et mon mot de passe pour accéder à mon espace personnel.

**Critères d'acceptation :**
- [ ] AC-01 : Le formulaire contient les champs Email et Mot de passe, un bouton "Commencer la session"
- [ ] AC-02 : En cas de succès, un cookie HttpOnly `jwt` est posé et l'utilisateur est redirigé vers son tableau de bord selon son rôle
- [ ] AC-03 : Si les identifiants sont invalides → message d'erreur "Identifiants invalides"
- [ ] AC-04 : Si le compte n'est pas encore activé → message d'erreur "Compte non activé, consultez vos emails"
- [ ] AC-05 : Le lien "Mot de passe oublié / Activer mon compte" est présent sous le formulaire
- [ ] AC-06 : Le texte "Pas de compte ? Contactez votre administrateur" est affiché (pas de lien d'inscription)

**Taille** : S
**Dépend de** : —

---

## US-002 — Activation de compte

**Story** : En tant que nouvel utilisateur (Formateur ou Stagiaire), je veux activer mon compte via un lien reçu par email pour créer mon mot de passe et accéder à la plateforme.

**Critères d'acceptation :**
- [ ] AC-01 : À la création du compte par le Super Admin, un email est envoyé automatiquement avec un code à 6 chiffres et un lien valide 30 min
- [ ] AC-02 : La page d'activation affiche les champs : code de vérification, nouveau mot de passe, confirmation du mot de passe
- [ ] AC-03 : Le mot de passe doit respecter les critères de sécurité (min 8 caractères, majuscule, chiffre) — validé côté client et serveur
- [ ] AC-04 : Si le code est expiré ou invalide → message d'erreur + bouton "Renvoyer un code"
- [ ] AC-05 : Maximum 3 envois de code par utilisateur sur 15 minutes → erreur 429 si dépassé
- [ ] AC-06 : En cas de succès → `isActive = true` + redirection vers la page de connexion avec bandeau "Compte activé avec succès"
- [ ] AC-07 : Un token déjà utilisé ne peut pas être réutilisé

**Taille** : M
**Dépend de** : US-001

---

## US-003 — Mot de passe oublié

**Story** : En tant qu'utilisateur, je veux réinitialiser mon mot de passe via un code reçu par email pour récupérer l'accès à mon compte.

**Critères d'acceptation :**
- [ ] AC-01 : La page "Mot de passe oublié" demande l'adresse email
- [ ] AC-02 : Même réponse affichée que l'email soit connu ou non (sécurité anti-énumération)
- [ ] AC-03 : Si l'email est connu → email envoyé avec code 6 chiffres valide 30 min (limite : 3/15 min)
- [ ] AC-04 : Le formulaire de réinitialisation : code + nouveau MDP + confirmation
- [ ] AC-05 : Mêmes validations que l'activation (force du MDP, code valide/expiré)
- [ ] AC-06 : En cas de succès → redirection vers connexion avec bandeau "Mot de passe mis à jour"

**Taille** : S
**Dépend de** : US-001, US-002

---

## US-004 — Créer et gérer une formation

**Story** : En tant que Super Admin, je veux créer, modifier et archiver des formations pour organiser le catalogue de l'ADAC.

**Critères d'acceptation :**
- [ ] AC-01 : Le formulaire de création contient : intitulé (obligatoire), date début/fin (obligatoires), catégorie (obligatoire — bouton de sélection + bouton "Créer nouvelle catégorie"), formateur (liste déroulante — actifs uniquement, optionnel), modalité visio/présentiel/mixte (obligatoire), documents drag & drop (optionnel)
- [ ] AC-02 : Si aucun formateur sélectionné → le Super Admin est assigné automatiquement comme formateur
- [ ] AC-03 : La liste des formations affiche les formations actives et archivées, avec filtres (dont filtre par catégorie)
- [ ] AC-04 : Un Super Admin peut modifier tous les champs d'une formation active, y compris sa catégorie
- [ ] AC-05 : Archiver une formation passe son statut à `ARCHIVED` — elle devient lecture seule
- [ ] AC-06 : Une formation archivée ne peut pas être modifiée ni désarchivée
- [ ] AC-07 : Le Formateur peut voir toutes les formations (filtre par défaut : ses formations) en lecture seule, avec filtre par catégorie

**Taille** : L
**Dépend de** : US-001, US-009, US-017

---

## US-017 — Gérer les catégories de formation

**Story** : En tant que Super Admin, je veux créer, modifier et activer/désactiver des catégories pour classer les formations sans jamais casser l'historique.

**Critères d'acceptation :**
- [ ] AC-01 : Créer une catégorie : nom (obligatoire, unique) + couleur au format `#RRGGBB` (obligatoire), possible depuis une page dédiée ou directement depuis le formulaire de création de formation ("Créer nouvelle catégorie")
- [ ] AC-02 : Modifier le nom et/ou la couleur d'une catégorie existante (ex : faute de frappe)
- [ ] AC-03 : Désactiver une catégorie — disparaît du sélecteur de création de formation, mais reste affichée telle quelle sur les formations (actives ou archivées) qui la référencent déjà
- [ ] AC-04 : Réactiver une catégorie désactivée
- [ ] AC-05 : Aucune suppression possible — pas de bouton ni d'endpoint de suppression
- [ ] AC-06 : Page de gestion des catégories accessible au Super Admin, listant toutes les catégories (actives et désactivées) avec leur couleur

**Taille** : M
**Dépend de** : US-001

---

## US-005 — Importer une formation via Excel

**Story** : En tant que Super Admin, je veux importer une formation depuis un fichier Excel pour éviter la saisie manuelle et gagner du temps.

**Critères d'acceptation :**
- [ ] AC-01 : Un bouton "Importer un fichier Excel" est disponible sur la liste des formations
- [ ] AC-02 : Seuls les fichiers `.xlsx` sont acceptés → message d'erreur si autre format
- [ ] AC-03 : En cas d'import réussi → formation(s) créée(s) et affichée(s) dans la liste
- [ ] AC-04 : En cas d'erreur de format dans le fichier → message explicite indiquant la ligne/colonne problématique
- [ ] AC-05 : Les colonnes attendues sont documentées (template fourni ou affiché)

**Taille** : M
**Dépend de** : US-004

---

## US-006 — Inscrire des stagiaires à une formation

**Story** : En tant que Super Admin, je veux inscrire des stagiaires à une formation pour les associer à leur session.

**Critères d'acceptation :**
- [ ] AC-01 : Depuis le détail d'une formation, un bouton "Inscrire un stagiaire" permet de choisir dans la liste des stagiaires actifs
- [ ] AC-02 : Un stagiaire ne peut être inscrit qu'une seule fois à une même formation → erreur si doublon
- [ ] AC-03 : La liste des stagiaires inscrits est visible depuis le détail de la formation
- [ ] AC-04 : Un stagiaire peut être désinscrit par le Super Admin
- [ ] AC-05 : Le nombre d'inscrits est affiché sur la carte de la formation

**Taille** : S
**Dépend de** : US-004, US-010

---

## US-007 — Gérer les comptes Formateurs

**Story** : En tant que Super Admin, je veux créer et gérer les comptes des formateurs pour leur donner accès à la plateforme.

**Critères d'acceptation :**
- [ ] AC-01 : Le formulaire de création contient : nom, prénom, email (tous obligatoires)
- [ ] AC-02 : À la création → email d'activation envoyé automatiquement au formateur
- [ ] AC-03 : La liste affiche les formateurs actifs et suspendus
- [ ] AC-04 : Un formateur peut être suspendu (`isActive = false`) — il ne peut plus se connecter
- [ ] AC-05 : Un formateur suspendu peut être réactivé
- [ ] AC-06 : Le profil du formateur affiche un bouton "Envoyer un message" → redirige vers la messagerie
- [ ] AC-07 : Si l'email est déjà utilisé → message d'erreur 409

**Taille** : M
**Dépend de** : US-001, US-002

---

## US-008 — Gérer les comptes Stagiaires

**Story** : En tant que Super Admin, je veux créer et gérer les comptes des stagiaires pour leur donner accès à leurs formations.

**Critères d'acceptation :**
- [ ] AC-01 : Le formulaire de création contient : nom, prénom, email (obligatoires) + au moins une formation (obligatoire)
- [ ] AC-02 : À la création → email d'activation envoyé automatiquement au stagiaire
- [ ] AC-03 : La liste du Super Admin affiche les stagiaires actifs ET désactivés
- [ ] AC-04 : Les Formateurs ne voient que les stagiaires actifs
- [ ] AC-05 : Un stagiaire peut être désactivé (`isActive = false`) → il ne peut plus se connecter
- [ ] AC-06 : Un stagiaire désactivé peut être réactivé
- [ ] AC-07 : Le profil du stagiaire affiche un bouton "Envoyer un message" → redirige vers la messagerie
- [ ] AC-08 : L'historique du stagiaire est conservé même après désactivation

**Taille** : M
**Dépend de** : US-001, US-002, US-004

---

## US-009 — Déposer des documents sur une formation

**Story** : En tant que Super Admin ou Formateur, je veux déposer des documents sur une formation pour les rendre accessibles à tous les stagiaires inscrits.

**Critères d'acceptation :**
- [ ] AC-01 : Depuis le détail d'une formation, un drag & drop permet d'uploader des fichiers
- [ ] AC-02 : Les types de fichiers autorisés et la taille maximale sont vérifiés côté client et serveur
- [ ] AC-03 : Le fichier est stocké sur Supabase Storage — l'URL est sauvegardée en base
- [ ] AC-04 : Le document uploadé est immédiatement visible par tous les stagiaires inscrits à cette formation
- [ ] AC-05 : Le Formateur peut ajouter des documents uniquement sur ses formations (erreur 403 sinon)
- [ ] AC-06 : Le Super Admin peut supprimer un document ; le Formateur peut supprimer ses propres uploads
- [ ] AC-07 : Une notification est envoyée aux stagiaires concernés lors d'un nouvel upload

**Taille** : M
**Dépend de** : US-004, US-006

---

## US-010 — Déposer un document ciblé pour un stagiaire

**Story** : En tant que Super Admin, je veux déposer un document destiné à un stagiaire spécifique pour lui transmettre des informations personnalisées.

**Critères d'acceptation :**
- [ ] AC-01 : Depuis le profil d'un stagiaire ou le détail d'une formation, le Super Admin peut uploader un document ciblé
- [ ] AC-02 : Ce document est visible uniquement par le stagiaire concerné (lié à son inscription)
- [ ] AC-03 : Le stagiaire reçoit une notification lors de la réception d'un document ciblé
- [ ] AC-04 : Le document n'est pas visible par les autres stagiaires ni par les Formateurs

**Taille** : S
**Dépend de** : US-009

---

## US-011 — Consulter et télécharger des documents (Stagiaire)

**Story** : En tant que Stagiaire, je veux consulter et télécharger les documents de mes formations pour accéder aux ressources pédagogiques.

**Critères d'acceptation :**
- [ ] AC-01 : Depuis le détail d'une formation, le stagiaire voit la liste de ses documents (formation + ciblés)
- [ ] AC-02 : Un clic sur un document déclenche le téléchargement
- [ ] AC-03 : Le stagiaire ne voit que les documents de ses formations et ses documents personnels
- [ ] AC-04 : Si aucun document n'est disponible → message "Aucun document disponible pour le moment"

**Taille** : S
**Dépend de** : US-009, US-010

---

## US-012 — Déposer ses propres documents (Stagiaire)

**Story** : En tant que Stagiaire, je veux déposer mes documents requis depuis mon profil pour répondre aux demandes de l'administration.

**Critères d'acceptation :**
- [ ] AC-01 : Depuis "Mon profil", une section "Mes documents" permet le drag & drop par formation
- [ ] AC-02 : Le fichier est uploadé sur Supabase et lié à l'inscription du stagiaire
- [ ] AC-03 : Les types et tailles de fichiers sont validés
- [ ] AC-04 : L'upload réussi est confirmé par un message de succès
- [ ] AC-05 : Le document déposé est visible par le Super Admin

**Taille** : S
**Dépend de** : US-006, US-011

---

## US-013 — Messagerie individuelle

**Story** : En tant qu'utilisateur, je veux envoyer et recevoir des messages individuels pour communiquer directement avec un autre utilisateur de la plateforme.

**Critères d'acceptation :**
- [ ] AC-01 : L'interface de messagerie affiche la liste des conversations triées par date (plus récente en premier)
- [ ] AC-02 : Un Super Admin peut écrire à n'importe quel utilisateur
- [ ] AC-03 : Un Formateur peut écrire au Super Admin, à tout formateur actif et tout stagiaire actif
- [ ] AC-04 : Un Stagiaire peut écrire au Super Admin et à tout formateur (filtre par défaut : formateur de ses formations)
- [ ] AC-05 : Le bouton "Envoyer un message" sur les profils stagiaire/formateur ouvre directement la conversation
- [ ] AC-06 : Les messages non lus sont mis en évidence dans la liste des conversations
- [ ] AC-07 : L'envoi d'un message déclenche une notification pour le destinataire

**Taille** : L
**Dépend de** : US-001

---

## US-014 — Messagerie groupée

**Story** : En tant que Super Admin ou Formateur, je veux envoyer un message à un groupe de stagiaires pour communiquer efficacement avec plusieurs personnes à la fois.

**Critères d'acceptation :**
- [ ] AC-01 : Le Super Admin peut filtrer les destinataires par : formation / documents manquants / sélection libre
- [ ] AC-02 : Le Formateur peut filtrer par formation → envoyer à tous les membres de cette formation
- [ ] AC-03 : L'aperçu des destinataires est affiché avant l'envoi
- [ ] AC-04 : Chaque destinataire reçoit une notification individuelle
- [ ] AC-05 : Le message groupé est visible dans la messagerie de chaque destinataire

**Taille** : M
**Dépend de** : US-013

---

## US-015 — Notifications in-app

**Story** : En tant qu'utilisateur, je veux être notifié des événements qui me concernent sans avoir à rafraîchir la page, pour ne rien manquer d'important.

**Critères d'acceptation :**
- [ ] AC-01 : Une cloche dans l'en-tête affiche le nombre de notifications non lues (badge)
- [ ] AC-02 : Cliquer la cloche ouvre un panneau affichant uniquement les notifications non lues
- [ ] AC-03 : Cliquer une notification → navigation vers l'élément concerné + marquage comme lue + disparition de la cloche
- [ ] AC-04 : Chaque notification dans la cloche a un bouton de suppression (suppression de la cloche uniquement)
- [ ] AC-05 : La page "Notifications" (plein écran) affiche l'historique complet — non supprimable, filtrable par date / lues / non lues
- [ ] AC-06 : Types de notifications : nouveau message, document uploadé, mise à jour de formation
- [ ] AC-07 : Le frontend interroge `GET /api/notifications/unread` par polling toutes les 30-60s — pas de WebSocket/SSE (mécanisme volontairement simple, cohérent avec `INFRASTRUCTURE.md`)

**Taille** : M
**Dépend de** : US-013, US-009

---

## US-016 — Préférences de notification email

**Story** : En tant qu'utilisateur, je veux activer ou désactiver les notifications par email pour contrôler les emails que je reçois de la plateforme.

**Critères d'acceptation :**
- [ ] AC-01 : Dans "Mon profil", un toggle "Notifications par email" est affiché (activé par défaut)
- [ ] AC-02 : Si activé → un email est envoyé à chaque nouvelle notification in-app
- [ ] AC-03 : Si désactivé → aucun email envoyé, les notifications in-app fonctionnent toujours
- [ ] AC-04 : Le changement de préférence est sauvegardé immédiatement
- [ ] AC-05 : Les emails transactionnels (activation, reset MDP) sont toujours envoyés indépendamment de ce toggle

**Taille** : S
**Dépend de** : US-015

---

## Récapitulatif

| ID | Titre | Persona | Taille | Dépend de |
|---|---|---|---|---|
| US-001 | Connexion | Tous | S | — |
| US-002 | Activation de compte | Tous | M | US-001 |
| US-003 | Mot de passe oublié | Tous | S | US-001, US-002 |
| US-004 | Créer et gérer une formation | Super Admin | L | US-001, US-009, US-017 |
| US-005 | Importer une formation via Excel | Super Admin | M | US-004 |
| US-006 | Inscrire des stagiaires | Super Admin | S | US-004, US-010 |
| US-007 | Gérer les comptes Formateurs | Super Admin | M | US-001, US-002 |
| US-008 | Gérer les comptes Stagiaires | Super Admin | M | US-001, US-002, US-004 |
| US-009 | Déposer des documents (formation) | Super Admin, Formateur | M | US-004, US-006 |
| US-010 | Déposer un document ciblé (stagiaire) | Super Admin | S | US-009 |
| US-011 | Consulter et télécharger des documents | Stagiaire | S | US-009, US-010 |
| US-012 | Déposer ses propres documents | Stagiaire | S | US-006, US-011 |
| US-013 | Messagerie individuelle | Tous | L | US-001 |
| US-014 | Messagerie groupée | Super Admin, Formateur | M | US-013 |
| US-015 | Notifications in-app | Tous | M | US-013, US-009 |
| US-016 | Préférences notification email | Tous | S | US-015 |
| US-017 | Gérer les catégories de formation | Super Admin | M | US-001 |

_Généré le 2026-08-21 avec /new-project_
