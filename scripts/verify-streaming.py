"""Real Nginx HTTP/SSE + MySQL/pgvector acceptance. Local deterministic models only.

Never prints credentials/chat contents. Keeps all Docker volumes and existing users.
"""
import importlib.util
import json
import secrets
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("base", Path(__file__).with_name("verify-local-rag.py"))
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
report = {"mode": "LOCAL_TEST", "startedAt": datetime.now(timezone.utc).isoformat(),
          "endpoint": base.BASE + "/api/chat/stream", "phases": [],
          "realOpenAiStreaming": "UNVERIFIED", "realOpenAiTtft": "UNVERIFIED",
          "providerCancellation": "UNVERIFIED", "providerTokenUsage": "UNVERIFIED"}


def create(user):
    return base.http("/api/conversations", "POST", token=user["token"], expected=201)["conversationId"]


def history(user, cid):
    return base.http(f"/api/conversations/{cid}/messages", token=user["token"])["items"]


def stream(user, cid, text, status="COMPLETED", callback=None):
    before = len(history(user, cid))
    received = time.monotonic()
    req = urllib.request.Request(base.BASE + "/api/chat/stream", method="POST",
        data=json.dumps({"conversationId": cid, "message": text}).encode(),
        headers={"Authorization": "Bearer " + user["token"], "Content-Type": "application/json", "Accept": "text/event-stream", "Origin": base.BASE})
    events, tokens, arrivals = [], [], []
    first_event_ms = None
    with urllib.request.urlopen(req, timeout=30) as response:
        assert response.status == 200 and response.headers["Content-Type"].startswith("text/event-stream")
        assert response.headers.get("Cache-Control") == "no-cache"
        event, data = None, []
        for raw in response:
            line = raw.decode("utf-8").rstrip("\r\n")
            if line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:"):
                data.append(line[5:].lstrip())
            elif not line and event and data:
                payload = json.loads("\n".join(data))
                if first_event_ms is None:
                    first_event_ms = round((time.monotonic() - received) * 1000)
                events.append((event, payload))
                if event == "token":
                    tokens.append(payload["delta"])
                    arrivals.append(round((time.monotonic() - received) * 1000))
                if callback: callback(event, payload)
                event, data = None, []
    assert events[0][0] == "run.started"
    rid, request_id = events[0][1]["runId"], events[0][1]["requestId"]
    assert all(item["runId"] == rid and item["requestId"] == request_id for _, item in events)
    names = [name for name, _ in events]
    assert names[-1] == ("run.failed" if status == "FAILED" else "run.completed")
    assert events[-1][1]["status"] == status
    row = base.http(f"/api/chat/runs/{rid}", token=user["token"])
    assert row["status"] == status and row["outputChunkCount"] == len(tokens)
    after = history(user, cid)
    assert len(after) == before + (2 if status == "COMPLETED" else 1)
    if status == "COMPLETED":
        assert names[-2] == "message.completed"
        assert after[-1]["content"] == "".join(tokens) and after[-1]["status"] == "COMPLETED"
        assert after[-2]["role"] == "USER" and after[-2]["status"] == "SENT"
    else:
        assert "message.completed" not in names and after[-1]["role"] == "USER"
    trace = base.http("/api/observability/traces/" + request_id, token=user["token"])
    assert trace["request"]["status"] == ("SUCCESS" if status == "COMPLETED" else status)
    assert trace["request"]["outputChunkCount"] == len(tokens)
    assert trace["request"]["timeToFirstTokenMs"] >= 0 and trace["request"]["streamDurationMs"] >= 0
    assert all(value is None for value in trace["tokenUsage"].values())
    assert trace["provider"] == "LOCAL_TEST"
    assert text not in json.dumps(trace), "Observability must not store prompt contents"
    report["phases"].append({"status": "PASS", "runId": rid, "requestId": request_id,
        "runStatus": status, "events": names, "outputChunks": len(tokens),
        "firstEventAtMs": first_event_ms, "firstDeltaAtMs": arrivals[0], "lastDeltaAtMs": arrivals[-1],
        "timeToFirstTokenMs": row["timeToFirstTokenMs"], "streamDurationMs": row["streamDurationMs"]})
    return "".join(tokens), row, trace


def run():
    base.ready()
    a, b = base.register(secrets.token_hex(5), "streama"), base.register(secrets.token_hex(5), "streamb")
    ca, cb = create(a), create(b)
    report["users"] = {"A": a["id"], "B": b["id"]}
    report["conversations"] = {"A": ca, "B": cb}
    for user, cid, value, other in [(a, ca, "STREAM-A-12345", "STREAM-B-98765"), (b, cb, "STREAM-B-98765", "STREAM-A-12345")]:
        stream(user, cid, "My streaming memory value is " + value)
        answer, row, trace = stream(user, cid, "What is my streaming memory value?")
        assert value in answer and other not in answer
        assert any(event["type"] == "MEMORY" and event["memoryMessageCount"] == 2 for event in trace["events"])
    names = report["phases"][0]["events"]
    for left, right in zip(["run.started", "memory.loaded", "rag.started", "rag.completed", "llm.started", "token", "message.completed"],
                           ["memory.loaded", "rag.started", "rag.completed", "llm.started", "token", "message.completed", "run.completed"]):
        assert names.index(left) < names.index(right)
    assert report["phases"][0]["lastDeltaAtMs"] - report["phases"][0]["firstDeltaAtMs"] >= 500
    report["nginxIncrementalDelivery"] = "PASS"
    print("nginx-sse-order-and-multi-user-memory: PASS", flush=True)

    # HTTP ownership checks happen before a stream starts.
    base.http("/api/chat/stream", "POST", {"conversationId": ca, "message": "Attack"}, b["token"], expected=404)
    base.http("/api/chat/stream", "POST", {"conversationId": ca, "message": "Attack"}, expected=401)
    first = report["phases"][0]
    base.http("/api/chat/runs/" + first["runId"], token=b["token"], expected=404)
    base.http("/api/observability/traces/" + first["requestId"], token=b["token"], expected=404)

    tool_cid = create(a)
    answer, row, trace = stream(a, tool_cid, "LOCAL_TEST_TOOL getResume")
    assert "getResume executed" in answer
    assert any(event["type"] == "TOOL_CALL" and event["name"] == "getResume" for event in trace["events"])
    assert "tool.started" in report["phases"][-1]["events"] and "tool.completed" in report["phases"][-1]["events"]
    report["toolStreaming"] = "PASS"
    print("real-tool-callback-and-stream: PASS", flush=True)

    base.upload(a, "streaming-project", "CareerPilot Java project uses MySQL and Spring AI.")
    cr = create(a)
    answer, row, trace = stream(a, cr, "我的 CareerPilot Java database 项目是什么？")
    assert "MySQL" in answer
    rag = next(event for event in trace["events"] if event["type"] == "RAG_SEARCH")
    assert rag["chunkCount"] == 1 and rag["topK"] == 4 and rag["durationMs"] >= 0
    assert {"MEMORY", "RAG_SEARCH", "LLM"}.issubset({event["type"] for event in trace["events"]})
    report["persistentRagStreaming"] = "PASS"
    print("real-pgvector-rag-and-stream-trace: PASS", flush=True)

    cc = create(a)
    cancelled = False
    def cancel_callback(event, payload):
        nonlocal cancelled
        if event == "token" and not cancelled:
            cancelled = True
            rid = payload["runId"]
            base.http(f"/api/chat/runs/{rid}/cancel", "POST", token=b["token"], expected=404)
            base.http("/api/chat/stream", "POST", {"conversationId": cc, "message": "Duplicate"}, a["token"], expected=409)
            assert base.http(f"/api/chat/runs/{rid}/cancel", "POST", token=a["token"])["status"] == "CANCELLED"
    answer, row, trace = stream(a, cc, "Slow cancellation fixture", "CANCELLED", cancel_callback)
    assert len(answer) < 50 and trace["request"]["cancelled"] is True
    report["cancelIdor"] = {"B_cancel_A": 404, "sameConversationDuplicate": 409, "A_cancel": "CANCELLED", "assistantRows": 0}
    print("cancel-and-cancel-idor: PASS", flush=True)

    cf = create(a)
    stream(a, cf, "LOCAL_TEST_FAIL_STREAM", "FAILED")
    report["partialFailure"] = "PASS"
    print("partial-stream-failure-no-assistant: PASS", flush=True)

    base.compose("restart", "backend")
    base.ready(); base.login(a); base.login(b)
    assert len(history(a, ca)) == 4
    answer, _, _ = stream(a, ca, "What is my streaming memory value?")
    assert "STREAM-A-12345" in answer and "STREAM-B-98765" not in answer
    report["backendRestartHistoryAndMemory"] = "PASS"
    print("backend-restart-history-and-memory: PASS", flush=True)

    # Synchronous compatibility uses the same history and advisor chain.
    sync = base.http("/api/chat", "POST", {"conversationId": ca, "message": "What is my streaming memory value?"}, a["token"])
    assert "STREAM-A-12345" in sync["reply"]
    report["synchronousCompatibility"] = "PASS"
    ids = ",".join("'" + phase["runId"] + "'" for phase in report["phases"])
    rows = base.mysql(f"SELECT status,count(*),min(time_to_first_token_ms),min(output_chunk_count) FROM ai_run WHERE run_id IN ({ids}) GROUP BY status")
    report["mysqlRunAggregates"] = rows.splitlines()
    obs = base.mysql(f"SELECT l.status,count(*),sum(l.cancelled),sum(l.input_tokens IS NOT NULL OR l.output_tokens IS NOT NULL OR l.total_tokens IS NOT NULL) FROM ai_request_log l JOIN ai_run r ON r.request_id=l.request_id WHERE r.run_id IN ({ids}) GROUP BY l.status")
    report["mysqlObservationAggregates"] = obs.splitlines()
    assert "CANCELLED" in rows and "COMPLETED" in rows and "FAILED" in rows
    report["containerStates"] = base.compose("ps").splitlines()
    report["finishedAt"] = datetime.now(timezone.utc).isoformat()
    report["status"] = "PASS"
    (base.ROOT / "docs/streaming-acceptance-results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print("streaming acceptance: ALL PASS; evidence docs/streaming-acceptance-results.json", flush=True)


if __name__ == "__main__":
    try: run()
    except Exception as failure:
        report["status"] = "FAILED"
        report["failureType"] = type(failure).__name__
        (base.ROOT / "docs/streaming-acceptance-results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        raise
