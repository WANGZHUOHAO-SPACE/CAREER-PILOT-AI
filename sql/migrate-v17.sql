-- Repeatable index upgrade for the actual user-owned application list query.
USE career_pilot_ai;
DROP PROCEDURE IF EXISTS upgrade_v17;
DELIMITER $$
CREATE PROCEDURE upgrade_v17()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='application' AND index_name='idx_application_user_id') THEN
        ALTER TABLE application ADD INDEX idx_application_user_id (user_id, id);
    END IF;
END$$
DELIMITER ;
CALL upgrade_v17();
DROP PROCEDURE upgrade_v17;
