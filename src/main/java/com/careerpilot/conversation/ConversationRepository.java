package com.careerpilot.conversation;

import com.careerpilot.common.ResourceNotFoundException;
import com.careerpilot.conversation.ConversationDtos.Conversation;
import com.careerpilot.conversation.ConversationDtos.HistoryMessage;
import com.careerpilot.conversation.ConversationDtos.Page;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/** One canonical append-only history. Every query includes the authenticated owner. */
@Repository
public class ConversationRepository {
    private static final RowMapper<Conversation> CONVERSATION = (rs, n) -> new Conversation(
            rs.getString("conversation_id"), rs.getString("title"),
            rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime(),
            rs.getTimestamp("last_message_at") == null ? null : rs.getTimestamp("last_message_at").toLocalDateTime(),
            rs.getLong("message_count"));
    private static final RowMapper<HistoryMessage> MESSAGE = (rs, n) -> new HistoryMessage(
            rs.getLong("id"), rs.getString("role"), rs.getString("content"), rs.getLong("sequence_no"),
            rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("request_id"), rs.getString("status"));
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public ConversationRepository(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    public Conversation create(long userId, String id) {
        return transactions.execute(status -> {
            jdbc.update("INSERT INTO chat_conversation (user_id, conversation_id, title) VALUES (?, ?, 'New Chat')", userId, id);
            return requireOwned(userId, id);
        });
    }

    public Conversation requireOwned(long userId, String id) {
        List<Conversation> rows = jdbc.query("SELECT * FROM chat_conversation WHERE user_id=? AND conversation_id=?",
                CONVERSATION, userId, id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("未找到该会话");
        return rows.getFirst();
    }

    public Page<Conversation> list(long userId, int page, int size) {
        List<Conversation> rows = jdbc.query("SELECT * FROM chat_conversation WHERE user_id=? "
                + "ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?", CONVERSATION, userId, size + 1, offset(page, size));
        return new Page<>(page, size, rows.size() > size, rows.stream().limit(size).toList());
    }

    /** page=0 is the newest bounded page; each page is displayed oldest-to-newest. */
    public Page<HistoryMessage> history(long userId, String id, int page, int size) {
        requireOwned(userId, id);
        List<HistoryMessage> rows = jdbc.query("SELECT * FROM chat_message WHERE user_id=? AND conversation_id=? "
                + "AND role IN ('USER','ASSISTANT') ORDER BY sequence_no DESC LIMIT ? OFFSET ?",
                MESSAGE, userId, id, size + 1, offset(page, size));
        boolean more = rows.size() > size;
        List<HistoryMessage> visible = new ArrayList<>(rows.stream().limit(size).toList());
        Collections.reverse(visible);
        return new Page<>(page, size, more, List.copyOf(visible));
    }

    public List<HistoryMessage> window(long userId, String id, int max) {
        return history(userId, id, 0, max).items();
    }

    /** The streaming USER was committed before work was queued; the prompt adds it exactly once. */
    public List<HistoryMessage> windowBeforeRequest(long userId, String id, int max, String requestId) {
        requireOwned(userId, id);
        List<HistoryMessage> rows = jdbc.query("SELECT * FROM chat_message WHERE user_id=? AND conversation_id=? "
                + "AND role IN ('USER','ASSISTANT') AND (request_id IS NULL OR request_id<>?) ORDER BY sequence_no DESC LIMIT ?",
                MESSAGE, userId, id, requestId, max);
        Collections.reverse(rows);
        return List.copyOf(rows);
    }

    public void append(long userId, String id, List<NewMessage> messages, String requestId) {
        if (messages.isEmpty()) return;
        transactions.executeWithoutResult(status -> {
            // Serialize sequence allocation with a short row lock, never around the LLM call.
            List<Long> counts = jdbc.query("SELECT message_count FROM chat_conversation "
                    + "WHERE user_id=? AND conversation_id=? FOR UPDATE", (rs, n) -> rs.getLong(1), userId, id);
            if (counts.isEmpty()) throw new ResourceNotFoundException("未找到该会话");
            long sequence = counts.getFirst();
            for (NewMessage message : messages) {
                jdbc.update("INSERT INTO chat_message (user_id, conversation_id, role, content, sequence_no, request_id, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", userId, id, message.role(), message.content(), ++sequence, requestId,
                        "USER".equals(message.role()) ? "SENT" : "COMPLETED");
                if ("USER".equals(message.role()) && sequence == 1) {
                    String title = message.content().replaceAll("\\s+", " ").trim();
                    title = title.substring(0, title.offsetByCodePoints(0, Math.min(title.codePointCount(0, title.length()), 42)));
                    jdbc.update("UPDATE chat_conversation SET title=? WHERE user_id=? AND conversation_id=? AND title_manually_set=FALSE",
                            title, userId, id);
                }
            }
            jdbc.update("UPDATE chat_conversation SET message_count=?, last_message_at=CURRENT_TIMESTAMP(6), "
                    + "updated_at=CURRENT_TIMESTAMP(6) WHERE user_id=? AND conversation_id=?", sequence, userId, id);
        });
    }

    public Conversation rename(long userId, String id, String title) {
        return transactions.execute(status -> {
            if (jdbc.update("UPDATE chat_conversation SET title=?, title_manually_set=TRUE, updated_at=CURRENT_TIMESTAMP(6) "
                    + "WHERE user_id=? AND conversation_id=?", title, userId, id) == 0)
                throw new ResourceNotFoundException("未找到该会话");
            return requireOwned(userId, id);
        });
    }

    public void delete(long userId, String id) {
        transactions.executeWithoutResult(status -> {
            requireOwned(userId, id);
            jdbc.update("DELETE FROM chat_message WHERE user_id=? AND conversation_id=?", userId, id);
            if (jdbc.update("DELETE FROM chat_conversation WHERE user_id=? AND conversation_id=?", userId, id) != 1)
                throw new ResourceNotFoundException("未找到该会话");
        });
    }

    private static long offset(int page, int size) {
        if (page < 0 || size < 1 || size > 200 || (long) page * size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("page须大于等于0，size须介于1和200");
        return (long) page * size;
    }

    public record NewMessage(String role, String content) { }
}
