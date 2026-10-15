INSERT INTO stocks (
    symbol,
    company_name,
    current_price,
    day_change,
    day_change_percent,
    market_source,
    sector
)
VALUES
    ('AAPL', 'Apple Inc.', 214.86, 2.34, 1.10, 'demo', 'Technology'),
    ('MSFT', 'Microsoft Corporation', 448.12, 4.11, 0.92, 'demo', 'Technology'),
    ('NVDA', 'NVIDIA Corporation', 128.42, -1.53, -1.18, 'demo', 'Semiconductors'),
    ('AMZN', 'Amazon.com, Inc.', 191.24, 1.89, 1.00, 'demo', 'Consumer Discretionary')
ON CONFLICT (symbol) DO NOTHING;
