# Stage 16 Streaming Chat

Preserve Boot 4.0.8 / Spring AI 2.0.1 / Java 21. Reuse the same ChatClient, persistent USER/ASSISTANT history, authenticated owner and PgVectorStore filter. SSE POST plus owner-scoped run/cancel endpoints; one canonical history, buffer assistant until successful atomic finalization. No hidden reasoning or private tool output in events/observability.

- [x] Verify installed ChatClient streaming APIs and advisor aggregation behavior.
- [x] Add repeatable v16 MySQL migration, ai_run and streaming observation metadata.
- [x] Propagate authenticated security/MDC/trace across Reactor; bounded runs, finalization and cancellation.
- [x] Local-only deterministic stream, tool fixture and partial-failure fixture; absent provider Usage stays null.
- [x] Authenticated frontend SSE parsing, incremental safe text, Stop, account/conversation guards; Nginx no buffering.
- [x] Tests for protocol, finalization, failure, cancel, tools, ownership and persistent memory.
- [x] Final local clean test/package (83 tests, none skipped), npm ci/build and Compose config.
- [x] Final Docker/browser rerun: Docker recovered on 2026-09-29. Final Nginx HTTP A/B, Origin, restart, down/up, RAG/tool/cancel/observability and browser login/live output/Stop/history checks passed using local test models.
- [x] Finish docs and numbered stage16 report with separate earlier Docker and latest local evidence; real OpenAI remains unverified.

Verification scope: [summary](../../streaming-verification-summary.json), [final HTTP evidence](../../streaming-final-http-results.json), [browser evidence](../../streaming-final-browser-results.json), [seal report](../../stage16-seal-report.md). No factory reset or volume deletion was performed. Final gates passed for the explicit LOCAL_TEST profile. Real OpenAI Streaming, TTFT, cancellation and Usage remain unverified.
