-- One-time migration for an existing v12 database. Back up first and run once before starting v13.
USE career_pilot_ai;

CREATE TABLE ai_request_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    model_name VARCHAR(120) NULL,
    provider VARCHAR(32) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    input_tokens INT NULL,
    output_tokens INT NULL,
    total_tokens INT NULL,
    latency_ms BIGINT NOT NULL,
    tool_call_count INT NOT NULL DEFAULT 0,
    rag_used BOOLEAN NOT NULL DEFAULT FALSE,
    error_type VARCHAR(40) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_ai_request_id (request_id),
    INDEX idx_ai_request_user_created (user_id, created_at),
    CONSTRAINT fk_ai_request_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_trace_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_name VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL,
    duration_ms BIGINT NOT NULL,
    top_k INT NULL,
    chunk_count INT NULL,
    memory_message_count INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_trace_user_request (user_id, request_id),
    INDEX idx_ai_trace_user_type_created (user_id, event_type, created_at),
    CONSTRAINT fk_ai_trace_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
