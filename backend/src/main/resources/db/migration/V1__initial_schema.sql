-- 1. Machines table
CREATE TABLE machines (
    id UUID PRIMARY KEY,
    machine_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    message VARCHAR(500),
    created_at TIMESTAMP
    WITH
        TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP
    WITH
        TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Products table
CREATE TABLE products (
    id UUID PRIMARY KEY,
    machine_id UUID NOT NULL REFERENCES machines (id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    image_url VARCHAR(500),
    price_vnd INTEGER NOT NULL CHECK (price_vnd > 0),
    slot_number INTEGER NOT NULL CHECK (slot_number IN (1, 2, 3)),
    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP
    WITH
        TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP
    WITH
        TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT uq_machine_slot UNIQUE (machine_id, slot_number)
);

-- 3. Orders table
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    public_order_code VARCHAR(50) NOT NULL UNIQUE,
    machine_id UUID NOT NULL REFERENCES machines (id),
    product_id UUID NOT NULL REFERENCES products (id),
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity = 1),
    unit_price_vnd INTEGER NOT NULL CHECK (unit_price_vnd > 0),
    total_amount_vnd INTEGER NOT NULL CHECK (total_amount_vnd > 0),
    transfer_reference VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_expires_at TIMESTAMP
    WITH
        TIME ZONE NOT NULL,
        paid_at TIMESTAMP
    WITH
        TIME ZONE,
        dispensing_started_at TIMESTAMP
    WITH
        TIME ZONE,
        completed_at TIMESTAMP
    WITH
        TIME ZONE,
        created_at TIMESTAMP
    WITH
        TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP
    WITH
        TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Order Items table
CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id),
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price_vnd INTEGER NOT NULL CHECK (unit_price_vnd > 0),
    subtotal_vnd INTEGER NOT NULL CHECK (subtotal_vnd > 0),
    created_at TIMESTAMP
    WITH
        TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE inventory (
    machine_id BIGINT NOT NULL REFERENCES machines (id),
    product_id BIGINT NOT NULL REFERENCES products (id),
    quantity INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    PRIMARY KEY (machine_id, product_id)
);