"""Real HTTP/database acceptance for the explicitly isolated local test Compose project.

Never calls a paid API. Does not print credentials, tokens, document text or vector values.
Keeps volumes; DELETE targets only the generated test user A.
"""
import json
import secrets
import subprocess
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://127.0.0.1:18088"
COMPOSE = ["docker", "compose", "--env-file", ".env.rag-acceptance", "-p", "career-pilot-rag-acceptance",
           "-f", "docker-compose.yml", "-f", "docker-compose.local-test.yml"]
REPORT = {"startedAt": datetime.now(timezone.utc).isoformat(), "modelMode": "LOCAL_TEST",
          "realOpenAiEmbedding": "UNVERIFIED", "realOpenAiChat": "UNVERIFIED",
          "providerTokenUsage": "UNVERIFIED", "semanticQuality": "UNVERIFIED", "phases": []}


def compose(*args):
    result = subprocess.run(COMPOSE + list(args), cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
    if result.returncode:
        raise RuntimeError("Compose command failed: " + args[0])
    return result.stdout


def database(sql):
    return compose("exec", "-T", "postgres-vector", "sh", "-c",
                   'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA -c "$1"', "sh", sql).strip()


def mysql(sql):
    return compose("exec", "-T", "mysql", "sh", "-c",
                   'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u "$MYSQL_USER" "$MYSQL_DATABASE" -N -e "$1"', "sh", sql).strip()


def http(path, method="GET", data=None, token=None, headers=None, expected=200):
    request_headers = headers.copy() if headers else {}
    if isinstance(data, dict):
        data = json.dumps(data, ensure_ascii=False).encode("utf-8")
        request_headers["Content-Type"] = "application/json"
    if token:
        request_headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, data=data, method=method, headers=request_headers)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    if status != expected:
        raise RuntimeError(f"HTTP {method} {path} returned {status}, expected {expected}")
    if not raw:
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return raw.decode("utf-8")


def ready():
    deadline = time.monotonic() + 300
    while time.monotonic() < deadline:
        try:
            health = http("/api/health")
            if health["status"] == health["database"] == health["vectorDatabase"] == "UP":
                return
        except (RuntimeError, urllib.error.URLError, TimeoutError, ConnectionError):
            pass
        time.sleep(2)
    raise RuntimeError("Backend/dual-database health did not recover in 300 seconds")


def login(user):
    response = http("/api/auth/login", "POST", {"email": user["email"], "password": user["password"]})
    assert response["user"]["id"] == user["id"], "User identity changed after restart"
    user["token"] = response["token"]


def register(suffix, label):
    user = {"email": f"rag-{label}-{suffix}@example.invalid", "password": secrets.token_urlsafe(18)}
    response = http("/api/auth/register", "POST", {"username": f"rag_{label}_{suffix}", **user})
    user["id"] = response["id"]
    login(user)
    return user


def upload(user, label, text):
    boundary = "careerpilot" + secrets.token_hex(12)
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{label}.md"\r\n'
            f'Content-Type: text/markdown\r\n\r\n{text}\r\n--{boundary}--\r\n').encode("utf-8")
    result = http("/api/knowledge/upload", "POST", body, user["token"],
                  {"Content-Type": "multipart/form-data; boundary=" + boundary})
    assert result["chunks"] == 1, "Expected one fixture chunk per document"


def check_chat(user, own_database, other_database, own_secret, other_secret):
    conversation = http("/api/conversations", "POST", token=user["token"], expected=201)
    response = http("/api/chat", "POST", {
        "conversationId": conversation["conversationId"],
        "message": "我的 CareerPilot Java database 是什么？"}, user["token"])
    answer = response["reply"]
    assert answer.startswith("[LOCAL TEST"), "External/real LLM must not be used"
    assert own_database in answer and own_secret in answer, "Own database/secret was not retrieved"
    assert other_database not in answer and other_secret not in answer, "Cross-user retrieval leak"
    trace = http("/api/observability/traces/" + response["requestId"], token=user["token"])
    rag = [event for event in trace["events"] if event["type"] == "RAG_SEARCH"]
    assert len(rag) == 1 and rag[0]["status"] == "SUCCESS" and rag[0]["topK"] == 4
    assert rag[0]["chunkCount"] == 1 and rag[0]["durationMs"] >= 0
    assert trace["provider"] == "LOCAL_TEST" and all(value is None for value in trace["tokenUsage"].values())
    encoded = json.dumps(trace)
    assert own_database not in encoded and own_secret not in encoded, "Trace must not contain chunk text"
    return {"requestId": response["requestId"], "userId": user["id"], "ragEvent": rag[0], "provider": trace["provider"]}


def owner_counts(a, b):
    sql = f"""SELECT json_build_object('rows', count(*), 'owners', json_agg(DISTINCT metadata->>'userId'),
      'dimensions', json_agg(DISTINCT vector_dims(embedding)), 'nonNullEmbeddings', count(embedding),
      'documentIdsPresent', bool_and(metadata::jsonb ? 'documentId'))
      FROM public.vector_store WHERE metadata->>'userId' IN ('{int(a['id'])}','{int(b['id'])}')"""
    result = json.loads(database(sql))
    assert result["rows"] == result["nonNullEmbeddings"] == 2
    assert set(result["owners"]) == {str(a["id"]), str(b["id"])} and result["dimensions"] == [1536]
    assert result["documentIdsPresent"] is True
    return result


def check_phase(name, a, b):
    ready()
    login(a)
    login(b)
    for user in (a, b):
        status = http("/api/knowledge/status", token=user["token"])
        assert status == {"documentCount": 1, "chunkCount": 1, "vectorStoreType": "PgVectorStore"}
        for endpoint in ("/api/auth/me", "/api/jobs", "/api/applications", "/api/observability/summary"):
            http(endpoint, token=user["token"])
    trace_a = check_chat(a, "MYSQL-A", "POSTGRESQL-B", "VECTOR-A-12345", "VECTOR-B-98765")
    trace_b = check_chat(b, "POSTGRESQL-B", "MYSQL-A", "VECTOR-B-98765", "VECTOR-A-12345")
    http("/api/observability/traces/" + trace_a["requestId"], token=b["token"], expected=404)
    result = {"name": name, "status": "PASS", "vectorRows": owner_counts(a, b), "traces": [trace_a, trace_b], "traceIdor": 404}
    REPORT["phases"].append(result)
    print(name + ": PASS", flush=True)


def run():
    ready()
    assert "<html" in http("/").lower(), "Frontend must be reachable through Nginx"
    database("CREATE EXTENSION IF NOT EXISTS pg_stat_statements")
    suffix = secrets.token_hex(5)
    a, b = register(suffix, "a"), register(suffix, "b")
    REPORT["users"] = {"A": a["id"], "B": b["id"]}
    REPORT["totalVectorRowsBeforeUploads"] = int(database("SELECT count(*) FROM public.vector_store"))
    upload(a, "a-project", "My private persistent vector secret is VECTOR-A-12345.\nCareerPilot Java database for user A is MYSQL-A.")
    upload(b, "b-project", "My private persistent vector secret is VECTOR-B-98765.\nCareerPilot Java database for user B is POSTGRESQL-B.")
    check_phase("initial-http-and-database", a, b)
    REPORT["totalVectorRowsAfterUploads"] = int(database("SELECT count(*) FROM public.vector_store"))
    native_queries = json.loads(database("""SELECT COALESCE(json_agg(query), '[]'::json)
        FROM pg_stat_statements WHERE query LIKE '%embedding%' AND query LIKE '%metadata::jsonb%'
        AND query LIKE '%ORDER BY%' AND query LIKE '%LIMIT%'"""))
    assert any("@@" in query and "WHERE" in query and " AND " in query for query in native_queries), "Missing native metadata WHERE filter"
    REPORT["databaseFilterSql"] = native_queries
    REPORT["databaseVersion"] = json.loads(database("""SELECT json_build_object('postgres', current_setting('server_version'),
        'pgvector', (SELECT extversion FROM pg_extension WHERE extname='vector'),
        'columnType', (SELECT format_type(atttypid, atttypmod) FROM pg_attribute
        WHERE attrelid='public.vector_store'::regclass AND attname='embedding'))"""))
    print("database-level-metadata-filter: PASS", flush=True)
    compose("restart", "backend")
    check_phase("backend-restart", a, b)
    compose("restart", "postgres-vector")
    check_phase("postgres-restart", a, b)
    compose("down")  # Deliberately no -v: named volumes must survive.
    compose("up", "-d")
    check_phase("compose-down-up", a, b)
    assert http("/api/knowledge", "DELETE", token=a["token"])["chunkCount"] == 0
    empty_conversation = http("/api/conversations", "POST", token=a["token"], expected=201)
    empty = http("/api/chat", "POST", {"conversationId": empty_conversation["conversationId"],
                  "message": "我的 CareerPilot Java database 是什么？"}, a["token"])
    assert "当前知识库没有找到相关资料" in empty["reply"] and "MYSQL-A" not in empty["reply"]
    trace_b = check_chat(b, "POSTGRESQL-B", "MYSQL-A", "VECTOR-B-98765", "VECTOR-A-12345")
    assert http("/api/knowledge/status", token=b["token"])["chunkCount"] == 1
    counts = json.loads(database(f"""SELECT json_build_object('A', count(*) FILTER (WHERE metadata->>'userId'='{int(a['id'])}'),
        'B', count(*) FILTER (WHERE metadata->>'userId'='{int(b['id'])}')) FROM public.vector_store"""))
    assert counts == {"A": 0, "B": 1}
    REPORT["deletionIsolation"] = {"status": "PASS", "remainingRows": counts, "userBTrace": trace_b}
    REPORT["totalVectorRowsAfterDelete"] = int(database("SELECT count(*) FROM public.vector_store"))
    REPORT["index"] = json.loads(database("""SELECT json_build_object('hnswCosineIndex',
        bool_or(indexdef LIKE '%hnsw%' AND indexdef LIKE '%vector_cosine_ops%'))
        FROM pg_indexes WHERE schemaname='public' AND tablename='vector_store'"""))
    owners = f"({int(a['id'])},{int(b['id'])})"
    REPORT["observationDatabase"] = {
        "requests": json.loads(mysql("SELECT JSON_OBJECT('requestRows', COUNT(*), 'localProviderRows', SUM(provider='LOCAL_TEST'), "
            "'rowsWithTokens', SUM(input_tokens IS NOT NULL OR output_tokens IS NOT NULL OR total_tokens IS NOT NULL)) "
            "FROM ai_request_log WHERE user_id IN " + owners)),
        "events": json.loads(mysql("SELECT JSON_OBJECT('ragEvents', COUNT(*), 'minDurationMs', MIN(duration_ms), "
            "'maxDurationMs', MAX(duration_ms), 'topK', MIN(top_k)) FROM ai_trace_event "
            "WHERE event_type='RAG_SEARCH' AND user_id IN " + owners))}
    assert REPORT["index"]["hnswCosineIndex"] is True
    assert REPORT["observationDatabase"]["requests"]["requestRows"] == 10
    assert REPORT["observationDatabase"]["requests"]["localProviderRows"] == 10
    assert REPORT["observationDatabase"]["requests"]["rowsWithTokens"] == 0
    assert REPORT["observationDatabase"]["events"]["ragEvents"] == 10
    REPORT["status"] = "PASS"
    print("deletion-isolation: PASS", flush=True)


if __name__ == "__main__":
    try:
        run()
    except Exception as error:
        REPORT["status"] = "FAILED"
        REPORT["errorType"] = type(error).__name__
        print("Acceptance failed: " + str(error), flush=True)
        raise
    finally:
        REPORT["finishedAt"] = datetime.now(timezone.utc).isoformat()
        (ROOT / "docs" / "local-rag-acceptance-results.json").write_text(json.dumps(REPORT, ensure_ascii=False, indent=2), encoding="utf-8")
