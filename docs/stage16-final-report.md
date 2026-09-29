# 第十六阶段最终报告

> 此报告保留当时的验收状态。2026-09-29 Docker 恢复后的最终收尾结果见 [最终封版验收报告](stage16-seal-report.md)，已补齐最终 HTTP/SSE 和浏览器验证。

实现已完成；最终集成验收尚未全部完成。最新本机构建通过，此前 Docker HTTP/SSE 验收通过；目前 Docker Desktop 无法启动，最新代理修复后的浏览器及 HTTP 重测未验证。原始证据见 [HTTP/SSE 验收记录](streaming-acceptance-results.json)，验证范围见 [验证汇总](streaming-verification-summary.json)。以下本地模型结果均为本地集成测试，不代表真实 OpenAI 验证。

1. **Streaming 协议：** 使用 SSE，响应类型为 `text/event-stream`；原同步接口保留。
2. **选择 SSE 的原因：** 回答从服务器单向推送；POST 配合 `fetch` 支持 JWT 和 JSON 请求，不需要 WebSocket。
3. **Spring AI API：** 保持 Spring AI 2.0.1，复用同一个 `ChatClient`，调用 `.prompt().stream().chatResponse()`；Memory/RAG/官方 Tool Calling Advisor 继续参与调用。
4. **Endpoint：** `POST /api/chat/stream`；另有 `GET /api/chat/runs/{runId}` 和 `POST /api/chat/runs/{runId}/cancel`。请求仍只有 `conversationId`、`message`，身份来自 JWT。
5. **SSE 事件：** `run.started`、`memory.loaded`、`rag.started`、`rag.completed`、`tool.started`、`tool.completed`、`llm.started`、`token`、`message.completed`、`run.completed`、`run.failed`。事件带 `runId`、`requestId`；取消以 `run.completed` 携带 `status=CANCELLED` 结束，不发送 `message.completed`。
6. **runId：** 后端使用随机 UUID，为每次运行生成独立 ID。
7. **用户绑定：** `ai_run.user_id` 来自 `CurrentUser`；查询和取消使用 `WHERE user_id=? AND run_id=?`，他人记录返回 404。认证、Trace、MDC 通过 Reactor Context 传播。
8. **Assistant 持久化：** USER 在创建运行时的短事务内提交；流期间只累积文本，成功后将完整 ASSISTANT 和 COMPLETED 状态在同一个事务中提交。
9. **每 token 写库：** 否。只有最终成功时保存一次 ASSISTANT，避免逐块 SQL 写入。
10. **失败处理：** 保留 USER，丢弃部分 ASSISTANT，不保存虚假的成功回答；运行标记 FAILED，发送 `run.failed`。取消也不保存部分 ASSISTANT。`chat_message` 增加状态，USER 为 SENT，完整 ASSISTANT 为 COMPLETED。
11. **取消实现：** 校验用户归属后进入终态 CANCELLED，停止后续 token，释放会话运行锁并取消 Reactor 订阅。取消与成功完成的竞争由同一个终态锁控制。
12. **供应商计算是否取消：** 未验证。取消订阅不能证明供应商停止计算或计费；已执行的工具写入也不会自动撤销。
13. **Cancel IDOR：** 此前真实 Docker/Nginx HTTP 本地集成测试中，B 取消 A 的 run 返回 404；最新自动测试同样通过。最终版本 Docker 重测未验证。
14. **TTFT：** 从 HTTP 过滤器入口到首个非空文本 delta 提交给 SSE emitter，使用 `System.nanoTime()`；这是服务器发送指标，不是浏览器收到文本的网络耗时。真实 OpenAI TTFT 未验证。
15. **Observability：** 增加 `time_to_first_token_ms`、`stream_duration_ms`、`output_chunk_count`、`cancelled`，记录成功、失败及取消。Local Test Token 保持 null。官方 ToolCallingAdvisor 的多次调用 Usage 累加通过两轮 Mock 测试：输入 300、输出 70、总计 370；真实供应商 Usage 未验证。
16. **RAG Streaming：** 此前真实 PostgreSQL/pgvector + 本地 Embedding + Nginx SSE 流程通过，产生 RAG_SEARCH 和 `rag.started/completed`；继续使用数据库级 userId filter，不发送 Chunk 正文。最终版本重测未验证。
17. **Memory Streaming：** 本地集成测试中 A/B 多轮记忆、流完成后的 MySQL 历史及重启回忆通过。最新自动测试通过；上下文仍限制最近 20 条，当前已提交 USER 不会重复注入。
18. **Tool Streaming：** 本地测试模型发出实际工具调用，经官方 Advisor 执行当前用户 `getResume`，产生 `tool.started/completed` 后继续输出文本；此前 Docker 测试和最新自动测试通过。真实模型自行选择工具未验证。
19. **Nginx：** 配置 `proxy_buffering off`、`proxy_cache off`，读写超时 180 秒，保留 Host 端口并传递受控 Forwarded 信息。此前实测首块 375ms、末块 1594ms，确认逐步传输；最终 Origin 修复后的浏览器重测未验证。
20. **前端渲染：** JWT `fetch` + ReadableStream + TextDecoder 解析 SSE，将 delta 追加到固定 Assistant 气泡，显示检索/工具/生成状态及 Stop；最终结束才刷新一次服务端历史，不逐 token 查询会话。
21. **会话切换隔离：** 生成时禁用切换、新建及重复发送；回调同时检查账号代次和 conversationId。切换账号会中断连接，旧回调不能写入新用户页面。服务端同一会话已有运行时返回 409。
22. **Backend Restart：** 此前真实 Docker 重启后，已完成消息仍在 MySQL，重新登录并继续同一会话的本地记忆测试通过。最新版本重测未验证；未完成运行在启动时标记 FAILED，不伪造完整回答。
23. **Docker HTTP/SSE：** 实际执行过隔离 Compose 项目的 `config --quiet`、`build`、`up -d`、`ps` 以及 `python scripts/verify-streaming.py`。此前四个服务正常，9 次运行中 7 完成、1 取消、1 失败，HTTP/SSE 验收通过。最后一次镜像构建中执行了 82 个测试并成功。目前 Docker Desktop 失效，最终代码的完整重测及浏览器登录/Stop 尚未验证；没有重置或删除数据卷。
24. **Backend 测试：** 最新执行 `mvn -B -ntp clean test`：83 个测试，失败 0、错误 0、跳过 0，BUILD SUCCESS；`mvn -B -ntp clean package` 同样运行 83 个测试并 BUILD SUCCESS。流式专用测试 8 个，覆盖顺序、拼接、持久化、失败、取消、隔离、工具及 Usage 累加。没有跳过测试或关闭安全检查。
25. **Frontend Build：** 最新在 `frontend` 执行 `npm ci` 成功，`npm run build` 的 `vue-tsc --noEmit` 和 Vite 构建成功（8.93s）。仍有大于 500kB 的构建体积警告。`python scripts/check-ci-config.py` 通过；远程 GitHub Actions 未验证。
26. **仍未验证：** 当前 Docker 恢复后的最终完整验收、带 Origin 的最终 HTTP 请求、浏览器注册/登录/流式展示/Stop；真实 OpenAI Streaming、TTFT、供应商取消、Token Usage。运行目前限单 Backend，无断点续传或分布式 run 协调。
27. **Local Test 与真实模型：** LocalTestChatModel 只在显式本地测试 Profile 使用，按固定规则读取测试上下文、逐块输出及触发工具/失败夹具。它验证真实 HTTP、数据库、Advisor 与生命周期流程，不验证真实 LLM 的推理、流速或供应商行为；生产不会自动回退到测试模型。

本阶段到此停止，不开发第十七阶段。
