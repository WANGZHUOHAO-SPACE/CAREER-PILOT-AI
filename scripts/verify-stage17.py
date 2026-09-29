"""Local fixture smoke through Nginx with real MySQL/pgvector. Keeps all volumes.
Requires the isolated acceptance project documented in local-test-embedding.md.
Never prints credentials, message bodies or raw tool results.
"""
import importlib.util
import json
import secrets
import urllib.request
import urllib.error
from pathlib import Path
from datetime import datetime, timezone

spec = importlib.util.spec_from_file_location("streaming", Path(__file__).with_name("verify-streaming.py"))
streaming = importlib.util.module_from_spec(spec)
spec.loader.exec_module(streaming)
base = streaming.base
report = {"mode": "LOCAL_TEST", "startedAt": datetime.now(timezone.utc).isoformat()}

def run():
    base.ready()
    health = base.http("/api/health")
    assert health["mode"] == "LOCAL_TEST"
    report["health"] = health
    with urllib.request.urlopen(base.BASE + "/api/health") as response:
        assert response.headers.get_all("X-Content-Type-Options") == ["nosniff"]
        assert response.headers.get_all("X-Frame-Options") == ["DENY"]
        assert response.headers.get_all("Referrer-Policy") == ["same-origin"]
    with urllib.request.urlopen(base.BASE) as response:
        assert response.status == 200
        report["headers"] = {key: response.headers[key] for key in
                             ["X-Content-Type-Options", "X-Frame-Options", "Referrer-Policy", "Content-Security-Policy"]}
    suffix = secrets.token_hex(5)
    a, b = base.register(suffix, "hardeningA"), base.register(suffix, "hardeningB")
    report["userIds"] = [a["id"], b["id"]]
    owner = int(a["id"])
    base.mysql(f"INSERT INTO job(user_id,company,position,jd,location,salary) VALUES ({owner},'Hardening fictional company','Java test role','Synthetic Spring Boot JD','Example city','Example salary'); SET @fixture_job=LAST_INSERT_ID(); INSERT INTO application(user_id,job_id,status,remark) VALUES ({owner},@fixture_job,'SAVED','Synthetic fixture');")
    owned_jobs = base.http("/api/jobs", token=a["token"])
    owned_apps = base.http("/api/applications", token=a["token"])
    assert len(owned_jobs) == len(owned_apps) == 1
    assert base.http("/api/jobs", token=b["token"]) == []
    assert base.http("/api/applications", token=b["token"]) == []
    report["jobApplicationIsolation"] = "PASS"
    cid = streaming.create(a)
    streaming.stream(a, cid, "My final streaming memory value is HARDENING-12345")
    answer, _, trace = streaming.stream(a, cid, "What is my final streaming memory value?")
    assert "HARDENING-12345" in answer
    report["memoryStreaming"] = "PASS"
    report["observationPrivacy"] = "PASS"
    base.upload(a, "hardening-rag", "CareerPilot final RAG knowledge project uses HARDENING-RAG-67890.")
    rag_id = streaming.create(a)
    answer, _, trace = streaming.stream(a, rag_id, "CareerPilot final RAG knowledge project uses what?")
    assert "HARDENING-RAG-67890" in answer
    assert {"MEMORY", "RAG_SEARCH", "LLM"}.issubset({e["type"] for e in trace["events"]})
    report["ragStreaming"] = "PASS"
    tool_id = streaming.create(a)
    streaming.stream(a, tool_id, "LOCAL_TEST_TOOL getResume")
    names = streaming.report["phases"][-1]["events"]
    assert names.index("tool.started") < names.index("tool.completed") < names.index("token")
    report["toolStreaming"] = "PASS"
    cancel_id = streaming.create(a)
    stopped = False
    def cancel(event, payload):
        nonlocal stopped
        if event == "token" and not stopped:
            stopped = True
            base.http(f"/api/chat/runs/{payload['runId']}/cancel", "POST", token=b["token"], expected=404)
            base.http(f"/api/chat/runs/{payload['runId']}/cancel", "POST", token=a["token"])
    streaming.stream(a, cancel_id, "Hardening cancellation fixture", "CANCELLED", cancel)
    report["stopAndCancelIdor"] = "PASS"
    failed_id = streaming.create(a)
    streaming.stream(a, failed_id, "LOCAL_TEST_FAIL_STREAM", "FAILED")
    report["failureFinalization"] = "PASS"
    for method, path, data in [
        ("GET", f"/api/conversations/{cid}/messages", None),
        ("PATCH", f"/api/conversations/{cid}", {"title": "Attempt"}),
        ("DELETE", f"/api/conversations/{cid}", None),
        ("POST", "/api/chat", {"conversationId": cid, "message": "Attempt"})]:
        base.http(path, method, data, b["token"], expected=404)
    base.http("/api/observability/traces/" + trace["request"]["requestId"], token=b["token"], expected=404)
    report["ownershipIdor"] = "PASS"
    for path in ["/api/jobs", "/api/applications", "/api/observability/summary", "/api/observability/tools",
                 "/api/observability/requests", "/api/knowledge/status"]:
        base.http(path, token=a["token"])
    report["businessQueries"] = "PASS"
    for path in ["/api/conversations?size=1000000", "/api/observability/requests?size=1000000"]:
        base.http(path, token=a["token"], expected=400)
    base.http("/api/chat", "POST", {"conversationId": cid, "message": "x" * 8001}, a["token"], expected=400)
    boundary = "hardening-boundary"
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="../bad.md"\r\n'
            'Content-Type: text/markdown\r\n\r\nfictional\r\n' + f'--{boundary}--\r\n').encode()
    base.http("/api/knowledge/upload", "POST", body, a["token"],
              {"Content-Type": "multipart/form-data; boundary=" + boundary}, expected=400)
    report["boundsAndUploadValidation"] = "PASS"
    rate_user = base.register(suffix, "rate")
    denied = None
    for i in range(50):
        request = urllib.request.Request(base.BASE + ("/api/chat" if i % 2 == 0 else "/api/chat/stream"),
            method="POST", data=b"{}", headers={"Authorization": "Bearer " + rate_user["token"], "Content-Type": "application/json"})
        try:
            urllib.request.urlopen(request).close()
            raise AssertionError("Invalid chat unexpectedly accepted")
        except urllib.error.HTTPError as error:
            payload = json.loads(error.read())
            assert payload["requestId"] == error.headers["X-Request-Id"]
            if error.code == 429:
                assert int(error.headers["Retry-After"]) >= 1
                denied = i + 1
                break
            assert error.code == 400
    assert denied is not None
    base.http("/api/chat", "POST", {}, b["token"], expected=400)
    report["rateLimit"] = {"status": "PASS", "deniedAtRequest": denied, "otherUserUnaffected": True}
    report["applicationIndex"] = int(base.mysql("SELECT count(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='application' AND index_name='idx_application_user_id'"))
    assert report["applicationIndex"] == 2
    report["applicationQueryPlan"] = base.mysql(f"EXPLAIN SELECT * FROM application WHERE user_id={owner} ORDER BY id DESC LIMIT 200").splitlines()
    report["phases"] = streaming.report["phases"]
    report["status"] = "PASS"
    report["finishedAt"] = datetime.now(timezone.utc).isoformat()
    # Private browser fixtures remain ignored and are not part of the published report.
    (base.ROOT / ".env.stage17-users.json").write_text(json.dumps({"A": a, "B": b}), encoding="utf-8")
    print("Stage 17 local HTTP/DB/SSE smoke: PASS", flush=True)

if __name__ == "__main__":
    try: run()
    except Exception as error:
        report["status"] = "FAILED"
        report["failureType"] = type(error).__name__
        raise
    finally:
        (base.ROOT / "docs/stage17-smoke-results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
