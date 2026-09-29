# Architecture decisions

## Relational data and vectors

MySQL owns users, resume/profile, jobs, applications, conversations, visible chat messages, AI runs and observation metadata. PostgreSQL/pgvector owns document chunks and embeddings. Separate qualified DataSources/JdbcTemplates keep MyBatis-Plus connected to MySQL. Vector search uses Spring AI 2.0.1 PgVectorStore, cosine distance, HNSW and 1536 dimensions. Upload/query use the same configured embedding model.

`userId` is attached by the authenticated backend and filtered inside PostgreSQL similarity search. Results are never searched across all users and then filtered in Java. Knowledge counts and deletion use the same owner predicate. HNSW plus restrictive metadata filters requires larger-scale benchmarking before production capacity claims.

## Persistent memory

MySQL is the single source of visible USER/ASSISTANT history. Spring AI MessageChatMemoryAdvisor uses the application repository with an internal `u:<owner>:c:<public-id>` key. This permits ownership checks, lists, rename/delete and long-term history without a second conflicting history store. The model receives a bounded window (default 20 messages), not the entire database history. System/tool results are not exposed as visible history; hidden reasoning is not recorded.

## SSE instead of WebSocket

Responses flow from the backend to the browser. Authenticated POST + fetch SSE supports JWT headers and cancellation without a bidirectional socket protocol. Nginx disables buffering/cache. Observable execution events describe system steps, not model reasoning. USER is committed first; successful assistant output is buffered and inserted once. Cancel/failure discards partial assistant content. Cancelling the subscription does not prove that the model supplier stopped billing/computation.

## Ownership and limits

JWT subject determines the user. SQL queries include owner predicates; guessing conversation/run/request IDs returns 404. Private MCP tools remain disabled until a safe client identity mapping exists. Chat/stream share a per-user token bucket; upload has a separate bucket. State is bounded, expires after inactivity and resets on backend restart. This is a single-instance control, not a distributed quota or login abuse defense.

## Observations and privacy

Request/run IDs correlate memory count, RAG chunk count, tool name, status, latency and TTFT. Observations do not persist full prompts, replies, resume or retrieved text. Chat history deliberately persists visible conversation text. Optional observation write failures are isolated from the reply; canonical history/run storage failures remain business failures. Usage is provider-reported or null; local fixtures do not report real usage.

## Deployment boundary

Only Nginx is published. Backend and databases communicate by Compose service names. Backend runs as UID 10001; Nginx uses the standard root master/non-root worker design. Both application containers set `no-new-privileges`. Production needs HTTPS, appropriate firewall policy, backups, genuine supplier credentials and a reviewed runtime configuration. Local demo has separate project volumes, loopback ingress and no backend internet egress.
