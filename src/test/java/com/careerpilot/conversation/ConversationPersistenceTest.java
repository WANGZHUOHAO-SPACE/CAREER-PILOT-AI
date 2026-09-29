package com.careerpilot.conversation;

import com.careerpilot.TestConversations;
import com.careerpilot.TestSecurity;
import com.careerpilot.common.ResourceNotFoundException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import com.careerpilot.chat.*;
import com.careerpilot.service.*;
import com.careerpilot.rag.KnowledgeException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ConversationPersistenceTest {
    @BeforeEach void authenticate() { TestSecurity.as(1); }
    @org.junit.jupiter.api.AfterEach void closePools() { TestConversations.closeCreated(); }

    @Test void fullHistoryIsRetainedWhileModelReadsOnlyConfiguredWindow() {
        var db = TestConversations.create();
        var id = db.service().create().conversationId();
        var memory = new PersistentChatMemory(db.repository(), 4);
        for (int n = 0; n < 6; n++) memory.add(ConversationService.memoryKey(1, id), new UserMessage("message-" + n));
        assertEquals(4, memory.get(ConversationService.memoryKey(1, id)).size());
        assertEquals("message-2", memory.get(ConversationService.memoryKey(1, id)).getFirst().getText());
        assertEquals(6, db.service().history(id, 0, 100).items().size());
        assertEquals(6, db.repository().requireOwned(1, id).messageCount());
        assertEquals(2, db.service().history(id, 0, 2).items().size());
        assertTrue(db.service().history(id, 0, 2).hasMore());
        assertEquals("message-2", db.service().history(id, 1, 2).items().getFirst().content());
        assertEquals(4, new PersistentChatMemory(db.repository(), 4).get(ConversationService.memoryKey(1, id)).size());
    }

    @Test void titlesAndDeletionStayScopedAndOnlyVisibleMessagesAreStored() {
        var db = TestConversations.create();
        String id = db.service().create().conversationId();
        assertEquals("New Chat", db.repository().requireOwned(1, id).title());
        db.memory().add(ConversationService.memoryKey(1, id), new UserMessage("First\n message"));
        assertEquals("First message", db.repository().requireOwned(1, id).title());
        db.service().rename(id, "Custom title");
        db.memory().add(ConversationService.memoryKey(1, id), List.of(new UserMessage("Other"), new SystemMessage("private system"),
                AssistantMessage.builder().toolCalls(List.of(new AssistantMessage.ToolCall("c", "function", "getResume", "{}"))).build()));
        assertEquals("Custom title", db.repository().requireOwned(1, id).title());
        assertEquals(2, db.service().history(id, 0, 100).items().size());
        TestSecurity.as(2);
        assertThrows(ResourceNotFoundException.class, () -> db.service().history(id, 0, 100));
        assertThrows(ResourceNotFoundException.class, () -> db.service().rename(id, "Attack"));
        assertThrows(ResourceNotFoundException.class, () -> db.service().delete(id));
        assertThrows(ResourceNotFoundException.class, () -> db.service().beginChat(id));
        assertThrows(IllegalArgumentException.class, () -> db.memory().get(ConversationService.memoryKey(1, id)));
        assertFalse(db.service().list(0, 50).items().stream().anyMatch(row -> row.conversationId().equals(id)));
        TestSecurity.as(1);
        db.service().delete(id);
        assertEquals(0, db.jdbc().queryForObject("SELECT COUNT(*) FROM chat_message WHERE conversation_id=?", Long.class, id));
        assertThrows(ResourceNotFoundException.class, () -> db.service().history(id, 0, 100));
        assertNotNull(db.repository().requireOwned(2, "same-test"));
    }

    @Test void sequenceAllocationIsTransactionalUnderConcurrentAppends() throws Exception {
        var db = TestConversations.create();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = IntStream.range(0, 16).mapToObj(n -> executor.submit(() ->
                db.repository().append(1, "test-001", List.of(new ConversationRepository.NewMessage("USER", "m" + n)), null))).toList();
            for (var future : futures) future.get();
        }
        assertEquals(16, db.repository().requireOwned(1, "test-001").messageCount());
        assertEquals(IntStream.rangeClosed(1, 16).mapToObj(n -> (long) n).toList(),
                db.repository().history(1, "test-001", 0, 100).items().stream().map(row -> row.sequenceNo()).toList());
    }

    @Test void deletionRollsBackMessagesIfConversationDeletionFails() {
        var db = TestConversations.create();
        db.repository().append(1, "test-001", List.of(new ConversationRepository.NewMessage("USER", "retained")), null);
        db.jdbc().execute("CREATE TABLE deletion_guard (owner BIGINT, cid VARCHAR(128), FOREIGN KEY(owner,cid) REFERENCES chat_conversation(user_id,conversation_id))");
        db.jdbc().update("INSERT INTO deletion_guard VALUES (1,'test-001')");
        assertThrows(org.springframework.dao.DataAccessException.class, () -> db.service().delete("test-001"));
        assertEquals(1, db.service().history("test-001", 0, 100).items().size());
    }

    @Test void aiFailureKeepsUserWithoutFakeAssistantAndCanBeRetried() {
        var db = TestConversations.create();
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("model unavailable"))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Recovered")))));
        VectorStore vectors = mock(VectorStore.class);
        when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        ChatService chat = new ChatService(ChatClient.builder(model), new ResumeTools(mock(UserProfileService.class)),
                new JobAnalysisTools(mock(JobService.class)), new GreetingTools(mock(JobService.class)),
                new JobPersistenceTools(mock(JobPersistenceService.class)), new ApplicationTools(mock(ApplicationService.class)),
                db.memory(), QuestionAnswerAdvisor.builder(vectors).build(), null, db.service());
        assertThrows(KnowledgeException.class, () -> chat.reply("test-001", "Retain failed request"));
        assertEquals(List.of("USER"), db.service().history("test-001", 0, 100).items().stream().map(row -> row.role()).toList());
        assertEquals("Recovered", chat.reply("test-001", "Retry"));
        assertEquals(3, db.service().history("test-001", 0, 100).items().size());
    }

    @Test void overlappingOperationsReturnConflictAndReleaseTheirLease() {
        var db = TestConversations.create();
        try (var lease = db.service().beginChat("test-001")) {
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> db.service().beginChat("test-001"));
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> db.service().delete("test-001"));
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> db.service().rename("test-001", "Busy"));
        }
        assertNotNull(db.service().rename("test-001", "Released"));
    }
}
