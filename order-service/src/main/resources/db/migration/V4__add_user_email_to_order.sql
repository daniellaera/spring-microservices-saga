ALTER TABLE orders ADD COLUMN IF NOT EXISTS user_email VARCHAR(255);

UPDATE orders SET user_email = 'unknown@legacy.com' WHERE user_email IS NULL;
