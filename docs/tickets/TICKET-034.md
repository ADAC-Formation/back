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

## Acceptance criteria
- [ ] `sendActivationEmail` : email avec code à 6 chiffres, lien d'activation, template HTML ADAC
- [ ] `sendPasswordResetEmail` : email avec code à 6 chiffres, template HTML ADAC
- [ ] `sendNotificationEmail` : envoyé uniquement si `user.emailNotificationsEnabled = true`
- [ ] Les emails transactionnels (activation, reset) sont toujours envoyés indépendamment du toggle
- [ ] En profil `dev` → envoi via Mailtrap (`MAIL_HOST=smtp.mailtrap.io`)
- [ ] En profil `prod` → envoi via Brevo (`MAIL_HOST=smtp-relay.brevo.com`)
- [ ] `from:` configuré depuis les variables d'env (pas en dur)

## Branch
`feature/notifications`
- [ ] Create: `git checkout -b feature/notifications`
- [x] Switch to existing: `git checkout feature/notifications`

## Write tests first (TDD)
Before writing any implementation code:
- [ ] Test 1 (`@ExtendWith(MockitoExtension)`): `sendNotificationEmail` avec `emailNotificationsEnabled = true` → `JavaMailSender.send()` appelé
- [ ] Test 2 : `sendNotificationEmail` avec `emailNotificationsEnabled = false` → `JavaMailSender.send()` NON appelé
- [ ] Test 3 : `sendActivationEmail` → `JavaMailSender.send()` toujours appelé (indépendant du toggle)
- [ ] Test 4 : `EmailTemplateBuilder.buildActivationEmail(code)` → contient le code dans le HTML

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
[ ] To do   [ ] In progress   [ ] Done
