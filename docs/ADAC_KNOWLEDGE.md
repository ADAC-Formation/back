# Connaissances ADAC partagées

Ce document remplace, pour les règles techniques du projet, la mémoire personnelle référencée par l'ancien `backend-agent`. Il ne contient que des informations propres à ADAC et doit être lu par Claude Code et Codex avant un ticket backend.

## Sources de vérité

- La pile backend, Lombok, MapStruct, JPA/Hibernate, Flyway, Swagger et les conventions de tests sont décrits dans `docs/STACK.md`, section `Backend (Charlotte)`.
- L'architecture des packages, la sécurité, les filtres, les variables d'environnement et Swagger sont décrits dans `docs/ARCHI.md`, sections `Backend — Java Spring Boot 3`, `Authentification`, `Variables d'environnement` et `Swagger`.
- Le contrat API et sa configuration sont décrits dans `docs/tech.md`, sections `Configuration`, `Authentification` et les ressources concernées.
- Le modèle relationnel reste `docs/DB_MODEL.mmd`. Une modification d'entité ou de migration doit être cohérente avec ce modèle, les migrations Flyway et les repositories concernés.

## JWT, cookie et CORS

Le JWT est transporté dans le cookie `jwt`. Sa création est centralisée dans `JwtCookieFactory` : `HttpOnly`, `SameSite=Strict`, `Path=/`, durée issue de `app.jwt.expiration`, et `Secure` issu de `app.jwt.cookie-secure` (`false` en développement, `true` en production). Les sources techniques sont `docs/ARCHI.md`, section `Authentification`, et `src/main/java/com/adac/portail/security/JwtCookieFactory.java`.

Ne pas confondre les deux filtres : `JwtAuthenticationFilter` traite la connexion, lit les identifiants JSON et émet le cookie ; `JwtAuthorizationFilter` lit ensuite le cookie JWT sur les requêtes protégées et alimente le contexte de sécurité. Toute évolution doit préserver cette séparation et le contrat exposé dans `docs/tech.md`.

La configuration CORS vient de `app.cors.allowed-origins` et doit rester cohérente avec l'usage du cookie. Vérifier la configuration réelle dans `src/main/resources/application.yml` et `SecurityConfig` avant de l'élargir.

## Environnement, messagerie et démarrage local

`application.yml` charge optionnellement `.env` et utilise notamment `JWT_SECRET`, `MAIL_FROM` et les propriétés de messagerie. `MAIL_FROM` alimente `app.mail.from` et possède la valeur locale par défaut `no-reply@adac.fr` ; ne pas le remplacer par une valeur propre à un autre environnement.

Le profil actif par défaut est `dev` (`SPRING_PROFILES_ACTIVE`). Le profil `dev` utilise Mailtrap avec des valeurs de développement ; le profil `prod` utilise la configuration Brevo et active le cookie sécurisé. Démarrer localement avec le profil `dev` et les variables nécessaires, sans committer de secret. Les emplacements de référence sont `src/main/resources/application.yml`, `application-dev.yml`, `application-prod.yml`, `docs/ARCHI.md`, section `Variables d'environnement`, et `docs/tech.md`, section `Configuration`.

Pour un démarrage local, PostgreSQL doit répondre sur `localhost:5432`, un fichier `.env` à la racine doit au minimum renseigner `DB_*` et `JWT_SECRET`, puis la commande est `mvn spring-boot:run`. Vérifier le démarrage dans les logs et Swagger UI sur `http://localhost:8080/swagger-ui.html`. Les commandes de vérification usuelles sont `mvn test` et `mvn test -Dtest=NomTest`. Les tests de controller, service et repository suivent respectivement `@WebMvcTest`, `@ExtendWith(MockitoExtension)` et `@DataJpaTest`, conformément à `docs/STACK.md`, section `Tests`, et à la checklist locale historique de `CLAUDE.md`.

## Entités, scheduler et conventions

Avant une évolution d'entité, vérifier les contraintes JPA, les migrations Flyway, les repositories et `docs/DB_MODEL.mmd`. Les ajustements ADAC connus incluent la cohérence des relations, les contraintes d'unicité et la suppression des dépendances dans l'ordre imposé par le modèle.

`TokenCleanupScheduler` est activé avec la planification Spring et supprime les jetons expirés. Le code actuel utilise `OffsetDateTime`; une ancienne mémoire mentionnait `LocalDateTime`. Ne pas modifier ce type pour aligner un exemple historique sans une décision fondée sur le modèle et le code en vigueur.

Les endpoints backend sont documentés avec springdoc : `@Operation` et `@ApiResponse`, avec Swagger UI à `/swagger-ui.html` lorsque le profil l'autorise. Préserver l'usage de Lombok et MapStruct conforme à `docs/STACK.md` et l'organisation des packages définie dans `docs/ARCHI.md`.

## Divergences à signaler, sans décision autonome

Les exemples historiques de `CLAUDE.md` servent de contexte Claude mais le contrat et le code actuels prévalent pour l'état observé. Une ancienne mémoire utilisait `LocalDateTime` pour le scheduler, alors que le code actuel utilise `OffsetDateTime`. De plus, la formulation CORS de `docs/STACK.md` doit être lue avec la règle détaillée de `docs/ARCHI.md` : HTTPS seul ne remplace pas les protections de cookie et de même site. Ces écarts doivent être signalés lorsqu'ils touchent un ticket ; ils n'autorisent ni un changement métier ni une modification de code sans validation.
