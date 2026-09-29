-- Stage 16: run metadata only. Existing chat and business data are preserved.
USE career_pilot_ai;

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

-- MySQL 8 lacks ADD COLUMN IF NOT EXISTS. Inspect the schema for repeatable upgrades.
DROP PROCEDURE IF EXISTS upgrade_v16;
DELIMITER $$
CREATE PROCEDURE upgrade_v16()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='chat_message' AND column_name='status') THEN
        ALTER TABLE chat_message ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_request_log' AND column_name='time_to_first_token_ms') THEN
        ALTER TABLE ai_request_log ADD COLUMN time_to_first_token_ms BIGINT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_request_log' AND column_name='stream_duration_ms') THEN
        ALTER TABLE ai_request_log ADD COLUMN stream_duration_ms BIGINT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_request_log' AND column_name='output_chunk_count') THEN
        ALTER TABLE ai_request_log ADD COLUMN output_chunk_count INT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_request_log' AND column_name='cancelled') THEN
        ALTER TABLE ai_request_log ADD COLUMN cancelled BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END$$
DELIMITER ;
CALL upgrade_v16();
DROP PROCEDURE upgrade_v16;
