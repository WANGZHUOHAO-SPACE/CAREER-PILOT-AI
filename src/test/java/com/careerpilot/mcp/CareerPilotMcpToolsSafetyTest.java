package com.careerpilot.mcp;

import com.careerpilot.chat.JobService;
import com.careerpilot.rag.KnowledgeService;
import com.careerpilot.service.ApplicationService;
import com.careerpilot.service.UserProfileService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CareerPilotMcpToolsSafetyTest {

    @Test
    void hidesInternalServiceErrorFromMcpClient() {
        UserProfileService profile = mock(UserProfileService.class);
        when(profile.getResume()).thenThrow(new IllegalStateException("internal database details"));
        CareerPilotMcpTools tools = new CareerPilotMcpTools(profile, mock(JobService.class),
                mock(ApplicationService.class), mock(KnowledgeService.class));

        IllegalStateException error = assertThrows(IllegalStateException.class, tools::getResume);

        assertEquals("MCP 工具暂不可用", error.getMessage());
    }
}
