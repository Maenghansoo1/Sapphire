-- Default profile characters, in the order shown on frontend/myApp/www/pages/characters.html.
-- svg_key matches the image file name in frontend/myApp/www/img/characters/<svg_key>.svg.
INSERT INTO characters (id, name, svg_key) VALUES
  (1, '몽실이', 'monsili'),
  (2, '잎사귀', 'leaf'),
  (3, '곰돌이', 'bear'),
  (4, '별이',   'star'),
  (5, '물방울', 'drop'),
  (6, '감자',   'potato'),
  (7, '꽃님',   'flower'),
  (8, '필름이', 'film');

ALTER TABLE characters ADD CONSTRAINT uk_characters_svg_key UNIQUE (svg_key);
