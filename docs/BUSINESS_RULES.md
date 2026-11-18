# Business Rules

Each rule has a stable ID (`BRxx`). Tests covering a rule are named `BRxx_description`.

| ID   | Rule | Enforced in |
|------|------|-------------|
| BR01 | Account balance can never be negative. | DB `ck_accounts_balance` |
| BR02 | Blocked funds can never exceed the account balance. | DB `ck_accounts_blocked_balance_le_bal` |
| BR03 | Order quantity must be between 1 and 10000. | DB `ck_orders_quantity` |
| BR04 | A LIMIT order requires a limit price; other order types must not have one. | DB `ck_orders_limit_price` |
| BR05 | A STOP order requires a stop price; other order types must not have one. | DB `ck_orders_stop_price` |
| BR06 | Blocked shares in a position can never exceed the held quantity (no short selling). | DB `ck_positions_blocked_quantity_le_q` |
| BR07 | User email is unique (case-insensitive) and stored trimmed and lowercase; a duplicate returns 409 `EMAIL_TAKEN`. | `AuthService.register`, DB `uq_users_email`, `ck_users_email_lower` |
| BR08 | Only a hash of a refresh token is stored; a revoked or expired token cannot be used. | `RefreshToken.isActive` |
| BR09 | Each user has exactly one cash account. | DB `uq_accounts_user` |
| BR10 | Funds are reserved only for BUY orders; SELL orders reserve nothing. | DB `ck_orders_reserved_buy_only` |
| BR11 | A position exists only while it holds at least one share. | DB `ck_positions_quantity` |
| BR12 | Registration creates the user (role USER) and their cash account with balance 0 in one transaction. | `AuthService.register` |
| BR13 | Password is 8–128 characters and contains at least one letter and one digit. | `RegisterRequest` (Bean Validation) |
| BR14 | Login returns a 15-minute HS256 access token (`sub`=user id, `role`). Wrong password, unknown email and disabled account all return the identical 401 "Invalid credentials". | `AuthService.login` |
| BR15 | Refresh tokens are 256-bit random values valid for 7 days; only their SHA-256 hash is stored. | `TokenService`, `AuthService.issueTokens` |
| BR16 | Refresh rotates the pair and revokes the old token. Presenting a revoked token revokes all of the user's tokens and returns 401. | `AuthService.refresh` |
| BR17 | Logout revokes the given refresh token; it is idempotent and always returns 204. | `AuthService.logout` |
| BR18 | The current price of an instrument is its most recent price tick (latest `ts`; ticks with equal `ts` are ordered by id). | `PriceTickRepository` |
| BR19 | The instrument list shows only active instruments, ordered by symbol. An inactive instrument is still returned by id (with `active=false`); an unknown id returns 404 `INSTRUMENT_NOT_FOUND`. | `InstrumentService` |
| BR20 | An instrument symbol matches `^[A-Z0-9.]{1,16}$` and is unique; a duplicate returns 409 `SYMBOL_TAKEN`. | `CreateInstrumentRequest`, `InstrumentService.create`, DB `uq_instruments_symbol`, `ck_instruments_symbol` |
| BR21 | Only an ADMIN creates instruments. A new instrument is active and gets its first price tick (the starting price, > 0, rounded HALF_UP to 4 places) in the same transaction. | `AdminInstrumentController`, `InstrumentService.create` |
