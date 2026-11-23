CREATE TABLE `user_badges` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `badge_code` varchar(40) NOT NULL,
  `status` varchar(20) NOT NULL,
  `earned_at` datetime NOT NULL,
  `token_id` varchar(80) DEFAULT NULL,
  `tx_hash` char(66) DEFAULT NULL,
  `chain_id` int(11) DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_badges_user_code` (`user_id`, `badge_code`),
  KEY `idx_user_badges_user_earned` (`user_id`, `earned_at`),
  CONSTRAINT `fk_user_badges_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `users` ADD COLUMN `featured_badge_code` varchar(40) DEFAULT NULL;
