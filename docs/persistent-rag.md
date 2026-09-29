# Persistent RAG Acceptance

## Environment

Production Compose adds PostgreSQL 16 / pgvector 0.8.6 alongside the existing MySQL. Set all required `.env` values, including `PGVECTOR_DATABASE`, `PGVECTOR_USERNAME`, `PGVECTOR_PASSWORD`, then run:

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

Only Nginx is published (`127.0.0.1:8088` by default). MySQL/PostgreSQL have no public ports. The host needs Docker/Compose, not Java/Node/databases. The backend's default schema initialization enables extensions and creates/validates `public.vector_store` plus a cosine HNSW index. Initial schema setup requires database DDL/extension privileges.

Inspect the actual extension, dimension, and index without displaying credentials:

```bash
docker compose exec postgres-vector sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT extname, extversion FROM pg_extension WHERE extname IN ('"'"'vector'"'"', '"'"'hstore'"'"', '"'"'uuid-ossp'"'"');"'
docker compose exec postgres-vector sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\d public.vector_store"'
```

Default `text-embedding-3-small` uses 1536 dimensions. Upload/query use the same model; actual provider vectors are checked against `PGVECTOR_DIMENSIONS` before SQL. The official schema validator checks the existing column too. Switching embedding models requires re-embedding, even if dimensions match. Do not drop existing data just to resolve a mismatch.

## Real HTTP acceptance

Use Postman/Apifox or the frontend; store A/B JWTs privately and put them in `Authorization: Bearer <token>`. All endpoints below retain their existing DTOs.

1. GET `/api/health`: `database` and `vectorDatabase` both `UP`.
2. Register/login A and B with `/api/auth/register` and `/api/auth/login`.
3. A uploads two MD files using `/api/knowledge/upload` (multipart field `file`):
   - `a-project.md`: `CareerPilot Persistent RAG Test ABC123. Java project database is MySQL-A.`
   - `a-secret.md`: `My private vector secret is VECTOR-A-12345.`
4. B uploads one file: `Java project database is PostgreSQL-B. My private vector secret is VECTOR-B-98765.`
5. Status: A `documentCount=2`, B `documentCount=1`; `vectorStoreType=PgVectorStore`.
6. Each POST `/api/conversations` to create its own server ID, then send the same question to `/api/chat` and check each only sees their own database:

```json
{"conversationId":"<own-created-ID>","message":"我的Java project database是什么？"}
```

Then ask each: `我的private vector secret是什么？` — A must only see VECTOR-A-12345, B only VECTOR-B-98765.

7. A asks `我的知识库有什么ABC123内容？`. Record counts/answer. Restart only backend:

```bash
docker compose restart backend
```

Wait for health, repeat with a **new conversation created through POST `/api/conversations`** to prove retrieval does not depend on old chat memory. Counts and ABC123 knowledge must remain.

8. Restart PostgreSQL, wait for health, repeat retrieval:

```bash
docker compose restart postgres-vector
```

9. Verify volume persistence:

```bash
docker compose down
docker compose up -d
```

Login again and repeat retrieval. **Do not use `down -v`**, which deletes both `mysql_data` and `pgvector_data`.

10. A DELETE `/api/knowledge`: A counts zero and retrieval has no A facts. B counts/results remain unchanged. No table truncate is used.
11. For each successful chat, GET `/api/observability/traces/{requestId}`: RAG_SEARCH records duration, topK=4 and returned chunk count, without document text. B cannot fetch A's requestId.
12. Regression: `/api/auth/me`, `/api/jobs`, `/api/applications`, `/api/observability/summary` still query the original MySQL data.
13. Stop only PostgreSQL during a chat test, confirm health HTTP 503/vectorDatabase DOWN and a safe retrieval error; restore it immediately. Current chat executes RAG for every prompt and does not silently downgrade to ungrounded answers.

## Optional database integration tests

Run against a **dedicated test database**, never production. Set `PGVECTOR_HOST`, `PGVECTOR_PORT`, `PGVECTOR_DATABASE`, `PGVECTOR_USERNAME`, `PGVECTOR_PASSWORD`, and `PGVECTOR_DIMENSIONS` in the terminal. On Windows use `$env:VARIABLE='value'`; do not share/print real credentials.

```bash
mvn -Pvector-integration-test test
```

The opt-in `PgVectorStoreIT` uses the real official PgVectorStore and PostgreSQL with Mock Embedding. It initializes/validates the official table, tests user filters with identical keywords, status after replacing Store/pool instances, clearing only A, extension/index presence, and a small 100/500/1000-chunk × 10-query benchmark. Generated test owners have random IDs and cleanup deletes only those owners. No DROP/TRUNCATE, paid model call, Docker or MySQL dependency.

This tests persistence across fresh connections, **not an actual PostgreSQL/backend process restart**; use the HTTP steps above for that. Benchmark results include JDBC/search overhead with constant Mock vectors, exclude network Embedding latency, and are only a Local Development Benchmark. The ordinary `mvn clean test` excludes `*IT` and remains database-free; the integration Profile fails if no test database is configured.

## Current validation status

An isolated Docker project has now passed real HTTP acceptance with PostgreSQL 16.15 / pgvector 0.8.6 and the explicit local keyword embedding profile: real vector writes, owner filtering, Backend/PostgreSQL/Compose restarts, status persistence, deletion isolation and MySQL RAG traces. Evidence is in `docs/local-rag-acceptance-results.json`; instructions are in `docs/local-test-embedding.md`. This is **not** a real OpenAI/semantic-model acceptance. Paid Embedding, provider Token Usage, semantic quality and the separate 100/500/1000-chunk benchmark remain unverified.

Old SimpleVectorStore data had no durable file; users must re-upload. No automatic vector migration is provided. Source files are not archived; maintain originals for re-embedding/backups. Back up PostgreSQL data independently from MySQL.
