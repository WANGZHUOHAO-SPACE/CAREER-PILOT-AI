# Local RAG Integration Acceptance

This mode exercises real HTTP, JWT, MySQL, PostgreSQL/pgvector, PgVectorStore and observations without paid API calls. It **does not test a real AI model or semantic embedding quality**.

## Explicit opt-in only

- `LocalTestEmbeddingModel` is under `src/main/java/com/careerpilot/localtest/` and annotated `@Profile("local-test-embedding")`.
- It also requires `LOCAL_TEST_EMBEDDING_ENABLED=true`; enabling the profile alone fails startup.
- `application-local-test-embedding.yml` disables all six OpenAI model auto-configurations and uses an empty API key. The default production embedding remains OpenAI `text-embedding-3-small`.
- `ProductionAiKeyGuard` rejects a missing/blank production key. There is no automatic fallback.
- The same profile supplies `LocalTestChatModel`: a clearly labeled echo of the RAG Advisor's retrieved context and explicit Memory fixture facts. It does not infer semantic answers or call an external LLM. Stage15 adds deterministic explicit city/direction fixture recall from actual Message Context. Observations use provider `LOCAL_TEST`, model `local-test-retrieval-echo`, and null token usage.

The embedding normalizes text, handles a few database-query keyword aliases, hashes tokens using stable UTF-8 FNV-1a buckets, and normalizes the resulting **1536-dimensional** vector. A common fixture feature allows short mixed-topic documents to use the existing similarity threshold; this reduces discrimination. Relative keyword overlap is meaningful only for these fixtures. No semantic capability or retrieval-quality benchmark is claimed.

## Isolated Docker environment

Never point this profile at a production vector database: test hash vectors and OpenAI vectors are incompatible even though both have 1536 dimensions.

Use a separate Compose project, a private ignored `.env.rag-acceptance` file with random MySQL/PostgreSQL/JWT credentials, and these non-secret values:

```dotenv
MYSQL_DATABASE=career_pilot_ai
MYSQL_USER=career_pilot
PGVECTOR_DATABASE=career_pilot_vector
PGVECTOR_USERNAME=career_pilot
PGVECTOR_DIMENSIONS=1536
OPENAI_API_KEY=
LOCAL_TEST_WEB_PORT=18088
```

Also fill `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `PGVECTOR_PASSWORD`, `JWT_SECRET` locally. Keep them out of logs/Git. Root `.env.example` continues to default to `prod`; it does not enable test models.

Run from the repository root (one line each):

```bash
docker compose --env-file .env.rag-acceptance -p career-pilot-rag-acceptance -f docker-compose.yml -f docker-compose.local-test.yml config --quiet
docker compose --env-file .env.rag-acceptance -p career-pilot-rag-acceptance -f docker-compose.yml -f docker-compose.local-test.yml build
docker compose --env-file .env.rag-acceptance -p career-pilot-rag-acceptance -f docker-compose.yml -f docker-compose.local-test.yml up -d
docker compose --env-file .env.rag-acceptance -p career-pilot-rag-acceptance -f docker-compose.yml -f docker-compose.local-test.yml ps
python scripts/verify-local-rag.py
```

The override explicitly activates `prod,local-test-embedding`, binds Nginx only to `127.0.0.1:18088`, and places backend/databases on an internal network with no external egress. Nginx alone also joins an ingress bridge for the loopback port. It enables `pg_stat_statements` only in the test PostgreSQL container to verify native query filtering. MySQL and PostgreSQL have no published ports. The project has separate named volumes and does not affect existing `career-pilot-ai` containers/data.

## What the script actually verifies

1. Real health, frontend, user registration/login and MySQL-backed APIs through Nginx.
2. A/B uploads, actual vector rows, non-null 1536-dimensional embeddings and distinct owner metadata.
3. The same query for both users returns only their own database/secret. The response is a local retrieval echo.
4. Native PostgreSQL statement statistics contain the metadata WHERE filter before ordering/LIMIT; no Java post-filter is used.
5. Backend restart, PostgreSQL restart, and Compose down/up **without `-v`**, followed by fresh logins, counts and retrieval.
6. Deleting only A's knowledge leaves B's vectors and retrieval intact.
7. Real MySQL trace rows contain RAG_SEARCH duration/topK/chunkCount and no document text; B fetching A's trace returns 404.

The script emits only safe results into `docs/local-rag-acceptance-results.json`; passwords/JWTs stay in process memory. Random A/B test users remain in the isolated MySQL volume; A's vectors are deliberately deleted, B's remain for inspection. The file records each phase after its assertions pass. An absent/failed report is not a successful acceptance run.

Still unverified by this mode: real OpenAI Embedding, real LLM/Agent tool selection, provider Token Usage, and semantic embedding quality. Switching to real models requires the normal production profile, genuine credentials, and a separate vector dataset/re-upload.
