# TICKET-034 — Backend — Service email (JavaMailSender + templates)

## Story
[US-002] — Activation de compte
[US-003] — Mot de passe oublié
[US-016] — Préférences de notification email

## Description
Créer le service email avec templates HTML pour les emails transactionnels (activation, reset) et les notifications in-app. Le toggle `emailNotificationsEnabled` est respecté uniquement pour les notifications — pas pour les emails transactionnels.

## Repo
[ ] front/   [x] back   [ ] both

## Files to create or modify
- `service/EmailService.java` (interface) + `EmailServiceImpl.java` — `sendActivationEmail(user, code)`, `sendPasswordResetEmail(user, code)`, `sendNotificationEmail(user, message)` (respecte `emailNotificationsEnabled`)
- `utils/EmailTemplateBuilder.java` — construction HTML des emails (activation, reset, notification) avec les couleurs ADAC (`#cc3d34`, etc.)
- `config/MailConfig.java` — déjà créé en TICKET-007, vérifier que les profils dev/prod sont bien mappés

> **Décision validée avec Charlotte avant codage** : migration de `ActivationServiceImpl` vers
> `EmailService` incluse dans ce ticket (anticipée par `docs/ARCHI.md`, absente de la liste
> "Files to create or modify" ci-dessus à l'origine) — il enverrait sinon des emails texte brut
> via `MailSender` en parallèle des emails HTML du nouveau service.

## Acceptance criteria
- [x] `sendActivationEmail` : email avec code à 6 chiffres, template HTML ADAC (pas de lien
      d'activation — l'app ne fonctionne qu'avec le code saisi dans le formulaire, voir tech.md)
- [x] `sendPasswordResetEmail` : email avec code à 6 chiffres, template HTML ADAC
- [x] `sendNotificationEmail` : envoyé uniquement si `user.emailNotificationsEnabled = true` —
      implémenté et testé isolément ; **aucun appelant en production** (`NotificationServiceImpl`
      ne l'appelle pas encore, hors périmètre de ce ticket, voir TICKET-033/036)
- [x] Les emails transactionnels (activation, reset) sont toujours envoyés indépendamment du toggle
- [x] En profil `dev` → envoi via Mailtrap (déjà configuré TICKET-007, non retouché ici)
- [x] En profil `prod` → envoi via Brevo (déjà configuré TICKET-007, non retouché ici)
- [x] `from:` configuré depuis les variables d'env (`app.mail.from`, déjà en place, réutilisé tel quel)

### Ajouté en revue (branch-wide, 2 agents Opus : sécurité, backend+clean-code)
- [x] `EmailTemplateBuilder` échappait `prenom`/`content` avec `HtmlUtils.htmlEscape` avant
      interpolation HTML — latent aujourd'hui (`prenom` n'est réglable que par SUPER_ADMIN,
      `sendNotificationEmail` n'a pas d'appelant), mais nécessaire dès que TICKET-033 câblera un
      appelant : `content` porterait alors du texte libre d'un autre utilisateur dans un email
      envoyé depuis le domaine ADAC (lien de phishing, pixel de tracking)
- [x] Tests `EmailServiceImplTest` renforcés : vérifiaient seulement que `send()` était appelé,
      maintenant vérifient sujet/destinataire/from/Content-Type réels sur le `MimeMessage`
- [x] Test ajouté pour le wrap `MessagingException → MailPreparationException` (seule branche non
      testée, et celle dont dépend l'anti-oracle de `ActivationServiceImpl`)
- [x] `if/else` sur `TokenType` remplacé par un `switch` exhaustif — l'`else` implicite aurait
      silencieusement routé un futur 3ᵉ type vers le template de reset
- [x] Duplication du gabarit HTML entre `wrap()` et `buildNotificationEmail()` supprimée
      (`shell()` partagé)
- [x] Test ajouté prouvant que le code emailé correspond bien au hash stocké (`anyString()`
      acceptait n'importe quoi, y compris le hash lui-même)
- [x] Adresse email loggée via l'objet exception (`log.warn(msg, e)`) — contredisait la politique
      déjà énoncée dans ce fichier ("logs l'id, pas l'email") ; `e` déplacé en `DEBUG`
- [x] `ActivationServiceIntegrationTest` : `JavaMailSender` mocké renvoyait `null` sur
      `createMimeMessage()` — inoffensif aujourd'hui, aurait fait échouer confusément le premier
      test futur déclenchant un envoi
- [ ] **Déféré, signalé à Charlotte** : l'appel SMTP (`issueAndEmailCode`) reste dans la transaction
      `@Transactional` de `resendActivation`/`forgotPassword` — un relais SMTP dégradé retient une
      connexion DB ~15s (timeouts configurés), et un code est emailé avant que la ligne token ne
      soit commitée. Le code existant utilise déjà ce pattern `afterCommit` ailleurs
      (`UserServiceImpl.sendActivationCodeAfterCommit`, `MessageServiceImpl.notifyAfterCommit`) ;
      l'appliquer ici demanderait de déplacer la logique `catch (MailException)` de trois call
      sites vers l'intérieur du callback `afterCommit`, un remaniement plus large que le périmètre
      de ce ticket — pré-existant, pas une régression introduite ici.

## Branch
`feature/notifications`
- [ ] Create: `git checkout -b feature/notifications`
- [x] Switch to existing: `git checkout feature/notifications`

## Write tests first (TDD)
Before writing any implementation code:
- [x] Test 1 (`@ExtendWith(MockitoExtension)`): `sendNotificationEmail` avec `emailNotificationsEnabled = true` → `JavaMailSender.send()` appelé
- [x] Test 2 : `sendNotificationEmail` avec `emailNotificationsEnabled = false` → `JavaMailSender.send()` NON appelé
- [x] Test 3 : `sendActivationEmail` → `JavaMailSender.send()` toujours appelé (indépendant du toggle)
- [x] Test 4 : `EmailTemplateBuilder.buildActivationEmail(code)` → contient le code dans le HTML

Run tests → confirm RED. Then implement. Run tests → confirm GREEN.

## Pre-commit review
Once tests are GREEN, run `/review-code` on the files changed in this ticket.
Fix any blocking or critical issues before committing.

## Commit
Run `/commit` — it follows conventional commits format automatically.

Conventional commits format (always in English):
- `feat(email): add email service with HTML templates for activation, reset and notifications`

## PR (only on last ticket of this branch)
- [ ] This is NOT the last ticket on `feature/notifications` — see TICKET-036

## Skills to invoke
> Auto-populated by step-09. Do not edit manually.
- [x] /java-springboot → génère le controller, le service et la config Spring Boot
- [x] /spring-boot-test-patterns → patterns @WebMvcTest, @DataJpaTest, @ExtendWith(MockitoExtension) avant le code

## Depends on
- TICKET-007 — `MailConfig` bean et `JavaMailSender`
- TICKET-015 — `ActivationService` appelle ce service pour envoyer les codes

## Estimated time
3h

## Status
[ ] To do   [ ] In progress   [x] Done
