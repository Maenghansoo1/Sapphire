-- Mission slots and submission answers (docs/BACKEND/BACKEND_DB.md).
-- Assumes an empty missions table; new required columns have no backfill.

ALTER TABLE `groups`
    ADD COLUMN `relation_type` VARCHAR(20) NOT NULL DEFAULT 'CASUAL',
    ADD COLUMN `daily_mission_count` TINYINT UNSIGNED NOT NULL DEFAULT 1,
    DROP COLUMN `mission_publish_time`,
    DROP COLUMN `mission_deadline_time`;

CREATE TABLE `group_mission_slots` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT,
    `group_id`       BIGINT NOT NULL,
    `slot_no`        TINYINT UNSIGNED NOT NULL,
    `publish_time`   TIME NULL,
    `deadline_time`  TIME NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_group_mission_slots_group` FOREIGN KEY (`group_id`) REFERENCES `groups` (`id`),
    CONSTRAINT `uk_group_mission_slots_group_slot` UNIQUE (`group_id`, `slot_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `missions`
    ADD COLUMN `template` VARCHAR(40) NOT NULL,
    ADD COLUMN `params` JSON NOT NULL,
    ADD COLUMN `slot_no` TINYINT UNSIGNED NOT NULL DEFAULT 1,
    ADD COLUMN `published_at` DATETIME NOT NULL,
    ADD COLUMN `deadline_at` DATETIME NOT NULL,
    ADD CONSTRAINT `uk_missions_group_date_slot` UNIQUE (`group_id`, `mission_date`, `slot_no`);

ALTER TABLE `submissions`
    CHANGE COLUMN `image_url` `media_url` VARCHAR(500) NULL,
    ADD COLUMN `media_type` VARCHAR(10) NULL,
    ADD COLUMN `number_value` TINYINT UNSIGNED NULL,
    ADD COLUMN `choice_value` CHAR(1) NULL,
    ADD COLUMN `answer_data` JSON NULL,
    ADD COLUMN `answer_text` VARCHAR(100) NULL,
    ADD COLUMN `answer_aliases` JSON NULL,
    ADD CONSTRAINT `uk_submissions_mission_user` UNIQUE (`mission_id`, `user_id`);

CREATE TABLE `submission_guesses` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT,
    `submission_id`  BIGINT NOT NULL,
    `user_id`        BIGINT NOT NULL,
    `guess_text`     VARCHAR(100) NOT NULL,
    `is_correct`     BOOLEAN NOT NULL,
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_submission_guesses_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`),
    CONSTRAINT `fk_submission_guesses_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    INDEX `idx_submission_guesses_submission_user` (`submission_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
