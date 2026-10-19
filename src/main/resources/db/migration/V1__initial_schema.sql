-- Development DB reset and V1 rewritten (2026-10-12, docs/BACKEND/CONTRACT_WORK_PLAN.md).

CREATE TABLE `characters` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `svg_key` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_characters_svg_key` (`svg_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `nickname` varchar(50) NOT NULL,
  `character_id` bigint(20) DEFAULT NULL,
  `profile_image_url` varchar(500) DEFAULT NULL,
  `token_version` int(11) NOT NULL DEFAULT 0,
  `terms_agreed_at` datetime NOT NULL,
  `privacy_agreed_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`),
  KEY `fk_users_character` (`character_id`),
  CONSTRAINT `fk_users_character` FOREIGN KEY (`character_id`) REFERENCES `characters` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `groups` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `owner_id` bigint(20) NOT NULL,
  `invite_code` char(6) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `relation_type` varchar(20) NOT NULL DEFAULT 'CASUAL',
  `daily_mission_count` tinyint(3) unsigned NOT NULL DEFAULT 1,
  `start_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_groups_invite_code` (`invite_code`),
  KEY `fk_groups_owner` (`owner_id`),
  CONSTRAINT `fk_groups_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `group_members` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `group_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `role` varchar(20) NOT NULL,
  `joined_at` datetime NOT NULL DEFAULT current_timestamp(),
  `nickname_override` varchar(50) DEFAULT NULL,
  `character_id_override` bigint(20) DEFAULT NULL,
  `profile_image_url_override` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_members_group_user` (`group_id`,`user_id`),
  KEY `fk_group_members_user` (`user_id`),
  KEY `fk_group_members_character` (`character_id_override`),
  CONSTRAINT `fk_group_members_group` FOREIGN KEY (`group_id`) REFERENCES `groups` (`id`),
  CONSTRAINT `fk_group_members_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_group_members_character` FOREIGN KEY (`character_id_override`) REFERENCES `characters` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `group_mission_slots` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `group_id` bigint(20) NOT NULL,
  `slot_no` tinyint(3) unsigned NOT NULL,
  `publish_time` time DEFAULT NULL,
  `deadline_time` time DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_mission_slots_group_slot` (`group_id`,`slot_no`),
  CONSTRAINT `fk_group_mission_slots_group` FOREIGN KEY (`group_id`) REFERENCES `groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `missions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `group_id` bigint(20) NOT NULL,
  `content` text NOT NULL,
  `category` varchar(50) NOT NULL,
  `mission_date` date NOT NULL,
  `template` varchar(40) NOT NULL,
  `params` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`params`)),
  `slot_no` tinyint(3) unsigned NOT NULL DEFAULT 1,
  `published_at` datetime NOT NULL,
  `deadline_at` datetime NOT NULL,
  `generation_source` varchar(20) DEFAULT NULL,
  `experiment_arm` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_missions_group_date_slot` (`group_id`,`mission_date`,`slot_no`),
  KEY `idx_missions_experiment` (`experiment_arm`,`mission_date`),
  CONSTRAINT `fk_missions_group` FOREIGN KEY (`group_id`) REFERENCES `groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `submissions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `mission_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `media_url` varchar(500) DEFAULT NULL,
  `caption` text DEFAULT NULL,
  `submitted_at` datetime NOT NULL DEFAULT current_timestamp(),
  `is_late` tinyint(1) NOT NULL DEFAULT 0,
  `media_type` varchar(10) DEFAULT NULL,
  `number_value` tinyint(3) unsigned DEFAULT NULL,
  `choice_value` char(1) DEFAULT NULL,
  `answer_data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`answer_data`)),
  `answer_text` varchar(100) DEFAULT NULL,
  `answer_aliases` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`answer_aliases`)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_submissions_mission_user` (`mission_id`,`user_id`),
  KEY `fk_submissions_user` (`user_id`),
  CONSTRAINT `fk_submissions_mission` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`),
  CONSTRAINT `fk_submissions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `submission_ai_reviews` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `submission_id` bigint(20) NOT NULL,
  `kind` varchar(20) NOT NULL DEFAULT 'TOPIC_FIT',
  `status` varchar(10) NOT NULL,
  `fit_score` double DEFAULT NULL,
  `badge` varchar(30) DEFAULT NULL,
  `comment` varchar(100) DEFAULT NULL,
  `model_version` varchar(60) DEFAULT NULL,
  `attempts` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_reviews_submission` (`submission_id`,`kind`),
  CONSTRAINT `fk_ai_reviews_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `group_insights` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `group_id` bigint(20) NOT NULL,
  `month` char(7) NOT NULL,
  `status` varchar(10) NOT NULL,
  `payload` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`payload`)),
  `generated_at` datetime DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `attempts` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_insights_group_month` (`group_id`,`month`),
  CONSTRAINT `fk_group_insights_group` FOREIGN KEY (`group_id`) REFERENCES `groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `submission_guesses` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `submission_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `guess_text` varchar(100) NOT NULL,
  `is_correct` tinyint(1) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_submission_guesses_user` (`user_id`),
  KEY `idx_submission_guesses_submission_user` (`submission_id`,`user_id`),
  CONSTRAINT `fk_submission_guesses_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`),
  CONSTRAINT `fk_submission_guesses_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `comments` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `submission_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `content` text NOT NULL,
  `image_url` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_comments_submission` (`submission_id`),
  KEY `fk_comments_user` (`user_id`),
  CONSTRAINT `fk_comments_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`),
  CONSTRAINT `fk_comments_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `comment_mentions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `comment_id` bigint(20) NOT NULL,
  `mentioned_user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_comment_mentions_comment` (`comment_id`),
  KEY `fk_comment_mentions_user` (`mentioned_user_id`),
  CONSTRAINT `fk_comment_mentions_comment` FOREIGN KEY (`comment_id`) REFERENCES `comments` (`id`),
  CONSTRAINT `fk_comment_mentions_user` FOREIGN KEY (`mentioned_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `reactions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `submission_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `sticker_type` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reactions_submission_user_sticker` (`submission_id`,`user_id`,`sticker_type`),
  KEY `fk_reactions_submission` (`submission_id`),
  KEY `fk_reactions_user` (`user_id`),
  CONSTRAINT `fk_reactions_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`),
  CONSTRAINT `fk_reactions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pokes` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `from_user_id` bigint(20) NOT NULL,
  `to_user_id` bigint(20) NOT NULL,
  `group_id` bigint(20) NOT NULL,
  `mission_id` bigint(20) NOT NULL,
  `type` varchar(20) NOT NULL,
  `sent_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pokes_from_to_mission_type` (`from_user_id`,`to_user_id`,`mission_id`,`type`),
  KEY `fk_pokes_mission` (`mission_id`),
  KEY `fk_pokes_from_user` (`from_user_id`),
  KEY `fk_pokes_to_user` (`to_user_id`),
  KEY `fk_pokes_group` (`group_id`),
  CONSTRAINT `fk_pokes_from_user` FOREIGN KEY (`from_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_pokes_group` FOREIGN KEY (`group_id`) REFERENCES `groups` (`id`),
  CONSTRAINT `fk_pokes_mission` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`),
  CONSTRAINT `fk_pokes_to_user` FOREIGN KEY (`to_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notifications` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `from_user_id` bigint(20) DEFAULT NULL,
  `to_user_id` bigint(20) NOT NULL,
  `type` varchar(30) NOT NULL,
  `payload` text DEFAULT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_notifications_from_user` (`from_user_id`),
  KEY `fk_notifications_to_user` (`to_user_id`),
  CONSTRAINT `fk_notifications_from_user` FOREIGN KEY (`from_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_notifications_to_user` FOREIGN KEY (`to_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
