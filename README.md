Diagram ERD:

```mermaid
erDiagram
    USERS ||--o{ REFRESH_TOKENS : "has"
    USERS ||--|| ACCOUNTS : "owns"
    USERS ||--o{ ORDERS : "places"
    USERS ||--o{ POSITIONS : "holds"
    USERS ||--o{ TRANSACTIONS : "has"
    INSTRUMENTS ||--o{ PRICE_TICKS : "priced by"
    INSTRUMENTS ||--o{ ORDERS : "traded in"
    INSTRUMENTS ||--o{ POSITIONS : "held as"
    ORDERS |o--o{ TRANSACTIONS : "settled by"

    USERS {
        bigint id PK
        varchar email UK "max 254, lowercase"
        varchar password_hash "argon2id"
        varchar role "USER | ADMIN"
        timestamptz created_at
    }
    REFRESH_TOKENS {
        bigint id PK
        bigint user_id FK
        char token_hash UK "SHA-256 hex"
        timestamptz expires_at
        timestamptz revoked_at "nullable"
        timestamptz created_at
    }
    ACCOUNTS {
        bigint id PK
        bigint user_id FK,UK "1:1"
        numeric balance "19,4 | >= 0"
        numeric blocked_balance "19,4 | 0..balance"
        bigint version "optimistic lock"
        timestamptz updated_at
    }
    INSTRUMENTS {
        bigint id PK
        varchar symbol UK "e.g. AAPL"
        varchar name
        boolean active
        timestamptz created_at
    }
    PRICE_TICKS {
        bigint id PK
        bigint instrument_id FK
        numeric price "19,4 | > 0"
        timestamptz ts "idx (instrument_id, ts)"
    }
    ORDERS {
        bigint id PK
        bigint user_id FK
        bigint instrument_id FK
        varchar side "BUY | SELL"
        varchar type "MARKET | LIMIT | STOP"
        int quantity "1..10000"
        numeric limit_price "required iff LIMIT"
        numeric stop_price "required iff STOP"
        numeric reserved_amount "funds blocked for BUY"
        varchar status "NEW | FILLED | CANCELLED | REJECTED"
        numeric filled_price "nullable"
        timestamptz created_at
        timestamptz filled_at "nullable"
    }
    POSITIONS {
        bigint id PK
        bigint user_id FK "UK (user_id, instrument_id)"
        bigint instrument_id FK
        int quantity "> 0"
        int blocked_quantity "0..quantity"
        numeric avg_price "19,4"
        bigint version "optimistic lock"
    }
    TRANSACTIONS {
        bigint id PK
        bigint user_id FK
        bigint order_id FK "nullable"
        varchar type "DEPOSIT | WITHDRAW | BUY | SELL"
        numeric amount "19,4 | > 0"
        numeric balance_after "19,4"
        timestamptz created_at
    }
```
