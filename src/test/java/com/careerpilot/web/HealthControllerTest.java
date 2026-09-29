package com.careerpilot.web;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.careerpilot.service.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest {

    @Test
    void reportsDatabaseHealthWithoutExposingConfiguration() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        MockMvc mvc = mvc(new HealthService(dataSource, dataSource));

        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.vectorDatabase").value("UP"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void returnsServiceUnavailableWhenDatabaseIsDown() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));
        MockMvc mvc = mvc(new HealthService(dataSource, dataSource));

        mvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void returnsServiceUnavailableWhenOnlyVectorDatabaseIsDown() throws Exception {
        HealthService service = mock(HealthService.class);
        when(service.databaseAvailable()).thenReturn(true);
        when(service.vectorDatabaseAvailable()).thenReturn(false);
        mvc(service).perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.vectorDatabase").value("DOWN"));
    }

    @Test
    void addsRequestIdHeader() throws Exception {
        HealthService service = mock(HealthService.class);
        when(service.databaseAvailable()).thenReturn(true);
        when(service.vectorDatabaseAvailable()).thenReturn(true);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController(service))
                .addFilters(new RequestIdFilter()).build();

        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));
    }

    private static MockMvc mvc(HealthService service) {
        return MockMvcBuilders.standaloneSetup(new HealthController(service)).build();
    }
}
