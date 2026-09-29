"""Final stage-16 acceptance on the isolated Compose project. No external model calls.

Keeps all volumes. Only synthetic fixture data is created. Secrets stay in an ignored file.
"""
import importlib.util
import json
import secrets
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("stream", Path(__file__).with_name("verify-streaming.py"))
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)
base = v.base
report = {"mode": "LOCAL_TEST", "startedAt": datetime.now(timezone.utc).isoformat(),
          "entry": base.BASE, "realOpenAiStreaming": "UNVERIFIED", "realOpenAiTtft": "UNVERIFIED",
          "providerCancellation": "UNVERIFIED", "providerTokenUsage": "UNVERIFIED"}


def check_value(user, cid):
    answer, row, trace = v.stream(user, cid, "What is my final streaming memory value?")
    assert "FINAL-STREAM-12345" in answer
    return row


def run():
    base.ready()
    report["health"] = base.http("/api/health")
    suffix = secrets.token_hex(5)
    a, b = base.register(suffix, "finala"), base.register(suffix, "finalb")
    ca, cb = v.create(a), v.create(b)
    report["users"] = {"A": a["id"], "B": b["id"]}
    report["conversations"] = {"A": ca, "B": cb}
    v.stream(a, ca, "My final streaming memory value is FINAL-STREAM-12345")
    check_value(a, ca)
    v.stream(b, cb, "My final streaming memory value is FINAL-B-98765")
    answer, _, _ = v.stream(b, cb, "What is my final streaming memory value?")
    assert "FINAL-B-98765" in answer and "FINAL-STREAM-12345" not in answer
    report["memoryIsolation"] = "PASS"
    print("exact-final-memory-and-isolation: PASS", flush=True)

    names = v.report["phases"][0]["events"]
    required = ["run.started", "memory.loaded", "llm.started", "token", "message.completed", "run.completed"]
    assert [names.index(name) for name in required] == sorted(names.index(name) for name in required)
    timing = v.report["phases"][0]
    assert timing["lastDeltaAtMs"] - timing["firstDeltaAtMs"] >= 500
    report["sseTiming"] = {key: timing[key] for key in ["firstEventAtMs", "firstDeltaAtMs", "lastDeltaAtMs", "outputChunks"]}
    report["incrementalDelivery"] = "PASS"

    base.upload(a, "final-rag", "CareerPilot final RAG knowledge project uses FINAL-RAG-VALUE-67890.")
    cr = v.create(a)
    answer, _, trace = v.stream(a, cr, "CareerPilot final RAG knowledge project uses what?")
    assert "FINAL-RAG-VALUE-67890" in answer
    assert {"MEMORY", "RAG_SEARCH", "LLM"}.issubset({event["type"] for event in trace["events"]})
    rag = next(event for event in trace["events"] if event["type"] == "RAG_SEARCH")
    assert rag["chunkCount"] == 1 and rag["topK"] == 4
    report["ragTrace"] = {"status": "PASS", "topK": rag["topK"], "chunkCount": rag["chunkCount"], "durationMs": rag["durationMs"]}
    report["vectorRows"] = int(base.database(f"SELECT count(*) FROM vector_store WHERE metadata->>'userId'='{a['id']}'"))
    print("exact-final-pgvector-rag-and-trace: PASS", flush=True)

    ct = v.create(a)
    _, _, _ = v.stream(a, ct, "LOCAL_TEST_TOOL getResume")
    names = v.report["phases"][-1]["events"]
    assert names.index("tool.started") < names.index("tool.completed") < names.index("token")
    report["toolStreaming"] = "PASS"

    cc = v.create(a)
    stopped = False
    def cancel(event, payload):
        nonlocal stopped
        if event == "token" and not stopped:
            stopped = True
            base.http(f"/api/chat/runs/{payload['runId']}/cancel", "POST", token=b["token"], expected=404)
            assert base.http(f"/api/chat/runs/{payload['runId']}/cancel", "POST", token=a["token"])["status"] == "CANCELLED"
    v.stream(a, cc, "Final slow cancellation fixture", "CANCELLED", cancel)
    report["cancelIdor"] = 404
    report["cancelledAssistantRows"] = 0
    cf = v.create(a)
    v.stream(a, cf, "LOCAL_TEST_FAIL_STREAM", "FAILED")
    report["partialFailure"] = "PASS"
    print("tool-cancel-idor-and-failure: PASS", flush=True)

    base.compose("restart", "backend")
    base.ready(); base.login(a); base.login(b)
    assert len(v.history(a, ca)) == 4
    check_value(a, ca)
    report["backendRestart"] = "PASS"
    print("final-backend-restart: PASS", flush=True)

    # Do not pass -v: existing MySQL and pgvector volumes must remain.
    base.compose("down")
    base.compose("up", "-d")
    base.ready(); base.login(a); base.login(b)
    assert len(v.history(a, ca)) == 6
    check_value(a, ca)
    answer, _, _ = v.stream(a, cr, "CareerPilot final RAG knowledge project uses what?")
    assert "FINAL-RAG-VALUE-67890" in answer
    assert len(v.history(b, cb)) == 4
    report["composeDownUpHistoryAndRag"] = "PASS"
    print("compose-down-up-with-volumes-preserved: PASS", flush=True)

    ids = ",".join("'" + item["runId"] + "'" for item in v.report["phases"])
    requests = ",".join("'" + item["requestId"] + "'" for item in v.report["phases"])
    report["mysqlRunStatusCounts"] = base.mysql(f"SELECT status,count(*) FROM ai_run WHERE run_id IN ({ids}) GROUP BY status").splitlines()
    report["mysqlMessageStatusCounts"] = base.mysql(f"SELECT role,status,count(*) FROM chat_message WHERE request_id IN ({requests}) GROUP BY role,status").splitlines()
    report["mysqlObservationStatusCounts"] = base.mysql(f"SELECT status,count(*) FROM ai_request_log WHERE request_id IN ({requests}) GROUP BY status").splitlines()
    report["mysqlTraceTypeCounts"] = base.mysql(f"SELECT event_type,count(*) FROM ai_trace_event WHERE request_id IN ({requests}) GROUP BY event_type").splitlines()
    duplicate = int(base.mysql(f"SELECT count(*) FROM (SELECT request_id FROM chat_message WHERE request_id IN ({requests}) AND role='ASSISTANT' GROUP BY request_id HAVING count(*)<>1) x"))
    assert duplicate == 0
    for phase in v.report["phases"]:
        count = int(base.mysql(f"SELECT count(*) FROM chat_message WHERE request_id='{phase['requestId']}' AND role='ASSISTANT'"))
        assert count == (1 if phase["runStatus"] == "COMPLETED" else 0)
    report["assistantWrittenOnceOnlyOnSuccess"] = "PASS"
    report["containerStates"] = base.compose("ps").splitlines()
    report["phases"] = v.report["phases"]
    report["status"] = "PASS"
    report["finishedAt"] = datetime.now(timezone.utc).isoformat()
    # Private fixture credentials are not evidence and must never be committed or printed.
    (base.ROOT / ".env.streaming-final-users.json").write_text(json.dumps({"A": a, "B": b}), encoding="utf-8")
    print("final-http-acceptance: ALL PASS", flush=True)


if __name__ == "__main__":
    try:
        run()
    except Exception as error:
        report["status"] = "FAILED"
        report["failureType"] = type(error).__name__
        raise
    finally:
        (base.ROOT / "docs/streaming-final-http-results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
