-- Align profile characters with the iOS/Android apps (docs/BACKEND/CONTRACT_DECISIONS.md).
-- The apps ship 7 characters; svg_key equals the app's character key (ios ProfileCharacter rawValue).
-- 별이(star) has no app artwork, so users who picked it fall back to the default (NULL).
UPDATE users SET character_id = NULL WHERE character_id = 4;
DELETE FROM characters WHERE id = 4;

UPDATE characters SET svg_key = 'mongsil'   WHERE id = 1;
UPDATE characters SET svg_key = 'ipsagwi'   WHERE id = 2;
UPDATE characters SET svg_key = 'gomdol'    WHERE id = 3;
UPDATE characters SET svg_key = 'mulbangul' WHERE id = 5;
UPDATE characters SET svg_key = 'gamja'     WHERE id = 6;
UPDATE characters SET svg_key = 'kkotnim'   WHERE id = 7;
UPDATE characters SET svg_key = 'pilleum'   WHERE id = 8;
