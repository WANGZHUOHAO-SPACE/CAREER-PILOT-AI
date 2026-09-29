package com.careerpilot;

import com.careerpilot.conversation.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Exercises the actual v15 schema and repository with isolated H2 in MySQL mode. */
public record TestConversations(ConversationRepository repository, ConversationService service,
        PersistentChatMemory memory, JdbcTemplate jdbc) {
    private static final ThreadLocal<List<HikariDataSource>> POOLS = ThreadLocal.withInitial(ArrayList::new);
    public static void closeCreated() {
        POOLS.get().forEach(HikariDataSource::close);
        POOLS.remove();
    }
    public static TestConversations create() {
        try {
            HikariDataSource source = new HikariDataSource();
            source.setJdbcUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            source.setMaximumPoolSize(4);
            source.setMinimumIdle(1);
            // H2 CHECK expressions retain the DDL session. Pool it like production instead of closing it after DDL.
            POOLS.get().add(source);
            JdbcTemplate jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
            jdbc.update("INSERT INTO users VALUES (1),(2)");
            String sql = Files.readString(Path.of("sql/migrate-v15.sql"))
                    .replaceAll("(?m)^--.*$", "")
                    .replace("USE career_pilot_ai;", "")
                    .replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci", "");
            for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
            jdbc.execute("ALTER TABLE chat_message ADD status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED'");
            String runSql = Files.readString(Path.of("sql/migrate-v16.sql"));
            runSql = runSql.substring(runSql.indexOf("CREATE TABLE IF NOT EXISTS ai_run"), runSql.indexOf("-- MySQL 8"))
                    .replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci", "");
            jdbc.execute(runSql);
            ConversationRepository repository = new ConversationRepository(jdbc);
            for (long owner : new long[] {1, 2})
                for (String id : new String[] {"test-001", "test-002", "same-test", "rag-test-001", "observation-failure"}) repository.create(owner, id);
            return new TestConversations(repository, new ConversationService(repository, new ConversationOperations()),
                    new PersistentChatMemory(repository, 20), jdbc);
        }
        catch (Exception exception) { throw new IllegalStateException("Test schema setup failed", exception); }
    }
}
