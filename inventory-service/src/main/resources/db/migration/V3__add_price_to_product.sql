ALTER TABLE product ADD COLUMN IF NOT EXISTS price NUMERIC(10,2) NOT NULL DEFAULT 0.00;

INSERT INTO product (name, quantity, price)
VALUES ('iPhone 17', 50, 999.99)
ON CONFLICT (name) DO UPDATE SET price = 999.99;

INSERT INTO product (name, quantity, price)
VALUES ('MacBook Pro', 20, 1299.99)
ON CONFLICT (name) DO UPDATE SET price = 1299.99;

INSERT INTO product (name, quantity, price)
VALUES ('iPad Pro', 30, 799.99)
ON CONFLICT (name) DO UPDATE SET price = 799.99;
