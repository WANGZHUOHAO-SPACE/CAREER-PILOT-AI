CREATE DATABASE IF NOT EXISTS career_pilot_ai
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE career_pilot_ai;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Disabled owner for historical/demo rows. The value is not a usable BCrypt hash.
INSERT IGNORE INTO users (id, username, email, password_hash, display_name)
VALUES (1, 'legacy-disabled', 'legacy-disabled@invalid.local', '!', 'Legacy data');

CREATE TABLE IF NOT EXISTS user_profile (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    major VARCHAR(100),
    target_position VARCHAR(150),
    skills TEXT,
    introduction TEXT,
    UNIQUE KEY uq_profile_user (user_id),
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS job (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    company VARCHAR(200),
    position VARCHAR(200),
    jd TEXT NOT NULL,
    location VARCHAR(100),
    salary VARCHAR(100),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_job_user (user_id, id),
    CONSTRAINT fk_job_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS application (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SAVED',
    apply_time DATETIME NULL,
    remark TEXT,
    INDEX idx_application_job_id (job_id),
    INDEX idx_application_status (status),
    INDEX idx_application_user_status (user_id, status),
    INDEX idx_application_user_id (user_id, id),
    CONSTRAINT fk_application_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_application_job FOREIGN KEY (job_id) REFERENCES job(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ai_request_log (
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
    time_to_first_token_ms BIGINT NULL,
    stream_duration_ms BIGINT NULL,
    output_chunk_count INT NULL,
    cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    tool_call_count INT NOT NULL DEFAULT 0,
    rag_used BOOLEAN NOT NULL DEFAULT FALSE,
    error_type VARCHAR(40) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_ai_request_id (request_id),
    INDEX idx_ai_request_user_created (user_id, created_at),
    CONSTRAINT fk_ai_request_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ai_trace_event (
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

-- 演示简历属于不可登录的 legacy owner；注册用户会得到自己的空简历。
INSERT IGNORE INTO user_profile
    (id, user_id, name, major, target_position, skills, introduction)
VALUES
    (1, 1, '林同学（演示）', '软件工程', 'Java后端开发实习',
     'Java,Spring Boot,MySQL,Git,AI辅助开发',
     '演示简历：软件工程专业学生，求职方向为Java后端开发实习。');

-- Stage 15 upgrade for an existing MySQL database. Safe to execute again; no old tables are changed.

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
    status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED',
    content MEDIUMTEXT NOT NULL,
    sequence_no BIGINT NOT NULL,
    request_id CHAR(36) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_message_user_conversation_sequence (user_id, conversation_id, sequence_no),
    CONSTRAINT ck_chat_message_role CHECK (role IN ('USER','ASSISTANT','SYSTEM','TOOL')),
    CONSTRAINT fk_message_conversation FOREIGN KEY (user_id, conversation_id)
        REFERENCES chat_conversation(user_id, conversation_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ai_run (
    run_id CHAR(36) NOT NULL PRIMARY KEY,
    request_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    time_to_first_token_ms BIGINT NULL,
    stream_duration_ms BIGINT NULL,
    output_chunk_count INT NOT NULL DEFAULT 0,
    INDEX idx_run_user_started (user_id, started_at),
    CONSTRAINT ck_run_status CHECK (status IN ('RUNNING','COMPLETED','FAILED','CANCELLED')),
    CONSTRAINT fk_run_conversation FOREIGN KEY (user_id, conversation_id)
        REFERENCES chat_conversation(user_id, conversation_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


