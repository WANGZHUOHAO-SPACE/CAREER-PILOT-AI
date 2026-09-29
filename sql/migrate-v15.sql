-- Stage 15 upgrade for an existing MySQL database. Safe to execute again; no old tables are changed.
USE career_pilot_ai;

CREATE TABLE IF NOT EXISTS chat_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL DEFAULT 'New Chat',
    title_manually_set BOOLEAN NOT NULL DEFAULT FALSE,
    message_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_message_at DATETIME(6) NULL,
    UNIQUE KEY uq_conversation_user_id (user_id, conversation_id),
    INDEX idx_conversation_user_updated (user_id, updated_at),
    CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    sequence_no BIGINT NOT NULL,
    request_id CHAR(36) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_message_user_conversation_sequence (user_id, conversation_id, sequence_no),
    CONSTRAINT ck_chat_message_role CHECK (role IN ('USER','ASSISTANT','SYSTEM','TOOL')),
    CONSTRAINT fk_message_conversation FOREIGN KEY (user_id, conversation_id)
        REFERENCES chat_conversation(user_id, conversation_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
