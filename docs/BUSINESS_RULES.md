# Business Rules

Each rule has a stable ID (`BRxx`). Tests covering a rule are named `BRxx_description`.

| ID   | Rule | Enforced in |
|------|------|-------------|
| BR01 | Account balance can never be negative. | DB `ck_accounts_balance` |
| BR02 | Blocked funds can never exceed the account balance. | DB `ck_accounts_blocked_le_bal` |
| BR03 | Order quantity must be greater than zero. | DB `ck_orders_quantity` |
| BR04 | A LIMIT order requires a limit price; other order types must not have one. | DB `ck_orders_limit_price` |
| BR05 | A STOP order requires a stop price; other order types must not have one. | DB `ck_orders_stop_price` |
| BR06 | Blocked shares in a position can never exceed the held quantity (no short selling). | DB `ck_positions_blocked_le_q` |
| BR07 | User email is unique and stored lowercase. | DB `uq_users_email`, `ck_users_email_lower` |
| BR08 | Only a hash of a refresh token is stored; a revoked or expired token cannot be used. | `RefreshToken.isActive` |
