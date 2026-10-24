# Fintech Ledger Backend

A double-entry ledger backend built with Java and Spring Boot. It supports idempotency keys to prevent duplicate transactions, and the Transactional Outbox pattern to publish events to Kafka reliably.

## Tech Stack

- Java, Spring Boot, Spring Data JPA, Spring Security
- Apache Kafka
- Docker (docker-compose)
- JUnit 5, Mockito, Testcontainers
- Maven

## Features

- Double-entry bookkeeping: every transaction has balanced debit and credit entries
- Idempotency keys: repeated requests do not create duplicate transactions
- Transactional Outbox: events are saved with the transaction and then published to Kafka
- Global exception handling with clear error responses
- Unit and integration tests

## Author

Vishal Rawal