package com.careerpilot.conversation;

import com.careerpilot.conversation.ConversationDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService service;
    public ConversationController(ConversationService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Conversation create() { return service.create(); }
    @GetMapping
    public Page<Conversation> list(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return service.list(page, size);
    }
    @GetMapping("/{conversationId}/messages")
    public Page<HistoryMessage> history(@PathVariable String conversationId, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="100") int size) { return service.history(conversationId, page, size); }
    @PatchMapping("/{conversationId}")
    public Conversation rename(@PathVariable String conversationId, @Valid @RequestBody RenameRequest request) {
        return service.rename(conversationId, request.title());
    }
    @DeleteMapping("/{conversationId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String conversationId) { service.delete(conversationId); }
}
