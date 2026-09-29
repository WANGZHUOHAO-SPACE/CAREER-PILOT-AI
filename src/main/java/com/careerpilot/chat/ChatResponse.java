package com.careerpilot.chat;

public record ChatResponse(String reply, String requestId) {
    public ChatResponse(String reply) { this(reply, null); }
}
