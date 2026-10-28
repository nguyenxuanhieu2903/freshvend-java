INSERT INTO machines (name, location) VALUES ('Development machine', 'Local test');

INSERT INTO products (name, price_cents) VALUES
    ('Still water', 150),
    ('Orange juice', 250);

INSERT INTO inventory (machine_id, product_id, quantity)
SELECT 1, id, 10 FROM products;
