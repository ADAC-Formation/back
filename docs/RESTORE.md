# Restauration de la base de données — Portail ADAC

Procédure de restauration à partir d'un dump créé par `infra/backup.sh` (TICKET-040).
Une sauvegarde qui n'a jamais été restaurée n'est pas considérée comme fiable — cette procédure
doit être testée au moins une fois sur un environnement de test avant d'être considérée comme
opérationnelle (voir critère d'acceptation de `docs/tickets/TICKET-040.md`).

> **Le bucket `adac-backups` doit être privé** (pas de policy `public`, pas de lecture anonyme) —
> il contient des dumps complets de la base, PII des stagiaires incluses. Le dump lui-même n'est
> **pas chiffré côté client** (seulement le chiffrement au repos fourni par Supabase) ; le
> chiffrement applicatif (ex. `age`) nécessiterait une gestion de clé hors du périmètre de ce
> ticket — voir le TODO dans `infra/backup.sh`. Ne jamais rendre ce bucket public.

Le token utilisé ci-dessous (`SUPABASE_KEY`) est la clé service-role — évitez de la passer en
argument `curl -H` sur une machine partagée (visible via `/proc/<pid>/cmdline` par tout autre
compte local) ; sur le VPS, préférez l'exporter dans le shell courant (`export SUPABASE_KEY=...`,
jamais dans l'historique) ou source-z uniquement les variables depuis `.env` comme le fait
`infra/backup.sh`.

## Récupérer un dump depuis Supabase Storage

Lister les dumps disponibles dans le bucket `adac-backups` :

```bash
curl -s -X POST "${SUPABASE_URL}/storage/v1/object/list/adac-backups" \
  -H "Authorization: Bearer ${SUPABASE_KEY}" \
  -H "apikey: ${SUPABASE_KEY}" \
  -H "Content-Type: application/json" \
  --data '{"limit": 1000, "prefix": ""}' | jq -r '.[].name'
```

Télécharger le dump choisi :

```bash
curl -s -X GET "${SUPABASE_URL}/storage/v1/object/adac-backups/<nom_du_dump>.sql.gz" \
  -H "Authorization: Bearer ${SUPABASE_KEY}" \
  -H "apikey: ${SUPABASE_KEY}" \
  -o restore.sql.gz
```

## Restaurer sur un environnement de test (jamais directement sur la prod)

1. Démarrer un container Postgres vierge, isolé de la production. Le port est mappé sur
   `127.0.0.1` uniquement — **jamais** `-p 5433:5432` seul, qui publie sur toutes les interfaces
   et exposerait un dump complet de PII à quiconque atteint le VPS (revue, BLOCKING). Le mot de
   passe est généré aléatoirement à chaque exécution, pas codé en dur dans ce document :

   ```bash
   RESTORE_TEST_PASSWORD="$(openssl rand -hex 16)"
   docker run --rm -d --name adac-restore-test \
     -e POSTGRES_DB=adac_portail \
     -e POSTGRES_USER=adac_user \
     -e POSTGRES_PASSWORD="${RESTORE_TEST_PASSWORD}" \
     -p 127.0.0.1:5433:5432 \
     postgres:16
   # Laisser le temps au container de finir son initialisation avant de s'y connecter :
   until PGPASSWORD="${RESTORE_TEST_PASSWORD}" psql -h 127.0.0.1 -p 5433 -U adac_user -d adac_portail -c '\q' 2>/dev/null; do sleep 1; done
   ```

2. Décompresser et restaurer le dump. `-v ON_ERROR_STOP=1` fait échouer immédiatement le script
   (et remonter un exit code non nul) à la première erreur SQL — sans cette option, `psql`
   continue après une erreur et rend la main avec un exit code 0 même si la restauration s'est
   arrêtée à moitié, ce qui a longtemps donné une fausse impression de succès (revue, BLOCKING) :

   ```bash
   gunzip -c restore.sql.gz > restore.sql
   PGPASSWORD="${RESTORE_TEST_PASSWORD}" psql -h 127.0.0.1 -p 5433 -U adac_user -d adac_portail \
     -v ON_ERROR_STOP=1 -f restore.sql
   ```

3. Vérifier que les données correspondent à l'original (compter quelques tables clés) :

   ```bash
   PGPASSWORD="${RESTORE_TEST_PASSWORD}" psql -h 127.0.0.1 -p 5433 -U adac_user -d adac_portail \
     -c "SELECT count(*) FROM users;" \
     -c "SELECT count(*) FROM formations;"
   ```

4. Une fois vérifié, arrêter le container de test (son volume n'est pas persisté — `--rm` suffit
   à tout nettoyer, y compris le mot de passe généré) :

   ```bash
   docker stop adac-restore-test
   rm -f restore.sql restore.sql.gz
   ```

## Restaurer réellement en production (incident réel uniquement)

`pg_dump` sans `--clean`/`--if-exists` ne fait que des `INSERT`/`COPY` — le rejouer sur une base
non vide échoue sur les contraintes d'unicité (PK, colonnes `unique`) déjà présentes, ou pire,
s'arrête au milieu en laissant une base à moitié restaurée si l'erreur n'est pas détectée (revue,
BLOCKING). Le schéma est donc explicitement réinitialisé avant de rejouer le dump, et
`ON_ERROR_STOP=1` est utilisé ici aussi.

1. Mettre le backend en mode maintenance ou l'arrêter (`docker compose stop backend`) — éviter
   des écritures pendant la restauration.
2. Sauvegarder l'état actuel de la base avant d'écraser quoi que ce soit (même si elle est
   corrompue — pour pouvoir revenir en arrière si la restauration choisie était la mauvaise) :

   ```bash
   docker compose exec -T db pg_dump -U "${DB_USERNAME}" -d adac_portail | gzip > pre-restore-safety.sql.gz
   ```

3. Réinitialiser le schéma, puis restaurer le dump choisi dans le container `db` de production,
   en s'arrêtant à la première erreur :

   ```bash
   docker compose exec -T db psql -U "${DB_USERNAME}" -d adac_portail -v ON_ERROR_STOP=1 \
     -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
   gunzip -c restore.sql.gz | docker compose exec -T db psql -U "${DB_USERNAME}" -d adac_portail -v ON_ERROR_STOP=1
   ```

   Si cette seconde commande échoue (exit code non nul), **ne pas redémarrer le backend** — la
   base est dans un état partiel. Restaurer `pre-restore-safety.sql.gz` avec la même procédure
   avant toute autre action.

4. Redémarrer le backend (`docker compose start backend`) et vérifier `/actuator/health` puis un
   scénario applicatif de base (connexion, une liste de formations).

## Résultat du test de restauration (à compléter à chaque test réel)

| Date | Dump testé | Environnement | Résultat | Testé par |
|---|---|---|---|---|
| _(à compléter)_ | | | | |
