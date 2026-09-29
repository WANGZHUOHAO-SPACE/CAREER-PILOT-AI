package com.careerpilot.config;

import org.springframework.ai.chat.memory.ChatMemory;
import com.careerpilot.conversation.ConversationRepository;
import com.careerpilot.conversation.PersistentChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemoryConfig {

    public static final int MAX_MESSAGES = 20;

    @Bean
    public ChatMemory chatMemory(ConversationRepository repository,
            @Value("${app.chat-memory.max-messages:20}") int maxMessages) {
        return new PersistentChatMemory(repository, maxMessages);
    }
}
