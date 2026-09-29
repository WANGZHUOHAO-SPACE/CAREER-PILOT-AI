# Persistent Conversations Implementation Plan

Goal: one MySQL history serves user-visible conversations and bounded Spring AI memory.

Architecture: retain Spring AI 2.0.1 MessageChatMemoryAdvisor; implement append-only ChatMemory over an application ConversationRepository. Official JDBC saveAll replaces rows, so it cannot safely serve complete history with a truncating window. No second history or new migration framework.

Constraints: Boot 4.0.8, AI 2.0.1, Java 21 unchanged. MySQL history; PostgreSQL vectors unchanged. JWT owner predicates on every SQL operation. USER/ASSISTANT only, no tool results/system prompt/hidden reasoning. Model failure retains USER. Short transactions outside model calls. UUIDs created by backend. Window default 20; user input max 8000. Local models remain explicit profile only.

- [x] Add conversation/message schema and repeatable v15 upgrade script, MySQL repository, ownership services, append-only ChatMemory, REST endpoints, operation conflict guard.
- [x] Test repository transactions, pagination, retained full history/window, IDOR, failures, memory+RAG+tools using H2/mocks without paid API.
- [x] Replace frontend local content cache with server conversation management, list/history pagination, rename/delete confirmation, restored selected ID only.
- [x] Extend explicit local test responder with deterministic context fixture recall; preserve retrieval echo and no external API.
- [x] Build backend/frontend and Docker; apply migration only to isolated acceptance MySQL without deleting volumes.
- [x] Real HTTP A/B, restart/down-up, IDOR, delete/title, memory+RAG/trace validation; direct MySQL safe counts, update docs/evidence and report unverified real LLM separately.
