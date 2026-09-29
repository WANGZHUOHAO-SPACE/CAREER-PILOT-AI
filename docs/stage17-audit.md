# Stage 17 audit and scope

Audit date: 2026-09-29. No business feature expansion, dependency platform upgrade, Git push or remote repository creation.

## Findings before changes

| Priority | Finding | Targeted action |
| --- | --- | --- |
| High | Costly chat/upload endpoints have no per-user request budget | Bounded in-memory token buckets after JWT authentication |
| High | Security errors differ from controller errors; request IDs are unavailable before the security chain | Preserve `status/message`, add `requestId`, order the request filter first |
| Medium | Upload filenames silently discard paths | Reject path/control characters, retain existing MIME/signature/UTF-8 checks |
| Medium | Public upload scope includes untracked files; credentials and runtime logs exist locally | Read-only pre-public gate over all publish candidates, ignored-file inventory |
| Medium | Entire Element Plus registered globally, main JS 1,099.31 kB | Import used components/styles only; retain existing lazy routes |
| Medium | Jobs and applications lists are unbounded | Cap latest results at 200; label Dashboard counts accordingly |
| Medium | Applications without status sort by owner/id without a matching index | Add one repeatable index migration; retain batched job lookup |
| Medium | Local models lack a persistent UI warning | Health reports runtime mode; show LOCAL TEST MODE banner |
| Medium | Production Compose imports demo SQL; README contradicts persistent history | Stop automatic demo import; rewrite accurate deployment/privacy documentation |
| Low | No License, no release notes, documentation dispersed | Record absence of a selected License; prepare release notes and concise guides |

## Preserved designs

- Java 21, Spring Boot 4.0.8, Spring AI 2.0.1; no platform upgrade.
- MySQL ownership predicates, JWT subject-derived current user and no client `userId`.
- Official PgVectorStore, VECTOR(1536), HNSW, COSINE, Top-K 4 and native metadata `userId` filter.
- Canonical MySQL chat history, bounded memory window, USER retention on failure, successful assistant finalization once.
- SSE buffering disabled, bounded run registry, cancel subscription and run IDOR checks.
- Privacy-safe observability; MCP private tools remain disabled in multi-user deployments.

## Query review

Conversation lists use `(user_id, updated_at)`; messages use owner/conversation/sequence indexes. Job lists use `(user_id, id)`. Application status searches already use `(user_id, status)`; non-status listing receives `(user_id, id)`. Trace queries use owner/request indexes, recent requests owner/time indexes. Applications fetch all associated jobs in one query; no per-row job lookup exists. No confirmed N+1 was found in these paths. No speculative vector index or aggregate index was added.

HNSW combined with metadata filtering needs production-scale recall/latency benchmarking. SQL aggregation and deep OFFSET pagination remain small-project designs.

## Publication inventory

Ignored local environment files contain database/JWT/test credentials. Runtime logs contain local machine paths. Their values are never included in audit output. Do not publish these files. The gate is heuristic; manually inspect screenshots, fixtures, SQL and acceptance JSON for personal information before staging. No Git history is rewritten, and no License is selected by this work.
