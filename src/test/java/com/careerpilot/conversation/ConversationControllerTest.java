package com.careerpilot.conversation;

import com.careerpilot.TestConversations;
import com.careerpilot.TestSecurity;
import com.careerpilot.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConversationControllerTest {
    @AfterEach void closePools() { TestConversations.closeCreated(); }

    @Test void creationPaginationTitleValidationAndOwnership() throws Exception {
        TestSecurity.as(1);
        var db = TestConversations.create();
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var mvc = MockMvcBuilders.standaloneSetup(new ConversationController(db.service()))
                .setControllerAdvice(new ApiExceptionHandler()).setValidator(validator).build();
        mvc.perform(post("/api/conversations")).andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("New Chat"));
        mvc.perform(get("/api/conversations").param("size", "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(get("/api/conversations").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/conversations").param("page", "invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/conversations").param("size", "1000000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/conversations/test-001/messages").param("size", "1000000"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/conversations/test-001").contentType("application/json").content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/conversations/test-001").contentType("application/json").content("{\"title\":\""+ "a".repeat(101)+"\"}"))
                .andExpect(status().isBadRequest());
        String privateId = db.service().create().conversationId();
        TestSecurity.as(2);
        mvc.perform(get("/api/conversations/"+privateId+"/messages")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/conversations/"+privateId).contentType("application/json").content("{\"title\":\"Attack\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/conversations/"+privateId)).andExpect(status().isNotFound());
        TestSecurity.as(1);
        mvc.perform(delete("/api/conversations/"+privateId)).andExpect(status().isNoContent());
    }
}
