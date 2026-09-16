# Mini Wallet Project 

A simple backend electronic wallet application developed with Spring Boot.

The application supports authentication, deposits, withdrawals, transfers,
transaction tracking, and concurrent operations.

## Technologies

- Java 21
- Spring Boot
- Spring Security + JWT
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway
- Maven
- Docker / Docker Compose
- JUnit 5

## How to Run

### Requirements

- Java 21
- Docker
- Docker Compose

### Run the application

From the project root:

```bash
docker compose up --build
```

This starts the Spring Boot application and PostgreSQL database.

The application will be available at:

```text
### 1. Login

First, authenticate with:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "Amine",
    "password": "Amine01"
  }'
```

The response contains a JWT token:

```json
{
  "token": "YOUR_JWT_TOKEN"
}
```

### 2. Use the token

Copy the returned token and use it in the `Authorization` header.

For example, to make a deposit:

```bash

curl -X POST http://localhost:8080/accounts/1/deposit \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "amount": 100.00
  }'

```
## The Other  APIs

### Withdraw

```bash
curl -X POST http://localhost:8080/accounts/1/withdraw \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "amount": 50.00
  }'
```

This withdraws `50.00` from account `1`.

If the account does not have enough balance, the operation is rejected.

---

###  Transfer

For example, transfer `100.00` from Amine's account to Ahmed's account:

```bash
curl -X POST http://localhost:8080/transfers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "sourceAccountId": 1,
    "destinationAccountId": 2,
    "amount": 100.00
  }'
```
---

###  View All Accounts

This endpoint is available to administrators.

First login as:

```text
Username: Admin
Password: Admin01
```

Then use the returned JWT:

```bash
curl -X GET http://localhost:8080/accounts \
  -H "Authorization: Bearer ADMIN_JWT_TOKEN"
```

A normal USER cannot access this endpoint.

---


To stop the application:

```bash
docker compose down
```

### Run tests

```bash
./mvnw test
```

The test suite covers authentication, wallet operations, authorization,
validation, and concurrent operations:

/**
* Integration tests for the wallet project.
*
*   Valid login
*  Invalid password
*  Deposit
*  Negative deposit
*   Withdrawal
*   Insufficient balance
*  User cannot operate another user's account
*   Admin can view all accounts
*   Normal user cannot view all accounts
*  Transfer between different users
*  Transfer from and  to  the same account
*  Concurrent withdrawals
*  Concurrent deposit + withdrawal
*  Multiple concurrent operations
*  Opposite transfers / deadlock prevention
**/


## Architecture

The project uses a simple layered architecture:

Client -->
Controller-->
Service-->
Repository-->
PostgreSQL


###########################################


### Controller

Handles HTTP requests and input validation.

### Service

Contains the business logic such as:

- Balance validation
- Account ownership
- Deposits
- Withdrawals
- Transfers
- Transaction creation

### Repository

Handles database operations using Spring Data JPA.

DTOs are used for API requests instead of exposing entities directly.

## Authentication

The application uses JWT authentication with two roles:

- `USER`
- `ADMIN`

A `USER` can only operate on their own account.

An `ADMIN` can view all accounts.

## Concurrency

The wallet must keep balances consistent when several operations happen
at the same time.

For operations on the same account, the application uses a PostgreSQL
pessimistic write lock.

This prevents simultaneous operations from incorrectly modifying the
same balance.

For transfers, both accounts are locked in a consistent order based on
their IDs.

For example:

```text
A -> B
B -> A
```

Both transactions acquire the locks in the same order. This prevents
circular waiting and avoids deadlocks in opposite transfers.

The test suite includes concurrent tests for:

- Simultaneous withdrawals
- Deposit and withdrawal at the same time
- Multiple concurrent operations
- Opposite transfers

## Deferred Processing

After a successful wallet operation, an event is published for audit and
notification simulation.

The event is processed:

- After the main database transaction is committed
- Asynchronously so it does not block the HTTP request

This keeps the main wallet operation separate from the deferred processing.

## Database

PostgreSQL is used as the database.

Flyway is used to manage database migrations.

The main tables are:

```text
users
accounts
transactions
```

Database constraints are used to ensure that:

- Account balances cannot be negative
- Transaction amounts must be greater than zero
- Relationships between users, accounts, and transactions remain valid

## Design Choices 

### Pessimistic locking

Pessimistic locking was chosen because wallet balances must remain
consistent when multiple operations modify the same account at the same
time.



### JWT

JWT was chosen because it provides stateless authentication and fits well
with a REST API.

### Spring Events

Spring application events with asynchronous processing were used to keep
the implementation simple.

The trade-off is that in-memory events are not durable if the application
crashes before the event is processed.

## Verify the Database

PostgreSQL is running inside the `wallet-postgres` Docker container.

To access the database:

```bash
docker exec -it wallet-postgres psql -U wallet -d wallet
```

To check the tables:

```sql
\dt
```

You should see:

```text
accounts
transactions
users
flyway_schema_history
```

### Check accounts

```sql
SELECT
    a.id,
    u.username,
    a.balance,
    a.status
FROM accounts a
JOIN users u ON a.user_id = u.id
ORDER BY a.id;
```

### Check transactions

```sql
SELECT
    id,
    source_account_id,
    destination_account_id,
    amount,
    type,
    status,
    created_at
FROM transactions
ORDER BY id DESC;
```

This allows you to verify that deposits, withdrawals, transfers, and
failed operations are correctly recorded in the database.

To exit PostgreSQL:

```sql
\q
```
