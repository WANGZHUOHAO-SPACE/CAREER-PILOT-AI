package com.careerpilot.stream;

import com.careerpilot.common.ResourceNotFoundException;
import com.careerpilot.conversation.ConversationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class RunRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ConversationRepository conversations;
    public RunRepository(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc, ConversationRepository conversations) {
        this.jdbc = jdbc;
        this.conversations = conversations;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }
    public void begin(String runId, String requestId, long userId, String conversationId, String message) {
        transaction.executeWithoutResult(tx -> {
            jdbc.update("INSERT INTO ai_run (run_id, request_id, user_id, conversation_id, status) VALUES (?, ?, ?, ?, 'RUNNING')",
                    runId, requestId, userId, conversationId);
            conversations.append(userId, conversationId, List.of(new ConversationRepository.NewMessage("USER", message)), requestId);
        });
    }
    public RunView owned(long userId, String runId) {
        var rows = jdbc.query("SELECT * FROM ai_run WHERE user_id=? AND run_id=?", (rs, n) -> new RunView(
                rs.getString("run_id"), rs.getString("request_id"), rs.getString("conversation_id"), rs.getString("status"),
                rs.getTimestamp("started_at").toLocalDateTime(), rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime(),
                (Long) rs.getObject("time_to_first_token_ms"), (Long) rs.getObject("stream_duration_ms"), rs.getInt("output_chunk_count")), userId, runId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("未找到该运行记录");
        return rows.getFirst();
    }
    public void finish(String runId, long userId, String conversationId, String requestId, String status,
            Long ttft, long duration, int chunks, String answer) {
        transaction.executeWithoutResult(tx -> {
            if ("COMPLETED".equals(status)) conversations.append(userId, conversationId,
                    List.of(new ConversationRepository.NewMessage("ASSISTANT", answer)), requestId);
            int changed = jdbc.update("UPDATE ai_run SET status=?, completed_at=CURRENT_TIMESTAMP(6), time_to_first_token_ms=?, "
                    + "stream_duration_ms=?, output_chunk_count=? WHERE user_id=? AND run_id=? AND status='RUNNING'",
                    status, ttft, duration, chunks, userId, runId);
            if (changed != 1) throw new IllegalStateException("Run finalization conflict");
        });
    }
    public void recoverInterrupted() {
        // This deployment supports one backend. No unfinished response is promoted to completed.
        jdbc.update("UPDATE ai_run SET status='FAILED', completed_at=CURRENT_TIMESTAMP(6) WHERE status='RUNNING'");
    }
    public record RunView(String runId, String requestId, String conversationId, String status,
            LocalDateTime startedAt, LocalDateTime completedAt, Long timeToFirstTokenMs, Long streamDurationMs,
            int outputChunkCount) { }
}
