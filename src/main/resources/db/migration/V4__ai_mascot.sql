ALTER TABLE users ADD COLUMN is_ai TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE comments ADD COLUMN ai_detail LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(ai_detail));

INSERT INTO users (email, password_hash, nickname, character_id, is_ai, terms_agreed_at, privacy_agreed_at)
VALUES ('byeol@hawe.invalid', '!', '별이', NULL, 1, NOW(), NOW());
