# HTTP API

Base: Nginx origin, default `http://127.0.0.1:8088`. Send `Authorization: Bearer <token>` for protected routes. Never submit `userId` as the owner; the backend derives it from JWT. DTOs remain compatible: success bodies are existing DTOs, errors use `{status, message, requestId}`. Secrets, SQL addresses and stack traces are not returned.

## Endpoints

| Method / path | Input / behavior |
| --- | --- |
| POST /api/auth/register | Public; username/email/password, password 8–72 characters; returns safe user view |
| POST /api/auth/login | Public; email/password; returns token and safe user view |
| GET /api/health | Public; status/application/database/vectorDatabase/mode; no connection secrets |
| GET /api/auth/me | Safe current user fields |
| POST /api/auth/logout | Stateless semantic logout; client discards JWT; no server revocation |
| GET /api/profile | Current user resume/profile |
| PUT /api/profile | name, major, targetPosition, skills, introduction; bounded strings |
| POST /api/conversations | Server-generated UUID, title New Chat; HTTP 201 |
| GET /api/conversations | page=0, size=20 (maximum 50); updated descending |
| GET /api/conversations/{id}/messages | page=0, size=100 (maximum 200); latest page displayed in sequence order |
| PATCH /api/conversations/{id} | title, 1–100 characters; manual title retained |
| DELETE /api/conversations/{id} | Owner-only history/conversation deletion; HTTP 204 |
| POST /api/chat | conversationId/message; synchronous `{reply, requestId}` |
| POST /api/chat/stream | Same request; text/event-stream; message ≤8000 characters |
| GET /api/chat/runs/{runId} | Owner-only status/timing |
| POST /api/chat/runs/{runId}/cancel | Owner-only cancellation; no supplier compute guarantee |
| POST /api/knowledge/upload | multipart `file`; PDF/TXT/MD ≤10 MB; filename/status/chunks |
| GET /api/knowledge/status | Current owner documentCount/chunkCount/vectorStoreType |
| DELETE /api/knowledge | Deletes only current owner's vectors |
| GET /api/jobs | Latest 200 owned jobs including detail fields |
| GET /api/applications | Latest 200 owned records; optional status |
| GET /api/observability/summary | range=today/7d/30d (default 7d), owner-only aggregation |
| GET /api/observability/tools | Same range, owner-only tool aggregate |
| GET /api/observability/requests | page=1, size=20 (max 50), sort=recent/slowest |
| GET /api/observability/traces/{requestId} | Owner-only metadata and events; other user's ID returns 404 |

Application status values: SAVED, APPLIED, INTERVIEW, OFFER, REJECTED, WITHDRAWN. There are no standalone REST job create/update/delete or application mutation endpoints; existing agent tools use the service layer. No Swagger/OpenAPI or public test-only endpoint is added. Multi-user private MCP remains disabled.

Chat/stream share one per-user token bucket: capacity/refill 20 per minute by default. Upload has its own 5 per minute. Configuration ranges: chat 1–1000, upload 1–100. Rejected attempts on these protected paths also consume a token. 429 includes Retry-After seconds. Budgets are in memory, bounded to 10,000 recently active owners and expire after 10 minutes idle. Server restart resets them; this does not implement distributed quotas or login throttling.

## Safe copyable examples

Example credentials below are **fictional examples**, not an automatically seeded account. Choose your own password. Do not paste genuine tokens into documentation. Use the local test port when demonstrating without a supplier key.

```bash
BASE=http://127.0.0.1:8088
curl -sS "$BASE/api/auth/register" -H 'Content-Type: application/json' \
  -d '{"username":"demo","email":"demo@example.com","password":"example-password"}'
curl -sS "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"example-password"}'
# Set TOKEN privately from the login response; never commit it.
curl -sS "$BASE/api/conversations" -X POST -H "Authorization: Bearer $TOKEN"
# Set CONVERSATION_ID from that response.
curl -N "$BASE/api/chat/stream" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H 'Accept: text/event-stream' \
  -d "{\"conversationId\":\"$CONVERSATION_ID\",\"message\":\"我的项目使用了什么数据库？\"}"
curl -sS "$BASE/api/knowledge/upload" -H "Authorization: Bearer $TOKEN" \
  -F 'file=@projects.md;type=text/markdown'
curl -sS "$BASE/api/knowledge/status" -H "Authorization: Bearer $TOKEN"
```

## SSE protocol and failures

Events: run.started, memory.loaded, rag.started, rag.completed, tool.started, tool.completed, llm.started, token, message.completed, run.completed, run.failed. Token data contains `delta`, with runId/requestId for correlation. Tool events include the safe tool name, not parameters/results. Run completion can report CANCELLED without message.completed. Interrupted/failed streams retain USER and discard partial assistant output; successful content is inserted once. Refresh loads committed history; SSE is not resumable.

Before stream establishment, errors are ordinary JSON/HTTP (401/403/404/409/429/500/503). After establishment, run.failed is an SSE event. The UI shows a safe error and reloads committed history. No execution event includes hidden Chain-of-Thought.
