# Development and deployment

## Requirements

Docker workflow: Git, Docker Engine/Desktop and Compose with `!override` support. No host JDK/Maven/Node/database installation is needed. Local development: JDK 21 (CI and images), Maven 3.9, Node 24/npm, MySQL 8.4 and PostgreSQL 16 with pgvector 0.8.6.

For a development laptop, start with 4 CPU cores and 8 GB assigned to Docker Desktop, with free disk space for image builds and both databases. This is a development recommendation, not a measured production capacity claim.

## Production configuration

Copy `.env.example` to `.env`. Fill random database passwords, JWT secret (at least 32 bytes), genuine OPENAI_API_KEY and the desired model names. Keep `MYSQL_DATABASE=career_pilot_ai` to match the SQL initialization. Do not commit `.env`. Production never falls back to local test models.

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

Default URL: http://127.0.0.1:8088. Register your own account; no demo login is automatically created. To publish on Linux HTTP port 80 set `WEB_HOST=0.0.0.0` and `WEB_PORT=80`. Only Nginx is published; never open MySQL 3306, PostgreSQL 5432 or backend 8080 to the public internet. Use Docker's official Ubuntu/Debian installation instructions for Docker Engine and Compose Plugin, clone the project and run the commands above. Do not install host Java/Maven/Node/MySQL for this workflow.

HTTPS is not preconfigured. With a domain, terminate TLS at Nginx or an outer reverse proxy using a Let's Encrypt/Certbot certificate and open 443. Keep HTTP usable during configuration. Production should have backups and restricted server access.

## Explicit local test demo (no provider key)

After filling DB/JWT secrets in `.env`, use a **separate project** so synthetic vectors never mix with production embeddings:

```bash
docker compose --env-file .env -p career-pilot-local-demo -f docker-compose.yml -f docker-compose.local-test.yml up -d --build
```

Visit http://127.0.0.1:18088/register. Set `LOCAL_TEST_WEB_PORT=18089` in `.env` if 18088 is occupied. The override explicitly enables `prod,local-test-embedding` plus its safety flag, clears the supplier key and isolates backend/database internet egress. The UI displays **LOCAL TEST MODE**. LocalTestEmbeddingModel is a deterministic 1536-dimensional keyword/hash fixture, not semantic embedding. LocalTestChatModel echoes/retrieves predictable fixture content and exercises memory/tool/stream infrastructure; it is not an LLM. No production fallback exists.

Use the same project/override options for logs, stop and restart commands. Do not apply the test override to an existing production vector database. Normal and synthetic embeddings are incompatible even with the same dimension.

## Database initialization and upgrades

New MySQL volumes execute `sql/schema.sql` once. PostgreSQL init enables the vector extension; official PgVectorStore initializes/validates its table/index. Existing volumes do not rerun initialization. Production Compose no longer auto-imports `sql/demo-data.sql`; that historical fictional dataset belongs to the blocked legacy owner and is not a production seed.

Back up first, stop the backend, then apply only missing migrations in order. Older databases need `migrate-v12.sql` then `migrate-v13.sql` once; these are not repeatable. Stages 15/16/17 have repeatable migrations. From stage 16, only `sql/migrate-v17.sql` is needed; it adds an application owner/id index and does not change rows. Use a MySQL administrator/client, or pipe the SQL to the MySQL container using its existing environment credentials. Never place passwords in shell arguments or command history. Existing legacy ownership is not reassigned automatically.

`docker compose down` preserves `mysql_data` and `pgvector_data`. **`down -v` deletes both databases**. Do not run it on valuable data. Changing an embedding model/dimension requires a new vector dataset or re-upload/re-embedding; no automatic old SimpleVectorStore migration exists.

## Local process development

Initialize `sql/schema.sql` in MySQL and `sql/pgvector-init.sql` in PostgreSQL. Export DB_URL, DB_USERNAME, DB_PASSWORD, PGVECTOR_HOST/PORT/DATABASE/USERNAME/PASSWORD, JWT_SECRET and OPENAI_API_KEY. Maven does not automatically load `.env`.

```bash
mvn spring-boot:run
cd frontend
npm ci
npm run dev
```

Backend: 127.0.0.1:8080; frontend: 127.0.0.1:5173. Vite proxies `/api`; browser production requests remain relative to Nginx. Dev CORS allows only the two localhost:5173 origins.

## Verification

```bash
mvn -B -ntp clean test
mvn -B -ntp clean package
cd frontend
npm ci
npm run build
cd ..
python scripts/test_pre_public_check.py
python scripts/pre-public-check.py --workspace
docker compose config --quiet
docker compose build
```

`npm run build` includes vue-tsc; there is no separate lint/type-check script. Unit tests use mocks/H2, not paid model APIs or live databases. Optional vector integration: `mvn -Pvector-integration-test test`, only with its configured local pgvector fixture. See [local acceptance setup](local-test-embedding.md) for the isolated real HTTP/DB scripts; these scripts intentionally use `.env.rag-acceptance` and project `career-pilot-rag-acceptance`, never the production project.

Before publishing, review scripts, SQL, JSON evidence and real screenshots for personal data. The read-only pre-public gate inspects tracked and nonignored untracked files; it never deletes or stages files. Avoid `git add -f` on local configuration. No License has been selected; choose one explicitly before inviting code reuse.
