-- 1. Insert default machine VM001
INSERT INTO
    machines (
        id,
        machine_code,
        name,
        status,
        message,
        created_at,
        updated_at
    )
VALUES (
        '550e8400-e29b-41d4-a716-446655440000',
        'VM001',
        'Máy bán hàng tầng 1',
        'ACTIVE',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

INSERT INTO
    products (name, price_cents)
VALUES ('Still water', 150),
    ('Orange juice', 250);

INSERT INTO
    inventory (
        machine_id,
        product_id,
        quantity
    )
SELECT 1, id, 10
FROM products;