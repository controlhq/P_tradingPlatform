# Business Rules

Each rule has a stable ID (`BRxx`). Tests covering a rule are named `BRxx_description`.

| ID   | Rule | Enforced in |
|------|------|-------------|
| BR01 | Account balance can never be negative. | DB `ck_accounts_balance` |
| BR02 | Blocked funds can never exceed the account balance. | DB `ck_accounts_blocked_balance_le_bal` |
| BR03 | Order quantity must be between 1 and 10000. | `PlaceOrderRequest`, DB `ck_orders_quantity` |
| BR04 | A LIMIT order requires a limit price; other order types must not have one. | `PlaceOrderRequest` (`@ValidOrderPrices`), DB `ck_orders_limit_price` |
| BR05 | A STOP order requires a stop price; other order types must not have one. | `PlaceOrderRequest` (`@ValidOrderPrices`), DB `ck_orders_stop_price` |
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
| BR22 | Only an ADMIN activates or deactivates an instrument. Deactivation hides it from the list (BR19) but keeps its data and price history; setting the current state again is a no-op returning 200. | `InstrumentService.update` |
| BR23 | The market is open on trading days (default Mon–Fri) from `market.open-time` (inclusive) to `market.close-time` (exclusive), local time in `market.zone` (Europe/Warsaw). With `market.always-open=true` it is always open. | `MarketHoursService` |
| BR24 | While the market is open (BR23), every `market.tick-interval-ms` each active instrument that has a price gets a new tick: the latest price times `1 + N(0,1) * market.volatility`, capped at a 50% drop per tick, rounded HALF_UP to 4 places and never below 0.0001. | `PriceSimulator` |
| BR25 | Price history returns the `limit` most recent ticks with `from <= ts <= to`, oldest first. `from` defaults to no lower bound, `to` to now, `limit` to 200 (allowed 1–1000, otherwise 400). `from` after `to` returns 400 `INVALID_TIME_RANGE`; an unknown instrument returns 404 `INSTRUMENT_NOT_FOUND`; an inactive instrument still has its history. | `InstrumentController.prices`, `InstrumentService.priceHistory` |
| BR26 | Once an hour, price ticks older than `market.tick-retention-days` (default 7) are deleted, except the latest tick of each instrument (BR18), which is always kept. | `PriceTickRetentionService` |
| BR27 | A cash transaction has `amount > 0` and `balance_after >= 0`; BUY and SELL reference an order, DEPOSIT and WITHDRAW do not. | DB `ck_transactions_amount`, `ck_transactions_balance`, `ck_transactions_order` |
| BR28 | A user sees only their own cash account: `balance`, `blockedBalance` and `available = balance − blockedBalance`. | `AccountService` |
| BR29 | A deposit or withdrawal amount is > 0, has at most 4 decimal places and is at most 1 000 000 per operation; otherwise 400 with a field error on `amount`. | `AmountRequest` (Bean Validation) |
| BR30 | A withdrawal may use only available funds (`balance − blockedBalance`); otherwise 409 `INSUFFICIENT_FUNDS` and nothing changes. Concurrent operations on one account are serialized by a row lock. | `AccountService.withdraw`, `AccountRepository.findByUserIdForUpdate` |
| BR31 | Every deposit and withdrawal writes a ledger transaction (`amount`, `balance_after`) in the same DB transaction as the balance change, so the signed sum of the ledger always equals the balance. | `AccountService` |
| BR32 | A user lists only their own transactions, newest first (`created_at` desc, then id desc), paginated (`page` >= 0, `size` 1–100, default 20) and optionally filtered by `type`; out-of-range paging returns 400. | `TransactionService.list` |
| BR33 | Buying more shares sets the position's average price to the quantity-weighted average of the old position and the new fill, rounded HALF_UP to 4 places; selling keeps the average price. A position never gives away more than its free shares (`quantity − blocked_quantity`). | `Position` |
| BR34 | LIMIT and STOP orders are not executed on placement: they are stored with status NEW and wait. | `OrderService.place` |
| BR35 | An order placed while the market is closed (BR23) returns 409 `MARKET_CLOSED`. | `OrderService.place` |
| BR36 | An order for an unknown instrument returns 404 `INSTRUMENT_NOT_FOUND`; for an inactive instrument 422 `INSTRUMENT_INACTIVE`. | `OrderService.place` |
| BR37 | A BUY requires available funds (`balance − blockedBalance`) of at least `quantity × latest price`; otherwise 409 `INSUFFICIENT_FUNDS`. | `OrderService` |
| BR38 | A SELL requires free shares (`quantity − blocked_quantity` of the position) of at least the order quantity; otherwise 409 `INSUFFICIENT_SHARES`. A position that reaches 0 shares is deleted (BR11). | `OrderService` |
| BR39 | A MARKET order is filled immediately at the latest price (BR18): status FILLED with `filled_price` and `filled_at`; the account, the position and a BUY/SELL transaction linked by `order_id` change in the same DB transaction. A rejected order is not stored. | `OrderService` |
| BR40 | A user lists only their own orders, newest first (`created_at` desc, then id desc), paginated (`page` >= 0, `size` 1–100, default 20) and optionally filtered by `status`. Fetching another user's order by id returns 404 `ORDER_NOT_FOUND`, the same as an unknown id. | `OrderService.list`, `OrderService.get` |
| BR41 | Positions are listed per user, ordered by symbol, valued at the latest price (BR18): `marketValue = quantity × currentPrice`, `unrealizedPnl = marketValue − quantity × avgPrice` (both 4 places), `unrealizedPnlPct = unrealizedPnl / (quantity × avgPrice) × 100` rounded HALF_UP to 2 places. | `PositionService` |
| BR42 | A pending BUY reserves funds: LIMIT `quantity × limitPrice`, STOP `quantity × stopPrice × orders.stop-buy-reserve-factor` (default 1.05, a buffer for slippage), rounded HALF_UP to 4 places. The reservation is stored in `orders.reserved_amount` and added to `accounts.blocked_balance`, so the blocked balance always equals the sum of reservations of NEW orders. If available funds are lower, 409 `INSUFFICIENT_FUNDS` and nothing is stored. | `OrderService.reserveFunds` |
| BR43 | A pending SELL blocks its quantity in `positions.blocked_quantity`; only free shares (`quantity − blocked_quantity`) can be blocked or sold, so the same shares are never reserved twice. If free shares are lower, 409 `INSUFFICIENT_SHARES` and nothing is stored. | `OrderService.blockShares`, `Position.block` |
| BR44 | A user may have at most `orders.max-open-orders` (default 20) open orders (status NEW, both sides). Placing another LIMIT or STOP order returns 409 `TOO_MANY_OPEN_ORDERS` and nothing changes; MARKET orders are not limited. | `OrderService.place` |
