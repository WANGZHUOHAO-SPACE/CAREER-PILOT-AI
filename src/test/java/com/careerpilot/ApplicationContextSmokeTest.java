package com.careerpilot;

import com.careerpilot.observability.AiRequestLogMapper;
import com.careerpilot.observability.AiTraceEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "DB_URL=jdbc:mysql://127.0.0.1:1/unused",
        "DB_USERNAME=test",
        "DB_PASSWORD=test",
        "JWT_SECRET=acceptance-only-test-secret-32-bytes-minimum",
        "OPENAI_API_KEY=acceptance-placeholder",
        "app.pgvector.initialize-schema=false",
        "app.pgvector.validate-schema=false",
        "app.streaming.recover-on-startup=false"
})
class ApplicationContextSmokeTest {
    @Autowired private AiRequestLogMapper requests;
    @Autowired private AiTraceEventMapper events;
    @Autowired private DataSource primaryDataSource;
    @Autowired @Qualifier("vectorDataSource") private DataSource vectorDataSource;
    @Autowired @Qualifier("vectorJdbcTemplate") private JdbcTemplate vectorJdbcTemplate;
    @Autowired private SqlSessionFactory sqlSessionFactory;
    @Autowired private org.springframework.context.ApplicationContext context;

    @Test
    void observabilityMappersAreRegisteredInApplicationContext() {
        assertThat(requests).isNotNull();
        assertThat(events).isNotNull();
        assertThat(((HikariDataSource) primaryDataSource).getJdbcUrl()).startsWith("jdbc:mysql:");
        assertThat(((HikariDataSource) vectorDataSource).getJdbcUrl()).startsWith("jdbc:postgresql:");
        assertThat(vectorJdbcTemplate.getDataSource()).isSameAs(vectorDataSource);
        assertThat(primaryDataSource).isNotSameAs(vectorDataSource);
        assertThat(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource())
                .isSameAs(primaryDataSource);
        assertThat(context.getBeansOfType(com.careerpilot.localtest.LocalTestEmbeddingModel.class)).isEmpty();
        assertThat(context.getBeansOfType(com.careerpilot.localtest.LocalTestChatModel.class)).isEmpty();
        assertThat(context.getBean(org.springframework.ai.chat.memory.ChatMemory.class))
                .isInstanceOf(com.careerpilot.conversation.PersistentChatMemory.class);
        assertThat(context.getBean("mysqlJdbcTemplate", JdbcTemplate.class).getDataSource()).isSameAs(primaryDataSource);
    }
}
