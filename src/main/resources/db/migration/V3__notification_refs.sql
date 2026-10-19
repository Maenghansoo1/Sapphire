ALTER TABLE notifications
  ADD COLUMN group_id BIGINT NULL,
  ADD COLUMN mission_id BIGINT NULL,
  ADD COLUMN submission_id BIGINT NULL,
  ADD KEY idx_notifications_inbox (to_user_id, created_at, id),
  ADD KEY idx_notifications_unread (to_user_id, is_read);
