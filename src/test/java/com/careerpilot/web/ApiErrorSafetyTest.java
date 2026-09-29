package com.careerpilot.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class ApiErrorSafetyTest {
    @Test void unexpectedIllegalArgumentDetailsAreNotReturned() {
        var body = new ApiExceptionHandler().badRequest(new IllegalArgumentException("private model or database detail")).getBody();
        assertEquals(400, body.status());
        assertFalse(body.message().contains("private"));
    }
    @Test void databaseDetailsAreHiddenAndRequestIdIsPreserved() {
        MDC.put("requestId", "safe-request-id");
        try {
            var body = new ApiExceptionHandler().databaseUnavailable(
                    new DataAccessResourceFailureException("sensitive database connection detail")).getBody();
            assertEquals(503, body.status());
            assertEquals("safe-request-id", body.requestId());
            assertFalse(body.message().contains("sensitive"));
        } finally { MDC.remove("requestId"); }
    }
    @Test void filterErrorsReuseJsonContractWithoutStackTrace() throws Exception {
        var response = new MockHttpServletResponse();
        response.setHeader("X-Request-Id", "safe-request-id");
        ApiExceptionHandler.writeError(response, 429, "Too many requests");
        var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.getContentAsString());
        assertEquals(429, body.get("status").asInt());
        assertEquals("safe-request-id", body.get("requestId").asText());
        assertFalse(body.has("stackTrace"));
    }
}
