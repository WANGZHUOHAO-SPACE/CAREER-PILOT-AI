# 第十五阶段最终报告

1. 原Memory：InMemoryChatMemoryRepository + MessageWindowChatMemory，重启丢失。
2. 新Memory：PersistentChatMemory + 单一MySQL ConversationRepository。
3. Spring AI API：2.0.1 ChatMemory、MessageChatMemoryAdvisor，已检查实际Jar。
4. 官方JdbcChatMemoryRepository：未使用。
5. 自定义原因：官方saveAll替换语义与长期完整历史、owner约束及会话CRUD不直接匹配；只维护一套消息。
6. 新表：chat_conversation、chat_message，schema.sql和migrate-v15.sql。
7. 会话表：id/conversation_id/user_id/title/title_manually_set/message_count/created_at/updated_at/last_message_at。
8. 消息表：id/conversation_id/user_id/role/content/sequence_no/request_id/created_at，唯一序号和组合owner外键。
9. 隔离：所有查询、更新、删除都带JWT owner SQL谓词。
10. Public ID：后端随机UUID。
11. Internal key：u:userId:c:conversationId，校验当前认证用户。
12. 模型加载：默认最多20条历史，环境变量CHAT_MEMORY_MAX_MESSAGES。
13. 数据库：完整可见历史，窗口不删除旧行。
14. SYSTEM/TOOL：不持久化；只有USER和最终ASSISTANT，工具循环当轮仍由官方Advisor处理。
15. API：POST/GET conversations，GET messages，PATCH title，DELETE conversation。
16. New Chat：调用后端POST，使用返回UUID。
17. 列表：默认20条/页，updated_at降序，Load More。
18. 历史：默认最近100条，最多200条/页，每页正序，支持Earlier Messages。
19. Rename：owner限定，1～100字符，标记手动标题。
20. Delete：事务删除消息和会话，FK级联，无缓存。
21. 浏览器ID：不再生成最终会话ID；localStorage仅保存选择ID，不保存正文。
22. Backend restart：真实Docker/HTTP本地集成测试通过。
23. Compose down/up：真实通过，未使用-v。
24. A/B隔离：真实HTTP通过。
25. IDOR：B读取消息、rename、delete、chat使用A_ID全部404。
26. Memory串线：真实本地测试，A/B只召回各自城市。
27. 删除：A消息清零，B正常，真实MySQL确认。
28. Memory+RAG：真实MySQL/PgVectorStore/HTTP本地模型联合通过。
29. Memory+Tools：自动测试通过getResume/analyzeJob调用、用户上下文和仅保存最终消息；真实OpenAI自主工具选择未验证。
30. MEMORY Trace：真实HTTP/MySQL通过，记录加载数量及耗时，不含正文；26条历史仅加载20条。
31. AI失败：自动测试确认USER保留、无假ASSISTANT、之后重试可成功。
32. Backend：mvn clean test及mvn clean package，74 tests，0失败/错误/跳过，BUILD SUCCESS。
33. Frontend：npm ci及npm run build成功，类型检查开启；已有大包warning。
34. Docker：config/build/up/ps通过，三项健康、Frontend Up。首次构建Maven Central403；使用本机依赖预填BuildKit缓存后，Java21容器内真实编译并执行全部测试。
35. MySQL：本轮4会话、46条消息实际写入，USER/ASSISTANT、owner、序号、时间、requestId已检查；删除A后A消息为0。完整schema及v15脚本（两次）执行成功，既有用户数量未变。
36. 未验证：真实OpenAI Memory/Token/自主Tool选择、GitHub远程CI、Linux部署、完整浏览器交互。
37. 限制：历史没有自动保留期/总配额；明文聊天正文需数据库及备份保护；并发协调只覆盖单Backend，序号数据库安全；没有自动摘要。

本地模型不代表真实LLM Memory能力。证据：persistent-memory-acceptance-results.json。
