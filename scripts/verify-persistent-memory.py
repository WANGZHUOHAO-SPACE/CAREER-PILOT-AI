"""Stage 15 real HTTP/MySQL acceptance in the isolated local-model Compose project.

Never prints chat content/passwords/JWTs. Never removes volumes. Not a real LLM test.
"""
import json
import secrets
from datetime import datetime, timezone
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location("rag_acceptance", Path(__file__).with_name("verify-local-rag.py"))
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)

report = {"startedAt": datetime.now(timezone.utc).isoformat(), "mode": "LOCAL_TEST",
          "realLlmMemory": "UNVERIFIED", "phases": []}


def create(user):
    row = base.http("/api/conversations", "POST", token=user["token"], expected=201)
    assert row["title"] == "New Chat" and len(row["conversationId"]) == 36 and row["messageCount"] == 0
    return row["conversationId"]


def history(user, cid):
    return base.http(f"/api/conversations/{cid}/messages", token=user["token"])


def chat(user, cid, text, contains=None, absent=None):
    old_count = len(history(user, cid)["items"])
    response = base.http("/api/chat", "POST", {"conversationId": cid, "message": text}, user["token"])
    assert response["reply"].startswith("[LOCAL TEST"), "Only local deterministic responder is allowed"
    if contains: assert contains in response["reply"], "Missing fixture recall/retrieval"
    if absent: assert absent not in response["reply"], "Cross-user content detected"
    trace = base.http("/api/observability/traces/" + response["requestId"], token=user["token"])
    memory = [event for event in trace["events"] if event["type"] == "MEMORY"]
    assert len(memory) == 1 and memory[0]["memoryMessageCount"] == min(old_count, 20)
    assert memory[0]["durationMs"] >= 0
    assert len(history(user, cid)["items"]) == old_count + 2
    encoded = json.dumps(trace)
    assert text not in encoded and (not contains or contains not in encoded), "Trace contains chat contents"
    return response, trace


def run():
    base.ready()
    suffix = secrets.token_hex(5)
    a, b = base.register(suffix, "memorya"), base.register(suffix, "memoryb")
    ca, cb = create(a), create(b)
    report["users"] = {"A": a["id"], "B": b["id"]}
    report["conversations"] = {"A": ca, "B": cb}
    chat(a, ca, "My persistent memory city is HANGZHOU-A-12345")
    _, first_trace = chat(a, ca, "What is my persistent memory city?", "HANGZHOU-A-12345", "SHANGHAI-B")
    chat(b, cb, "MY_CITY_B = SHANGHAI-B")
    chat(b, cb, "What is my city?", "SHANGHAI-B", "HANGZHOU-A-12345")
    title = next(row for row in base.http("/api/conversations", token=a["token"])["items"] if row["conversationId"] == ca)["title"]
    assert title != "New Chat"
    base.http(f"/api/conversations/{ca}", "PATCH", {"title": "Manual acceptance title"}, a["token"])
    report["phases"].append({"name": "multi-round-recall", "status": "PASS", "memoryCount": first_trace["events"][1]["memoryMessageCount"]})
    print("multi-round-recall: PASS", flush=True)

    # Every operation must reject another user's public ID at the database ownership boundary.
    for path, method, body in [(f"/api/conversations/{ca}/messages", "GET", None),
            (f"/api/conversations/{ca}", "PATCH", {"title": "Attack"}),
            (f"/api/conversations/{ca}", "DELETE", None),
            ("/api/chat", "POST", {"conversationId": ca, "message": "Attack"})]:
        base.http(path, method, body, b["token"], expected=404)
    base.http("/api/chat", "POST", {"conversationId": secrets.token_hex(16), "message": "Unknown"}, a["token"], expected=404)
    base.http(f"/api/observability/traces/{first_trace['request']['requestId']}", token=b["token"], expected=404)
    report["idor"] = {"messages": 404, "rename": 404, "delete": 404, "chat": 404, "trace": 404}
    print("http-idor: PASS", flush=True)

    for phase, restart in [("backend-restart", ("restart", "backend")), ("compose-down-up", ("down",))]:
        base.compose(*restart)
        if phase == "compose-down-up": base.compose("up", "-d")  # Never -v.
        base.ready()
        base.login(a)
        base.login(b)
        assert len(history(a, ca)["items"]) >= 4
        assert any(row["conversationId"] == ca for row in base.http("/api/conversations", token=a["token"])["items"])
        chat(a, ca, "What is my persistent memory city?", "HANGZHOU-A-12345", "SHANGHAI-B")
        chat(b, cb, "What is my city?", "SHANGHAI-B", "HANGZHOU-A-12345")
        updated = next(row for row in base.http("/api/conversations", token=a["token"])["items"] if row["conversationId"] == ca)
        assert updated["title"] == "Manual acceptance title"
        report["phases"].append({"name": phase, "status": "PASS", "historyRowsA": len(history(a, ca)["items"])})
        print(phase + ": PASS", flush=True)

    # Persistent RAG and persistent history must both feed the local context fixture.
    base.upload(a, "memory-rag-project", "CareerPilot Java project uses MySQL and Spring AI.")
    cr = create(a)
    chat(a, cr, "Remember that I am discussing my Java backend direction.")
    response, trace = chat(a, cr, "结合刚才的方向，我的 CareerPilot Java database 项目是什么？", "Java backend")
    assert "MySQL" in response["reply"]
    rag = [event for event in trace["events"] if event["type"] == "RAG_SEARCH"]
    assert len(rag) == 1 and rag[0]["chunkCount"] == 1 and rag[0]["topK"] == 4
    report["memoryRag"] = {"status": "PASS", "events": [event["type"] for event in trace["events"]],
                            "chunkCount": 1, "topK": 4}
    print("memory-rag-and-trace: PASS", flush=True)

    cw = create(a)
    for n in range(12): chat(a, cw, f"Window fixture message {n}")
    _, trace = chat(a, cw, "Window fixture final")
    memory_event = next(event for event in trace["events"] if event["type"] == "MEMORY")
    assert memory_event["memoryMessageCount"] == 20 and len(history(a, cw)["items"]) == 26
    page = base.http(f"/api/conversations/{cw}/messages?size=2&page=0", token=a["token"])
    assert len(page["items"]) == 2 and page["hasMore"]
    assert page["items"][0]["sequenceNo"] < page["items"][1]["sequenceNo"]
    report["window"] = {"historyCount": 26, "injectedCount": 20, "pagination": "PASS"}

    owners = f"({int(a['id'])},{int(b['id'])})"
    report["mysqlBeforeDelete"] = json.loads(base.mysql("SELECT JSON_OBJECT('conversationRows', COUNT(*), 'owners', COUNT(DISTINCT user_id), "
        "'messageCount', SUM(message_count)) FROM chat_conversation WHERE user_id IN " + owners))
    report["messageStructure"] = json.loads(base.mysql("SELECT JSON_OBJECT('rows', COUNT(*), 'roles', GROUP_CONCAT(DISTINCT role), "
        "'sequencePopulated', SUM(sequence_no>0), 'createdAtPopulated', COUNT(created_at), 'requestIdsPopulated', COUNT(request_id)) "
        "FROM chat_message WHERE user_id IN " + owners))
    report["mysqlIndexes"] = json.loads(base.mysql("SELECT JSON_OBJECT('conversationIndexes', "
        "(SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat_conversation'), "
        "'messageIndexes', (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat_message'))"))
    base.http(f"/api/conversations/{ca}", "DELETE", token=a["token"], expected=204)
    base.http(f"/api/conversations/{ca}/messages", token=a["token"], expected=404)
    deleted = int(base.mysql(f"SELECT COUNT(*) FROM chat_message WHERE user_id={int(a['id'])} AND conversation_id='{ca}'"))
    assert deleted == 0
    chat(b, cb, "What is my city?", "SHANGHAI-B", "HANGZHOU-A-12345")
    report["deleteIsolation"] = {"status": "PASS", "remainingDeletedConversationMessages": deleted,
                                "userBHistoryRows": len(history(b, cb)["items"])}
    report["titleAutoAndManual"] = "PASS"
    report["status"] = "PASS"
    print("window-title-and-delete-isolation: PASS", flush=True)


if __name__ == "__main__":
    try: run()
    except Exception as error:
        report["status"] = "FAILED"
        report["errorType"] = type(error).__name__
        print("Acceptance failed: " + str(error), flush=True)
        raise
    finally:
        report["finishedAt"] = datetime.now(timezone.utc).isoformat()
        (base.ROOT / "docs" / "persistent-memory-acceptance-results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
