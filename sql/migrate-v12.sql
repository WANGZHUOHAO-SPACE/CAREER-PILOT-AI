-- One-time migration for an existing pre-v12 database. Back up first; run once before starting v12.
USE career_pilot_ai;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- No one can sign in as this account. Historical rows stay unavailable until explicitly reassigned.
INSERT INTO users (id, username, email, password_hash, display_name)
VALUES (1, 'legacy-disabled', 'legacy-disabled@invalid.local', '!', 'Legacy data');

ALTER TABLE user_profile ADD COLUMN user_id BIGINT NULL;
ALTER TABLE job ADD COLUMN user_id BIGINT NULL;
ALTER TABLE application ADD COLUMN user_id BIGINT NULL;
UPDATE user_profile SET user_id = 1 WHERE user_id IS NULL;
UPDATE job SET user_id = 1 WHERE user_id IS NULL;
UPDATE application SET user_id = 1 WHERE user_id IS NULL;
ALTER TABLE user_profile MODIFY user_id BIGINT NOT NULL,
    ADD INDEX idx_profile_user (user_id),
    ADD CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE job MODIFY user_id BIGINT NOT NULL,
    ADD INDEX idx_job_user (user_id, id),
    ADD CONSTRAINT fk_job_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE application MODIFY user_id BIGINT NOT NULL,
    ADD INDEX idx_application_user_status (user_id, status),
    ADD CONSTRAINT fk_application_user FOREIGN KEY (user_id) REFERENCES users(id);
