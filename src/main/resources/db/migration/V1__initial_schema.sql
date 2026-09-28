-- HA'WE initial schema (based on docs/DB/DB_DESIGN_ver0.1.md)

CREATE TABLE `characters` (
    `id`        BIGINT NOT NULL AUTO_INCREMENT,
    `name`      VARCHAR(50)  NOT NULL,
    `svg_key`   VARCHAR(100) NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT,
    `nickname`       VARCHAR(50)  NOT NULL,
    `login_id`       VARCHAR(50)  NOT NULL,
    `password_hash`  VARCHAR(255) NOT NULL,
    `email`          VARCHAR(255) NULL,
    `character_id`   BIGINT NULL,
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_login_id` (`login_id`),
    CONSTRAINT `fk_users_character` FOREIGN KEY (`character_id`) REFERENCES `characters` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `groups` (
    `id`                     BIGINT NOT NULL AUTO_INCREMENT,
    `name`                   VARCHAR(100) NOT NULL,
    `owner_id`               BIGINT NOT NULL,
    `mission_publish_time`   TIME NOT NULL,
    `mission_deadline_time`  TIME NOT NULL,
    `created_at`             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_groups_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `group_members` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `group_id`   BIGINT NOT NULL,
    `user_id`    BIGINT NOT NULL,
    `role`       VARCHAR(20) NOT NULL,
    `joined_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_group_members_group` FOREIGN KEY (`group_id`) REFERENCES `groups` (`id`),
    CONSTRAINT `fk_group_members_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `missions` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT,
    `group_id`      BIGINT NOT NULL,
    `content`       TEXT NOT NULL,
    `category`      VARCHAR(50) NOT NULL,
    `mission_date`  DATE NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_missions_group` FOREIGN KEY (`group_id`) REFERENCES `groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `submissions` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT,
    `mission_id`    BIGINT NOT NULL,
    `user_id`       BIGINT NOT NULL,
    `image_url`     VARCHAR(500) NOT NULL,
    `caption`       TEXT NULL,
    `submitted_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `is_late`       BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_submissions_mission` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`),
    CONSTRAINT `fk_submissions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `comments` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT,
    `submission_id`  BIGINT NOT NULL,
    `user_id`        BIGINT NOT NULL,
    `content`        TEXT NOT NULL,
    `image_url`      VARCHAR(500) NULL,
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_comments_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`),
    CONSTRAINT `fk_comments_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `comment_mentions` (
    `id`                  BIGINT NOT NULL AUTO_INCREMENT,
    `comment_id`          BIGINT NOT NULL,
    `mentioned_user_id`   BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_comment_mentions_comment` FOREIGN KEY (`comment_id`) REFERENCES `comments` (`id`),
    CONSTRAINT `fk_comment_mentions_user` FOREIGN KEY (`mentioned_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `reactions` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT,
    `submission_id`  BIGINT NOT NULL,
    `user_id`        BIGINT NOT NULL,
    `sticker_type`   VARCHAR(50) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_reactions_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`),
    CONSTRAINT `fk_reactions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pokes` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT,
    `from_user_id`   BIGINT NOT NULL,
    `to_user_id`     BIGINT NOT NULL,
    `group_id`       BIGINT NOT NULL,
    `type`           VARCHAR(20) NOT NULL,
    `sent_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_pokes_from_user` FOREIGN KEY (`from_user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_pokes_to_user` FOREIGN KEY (`to_user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_pokes_group` FOREIGN KEY (`group_id`) REFERENCES `groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notifications` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT,
    `from_user_id`   BIGINT NULL,
    `to_user_id`     BIGINT NOT NULL,
    `type`           VARCHAR(30) NOT NULL,
    `payload`        TEXT NULL,
    `is_read`        BOOLEAN NOT NULL DEFAULT FALSE,
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_notifications_from_user` FOREIGN KEY (`from_user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_notifications_to_user` FOREIGN KEY (`to_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
