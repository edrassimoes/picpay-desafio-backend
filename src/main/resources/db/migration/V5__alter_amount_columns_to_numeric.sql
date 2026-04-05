ALTER TABLE tb_wallets
    ALTER COLUMN balance TYPE NUMERIC(19, 4) USING balance::NUMERIC;

ALTER TABLE tb_transactions
    ALTER COLUMN amount TYPE NUMERIC(19, 4) USING amount::NUMERIC;
