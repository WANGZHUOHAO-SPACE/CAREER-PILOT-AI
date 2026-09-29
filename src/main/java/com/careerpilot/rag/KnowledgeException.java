package com.careerpilot.rag;

import org.springframework.http.HttpStatus;

public class KnowledgeException extends RuntimeException {

    private final HttpStatus status;

    public KnowledgeException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public KnowledgeException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
