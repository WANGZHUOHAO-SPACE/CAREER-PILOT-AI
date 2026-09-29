# 3–5 minute interview demo

Use the isolated **Local Test Demo** from [development.md](development.md), unless you have real supplier credentials. Point out the LOCAL TEST MODE banner at the start: this demo validates infrastructure, not genuine LLM reasoning or semantic search.

1. **0:00–0:40 — Login and workspace.** Register your own synthetic account, open Dashboard. Explain JWT/BCrypt and that every personal query uses the authenticated owner.
2. **0:40–1:30 — Persistent streaming chat.** New Chat, send `My final streaming memory value is DEMO-12345`, then `What is my final streaming memory value?`. Show progressive text, status and retained conversation. Explain bounded model context versus complete MySQL history.
3. **1:30–2:20 — RAG.** Upload a UTF-8 `projects.md` containing `CareerPilot Java database project uses MySQL.` Ask `CareerPilot Java database project uses what?`. Show native PgVectorStore retrieval, document/chunk counts and PostgreSQL persistence. The local answer is deterministic retrieval echo.
4. **2:20–3:10 — Tool and cancel.** Send `LOCAL_TEST_TOOL getResume` in a new conversation. This explicit fixture calls the real safe callback. Show tool status without raw tool results. Start a longer normal fixture and click Stop; partial assistant text is discarded and the run becomes CANCELLED.
5. **3:10–4:30 — Observability.** Open Observability, click the latest request. Show MEMORY/RAG_SEARCH/TOOL_CALL/LLM timing, TTFT and run status. Null token counts are expected for local fixtures. The trace does not contain prompt/reply/document bodies.

Wrap up: MySQL owns business/history/trace data, pgvector owns vectors, SSE is one-way authenticated response delivery, and B cannot read/cancel A's resources. For a live interview do not perform destructive volume cleanup. Preload only fictional documents; do not show real resume, credentials, tokens or logs containing local private paths.

Real OpenAI model selection, semantic answer quality, usage and supplier-side cancellation remain separate tests requiring a genuine key. Do not present this fixture demonstration as those tests.
