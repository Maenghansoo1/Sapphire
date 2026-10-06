-- Keep the earliest membership where earlier joins created duplicate rows.
DELETE duplicate FROM group_members duplicate
JOIN group_members retained
  ON retained.group_id = duplicate.group_id
 AND retained.user_id = duplicate.user_id
 AND retained.id < duplicate.id;

ALTER TABLE group_members
ADD CONSTRAINT uk_group_members_group_user UNIQUE (group_id, user_id);
