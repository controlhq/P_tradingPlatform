-- =====================================================================
-- V4: seed instruments, each with one starting price tick
-- =====================================================================

INSERT INTO instruments (symbol, name)
VALUES ('AAPL',  'Apple Inc.'),
       ('MSFT',  'Microsoft Corporation'),
       ('NVDA',  'NVIDIA Corporation'),
       ('TSLA',  'Tesla, Inc.'),
       ('AMZN',  'Amazon.com, Inc.'),
       ('GOOGL', 'Alphabet Inc. Class A'),
       ('META',  'Meta Platforms, Inc.'),
       ('PKO',   'PKO Bank Polski S.A.');

INSERT INTO price_ticks (instrument_id, price)
SELECT i.id, p.price
FROM (VALUES ('AAPL',  189.5000),
             ('MSFT',  415.2000),
             ('NVDA',  875.3000),
             ('TSLA',  175.4000),
             ('AMZN',  182.7500),
             ('GOOGL', 165.1000),
             ('META',  495.6000),
             ('PKO',    58.9200)) AS p (symbol, price)
JOIN instruments i ON i.symbol = p.symbol;
