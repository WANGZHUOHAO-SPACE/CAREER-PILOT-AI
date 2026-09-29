# CI/CD

## CI Architecture

```text
push (any branch) / pull request to main
  ├─ config-check: ignored files and high-confidence secret patterns
  ├─ backend-test: Maven tests, package, JAR artifact
  └─ frontend-build: npm ci, Vue type-check + Vite build, dist artifact
       ↓ all three pass
     docker-build: build backend and frontend images
```

The single workflow is `.github/workflows/ci.yml`. Every branch push runs CI; pull requests targeting `main` also run it. GitHub Actions jobs use read-only `contents: read` permission. There is no production deployment, image push, or Release creation.

## Jobs and Cache

| Job | Commands | Cache / output |
| --- | --- | --- |
| `config-check` | `python3 scripts/check-ci-config.py`; `python3 scripts/test_pre_public_check.py`; `python3 scripts/pre-public-check.py` | Read-only publication gate; checks tracked and nonignored untracked files; never prints secret values |
| `backend-test` | `mvn -B -ntp clean test`; `mvn -B -ntp clean package` | Java 21 Temurin; Maven dependency cache; JAR artifact retained 7 days |
| `frontend-build` | `npm ci`; `npm run build` | Node 24; npm download cache based on `frontend/package-lock.json`; `dist/` artifact retained 7 days |
| `docker-build` | `docker compose config --quiet`; build both Dockerfiles with `docker build` | Runs only after the other jobs pass; does not start services or push images |

`npm run build` already runs `vue-tsc --noEmit` before Vite, so no separate TypeScript command is needed. The project has no lint script. The Maven tests mock chat and embedding models and database mappers; `src/test/resources` provides a test-only JWT secret. No real OpenAI API Key, JWT secret, or MySQL service is needed. Docker builds use the existing multi-stage Dockerfiles and do not require a `.env` file.

The lockfile uses `registry.npmjs.org` tarball URLs so GitHub-hosted runners do not depend on the development machine's npm mirror. The integrity hashes remain unchanged, and `npm ci` validates the lockfile.

## Local Validation

Run from the repository root:

```bash
python3 scripts/check-ci-config.py
mvn clean test
mvn clean package
cd frontend
npm ci
npm run build
cd ..
docker compose config
docker compose build
docker build --file Dockerfile --tag career-pilot-backend:ci .
docker build --file frontend/Dockerfile --tag career-pilot-frontend:ci frontend
```

The CI Compose check supplies clearly fake `ci-only` values for required variables and uses `--quiet` to avoid printing expanded configuration. Local `docker compose config` and `docker compose build` remain deployment checks using the values described in `.env.example`; ordinary CI image builds intentionally avoid real secrets.

## Persistent Vector Store Checks

Compose validation includes the `postgres-vector` service and supplies fake `PGVECTOR_DATABASE`, `PGVECTOR_USERNAME`, and `PGVECTOR_PASSWORD`. Ordinary unit tests do not require PostgreSQL; the smoke context disables only test schema initialization, while production keeps initialization/validation enabled. `mvn -Pvector-integration-test test` is an optional real-database check, not part of ordinary CI and never calls a paid model.

## GitHub Secrets and Publishing

Ordinary CI requires **no repository secrets**. Test-only placeholders are local to the test code; they are not credentials. The workflow does not print environment variables or model prompts. The lightweight config checker is not a replacement for GitHub Secret Scanning or review of old Git history. If a real credential was ever committed, rotate it immediately.

GHCR publishing is **not configured**. Pushing a tag such as `v1.0.0` runs CI's normal push checks but does not publish an image or create a Release. There is no automatic SSH production deployment. Configure a separate publishing workflow only when repository ownership, registry visibility, and release policy are decided.

## Branch Protection

After pushing this repository to GitHub and observing a successful run, manually enable branch protection for `main`: **Require status checks to pass before merging**. Select `config-check`, `backend-test`, `frontend-build`, and `docker-build`. Also configure the desired review rule. This repository cannot change GitHub Settings by committing YAML.

No CI badge is included because this local repository has no GitHub remote yet. After publishing it, use the actual `OWNER/REPO` in GitHub's workflow badge URL.
