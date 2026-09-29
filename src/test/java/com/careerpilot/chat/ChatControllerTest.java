package com.careerpilot.chat;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.careerpilot.web.ApiExceptionHandler;
import com.careerpilot.web.RequestIdFilter;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {

    @Test
    void forwardsConversationAndMessageToService() throws Exception {
        ChatService service = mock(ChatService.class);
        when(service.reply("conversation-1", "你好")).thenReturn("你好，请问需要什么求职帮助？");
        MockMvc mvc = mvc(service);

        var result = mvc.perform(post("/api/chat").contentType("application/json")
                .content("{\"conversationId\":\"conversation-1\",\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("你好，请问需要什么求职帮助？"))
                .andReturn();
        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertTrue(result.getResponse().getContentAsString().contains("\"requestId\":\"" + requestId + "\""));
        verify(service).reply("conversation-1", "你好");
    }

    @Test
    void rejectsBlankConversationAndOversizedMessage() throws Exception {
        MockMvc mvc = mvc(mock(ChatService.class));
        mvc.perform(post("/api/chat").contentType("application/json")
                .content("{\"conversationId\":\" \",\"message\":\"你好\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat").contentType("application/json")
                .content("{\"conversationId\":\"c-1\",\"message\":\"" + "a".repeat(8001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    private static MockMvc mvc(ChatService service) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new ChatController(service))
                .setValidator(validator).setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestIdFilter()).build();
    }
}
