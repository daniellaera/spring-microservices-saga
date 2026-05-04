DELETE FROM product
WHERE id NOT IN (
    SELECT MIN(id) FROM product GROUP BY name
);

ALTER TABLE product ADD CONSTRAINT uc_product_name UNIQUE (name);