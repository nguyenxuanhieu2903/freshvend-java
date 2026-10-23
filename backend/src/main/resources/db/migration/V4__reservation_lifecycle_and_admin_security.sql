CREATE TABLE reservations (
    id BIGSERIAL PRIMARY KEY,
    machine_id BIGINT NOT NULL REFERENCES machines(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX reservations_status_expiry_idx ON reservations (status, expires_at);
