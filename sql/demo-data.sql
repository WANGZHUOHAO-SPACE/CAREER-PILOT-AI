-- 虚构演示数据，仅在新建 MySQL 数据卷时由 Compose 自动导入。
USE career_pilot_ai;

INSERT IGNORE INTO job (id, user_id, company, position, jd, location, salary, create_time) VALUES
    (101, 1, '星河科技（演示）', 'Java 后端实习生', '参与 Spring Boot API 开发，熟悉 Java、MySQL 与 Git。', '杭州', '200-250元/天', CURRENT_TIMESTAMP),
    (102, 1, '远山软件（演示）', 'AI 应用开发实习生', '使用 Java、Spring AI 开发智能体应用，了解 RAG 与数据库。', '上海', '220-280元/天', CURRENT_TIMESTAMP),
    (103, 1, '青禾数字（演示）', '后端开发实习生', '维护 Java 服务，编写 SQL，参与接口联调和测试。', '南京', '180-230元/天', CURRENT_TIMESTAMP);

INSERT IGNORE INTO application (id, user_id, job_id, status, apply_time, remark) VALUES
    (101, 1, 101, 'SAVED', NULL, '演示记录：待投递'),
    (102, 1, 102, 'APPLIED', CURRENT_TIMESTAMP, '演示记录：已投递'),
    (103, 1, 103, 'INTERVIEW', CURRENT_TIMESTAMP, '演示记录：等待面试');
