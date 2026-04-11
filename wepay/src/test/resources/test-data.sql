INSERT INTO accounts (id, owner_name, balance, created_at, updated_at)
VALUES (1, 'Alice', 1000000, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET owner_name = EXCLUDED.owner_name, balance = EXCLUDED.balance;
