INSERT INTO users (email, name)
SELECT '1@1.ww', 'Author'
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = '1@1.ww');

INSERT INTO users (email, name)
SELECT '2@2.ww', 'Liker'
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = '2@2.ww');
