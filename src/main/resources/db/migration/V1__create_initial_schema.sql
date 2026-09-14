CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       username VARCHAR(100) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       role VARCHAR(20) NOT NULL
);

CREATE TABLE accounts (
                          id BIGSERIAL PRIMARY KEY,
                          user_id BIGINT NOT NULL,
                          balance NUMERIC(19, 2) NOT NULL,
                          status VARCHAR(20) NOT NULL,

                          CONSTRAINT fk_accounts_user
                              FOREIGN KEY (user_id)
                                  REFERENCES users(id),

                          CONSTRAINT chk_account_balance
                              CHECK (balance >= 0)
);

CREATE TABLE transactions (
                              id BIGSERIAL PRIMARY KEY,
                              source_account_id BIGINT,
                              destination_account_id BIGINT,
                              amount NUMERIC(19, 2) NOT NULL,
                              type VARCHAR(20) NOT NULL,
                              status VARCHAR(20) NOT NULL,
                              created_at TIMESTAMP WITH TIME ZONE NOT NULL,

                              CONSTRAINT fk_transactions_source
                                  FOREIGN KEY (source_account_id)
                                      REFERENCES accounts(id),

                              CONSTRAINT fk_transactions_destination
                                  FOREIGN KEY (destination_account_id)
                                      REFERENCES accounts(id),

                              CONSTRAINT chk_transaction_amount
                                  CHECK (amount > 0)
);