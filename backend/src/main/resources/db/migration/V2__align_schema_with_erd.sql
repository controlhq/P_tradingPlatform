-- =====================================================================
-- V2: align schema with the ERD in README.md
-- =====================================================================

-- ---------------------------------------------------------------------
-- users: email max 254 (RFC 5321)
-- ---------------------------------------------------------------------
ALTER TABLE users ALTER COLUMN email TYPE VARCHAR(254);

-- ---------------------------------------------------------------------
-- refresh_tokens: fixed-length SHA-256 hex
-- ---------------------------------------------------------------------
ALTER TABLE refresh_tokens ALTER COLUMN token_hash TYPE CHAR(64);

-- ---------------------------------------------------------------------
-- accounts: exactly one account per user, single currency,
--           blocked -> blocked_balance
-- ---------------------------------------------------------------------
ALTER TABLE accounts DROP CONSTRAINT uq_accounts_user_currency;
ALTER TABLE accounts DROP COLUMN currency;
ALTER TABLE accounts ADD CONSTRAINT uq_accounts_user UNIQUE (user_id);

ALTER TABLE accounts RENAME COLUMN blocked TO blocked_balance;
ALTER TABLE accounts RENAME CONSTRAINT ck_accounts_blocked        TO ck_accounts_blocked_balance;
ALTER TABLE accounts RENAME CONSTRAINT ck_accounts_blocked_le_bal TO ck_accounts_blocked_balance_le_bal;

-- ---------------------------------------------------------------------
-- instruments: single currency
-- ---------------------------------------------------------------------
ALTER TABLE instruments DROP COLUMN currency;

-- ---------------------------------------------------------------------
-- orders: status PENDING -> NEW, quantity INT 1..10000,
--         reserved_amount (funds blocked for BUY)
-- ---------------------------------------------------------------------
ALTER TABLE orders DROP CONSTRAINT ck_orders_status;
UPDATE orders SET status = 'NEW' WHERE status = 'PENDING';
ALTER TABLE orders ALTER COLUMN status SET DEFAULT 'NEW';
ALTER TABLE orders ADD CONSTRAINT ck_orders_status
    CHECK (status IN ('NEW', 'FILLED', 'CANCELLED', 'REJECTED'));

ALTER TABLE orders DROP CONSTRAINT ck_orders_quantity;
ALTER TABLE orders ALTER COLUMN quantity TYPE INTEGER;
ALTER TABLE orders ADD CONSTRAINT ck_orders_quantity CHECK (quantity BETWEEN 1 AND 10000);

ALTER TABLE orders ADD COLUMN reserved_amount NUMERIC(19,4) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD CONSTRAINT ck_orders_reserved_nonneg CHECK (reserved_amount >= 0);
ALTER TABLE orders ADD CONSTRAINT ck_orders_reserved_buy_only CHECK (side = 'BUY' OR reserved_amount = 0);

-- ---------------------------------------------------------------------
-- positions: quantity INT > 0 (empty positions are deleted),
--            blocked -> blocked_quantity INT
-- ---------------------------------------------------------------------
DELETE FROM positions WHERE quantity = 0;

ALTER TABLE positions DROP CONSTRAINT ck_positions_quantity;
ALTER TABLE positions ALTER COLUMN quantity DROP DEFAULT;
ALTER TABLE positions ALTER COLUMN quantity TYPE INTEGER;
ALTER TABLE positions ADD CONSTRAINT ck_positions_quantity CHECK (quantity > 0);

ALTER TABLE positions RENAME COLUMN blocked TO blocked_quantity;
ALTER TABLE positions ALTER COLUMN blocked_quantity TYPE INTEGER;
ALTER TABLE positions RENAME CONSTRAINT ck_positions_blocked       TO ck_positions_blocked_quantity;
ALTER TABLE positions RENAME CONSTRAINT ck_positions_blocked_le_q  TO ck_positions_blocked_quantity_le_q;

-- ---------------------------------------------------------------------
-- transactions: WITHDRAWAL -> WITHDRAW; account is implied by user (1:1)
-- ---------------------------------------------------------------------
ALTER TABLE transactions DROP CONSTRAINT ck_transactions_type;
UPDATE transactions SET type = 'WITHDRAW' WHERE type = 'WITHDRAWAL';
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_type
    CHECK (type IN ('DEPOSIT', 'WITHDRAW', 'BUY', 'SELL'));

ALTER TABLE transactions DROP COLUMN account_id;
