package com.fintech.ledger.service;

import com.fintech.ledger.dto.EntryRequest;
import com.fintech.ledger.dto.PostTransactionRequest;
import com.fintech.ledger.dto.TransactionResponse;
import com.fintech.ledger.entity.Account;
import com.fintech.ledger.entity.AccountType;
import com.fintech.ledger.entity.EntryType;
import com.fintech.ledger.entity.LedgerEntry;
import com.fintech.ledger.entity.OutboxMessage;
import com.fintech.ledger.entity.Transaction;
import com.fintech.ledger.exception.InvalidTransactionException;
import com.fintech.ledger.exception.ResourceNotFoundException;
import com.fintech.ledger.repository.AccountRepository;
import com.fintech.ledger.repository.LedgerEntryRepository;
import com.fintech.ledger.repository.OutboxMessageRepository;
import com.fintech.ledger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private static final String STATUS_POSTED = "POSTED";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public TransactionResponse postTransaction(PostTransactionRequest request) {
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate request for idempotencyKey={}, returning existing transaction",
                    request.idempotencyKey());
            return toResponse(existing.get());
        }

        validateBalanced(request.entries());
        Map<String, Account> accounts = loadAccounts(request.entries());

        Transaction transaction = transactionRepository.save(Transaction.builder()
                .idempotencyKey(request.idempotencyKey())
                .transactionDate(LocalDateTime.now())
                .referenceId(request.referenceId())
                .status(STATUS_POSTED)
                .description(request.description())
                .build());

        List<LedgerEntry> entries = new ArrayList<>();
        for (EntryRequest entryRequest : request.entries()) {
            Account account = accounts.get(entryRequest.accountNumber());
            applyToBalance(account, entryRequest);
            entries.add(LedgerEntry.builder()
                    .transaction(transaction)
                    .account(account)
                    .type(entryRequest.type())
                    .amount(entryRequest.amount())
                    .build());
        }
        ledgerEntryRepository.saveAll(entries);
        outboxMessageRepository.save(buildOutboxMessage(transaction, entries));

        log.info("Posted transaction id={} idempotencyKey={}",
                transaction.getTransactionId(), transaction.getIdempotencyKey());
        return toResponse(transaction, entries);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        return toResponse(transaction);
    }

    private void validateBalanced(List<EntryRequest> entries) {
        BigDecimal debits = sum(entries, EntryType.DEBIT);
        BigDecimal credits = sum(entries, EntryType.CREDIT);
        if (debits.compareTo(credits) != 0) {
            throw new InvalidTransactionException(
                    "Debits (" + debits + ") must equal credits (" + credits + ")");
        }
    }

    private BigDecimal sum(List<EntryRequest> entries, EntryType type) {
        return entries.stream()
                .filter(entry -> entry.type() == type)
                .map(EntryRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Account> loadAccounts(List<EntryRequest> entries) {
        Set<String> numbers = entries.stream()
                .map(EntryRequest::accountNumber)
                .collect(Collectors.toSet());

        Map<String, Account> accounts = accountRepository.findAllForUpdate(numbers).stream()
                .collect(Collectors.toMap(Account::getAccountNumber, account -> account));

        for (String number : numbers) {
            Account account = accounts.get(number);
            if (account == null) {
                throw new ResourceNotFoundException("Account not found: " + number);
            }
            if (!STATUS_ACTIVE.equals(account.getStatus())) {
                throw new InvalidTransactionException("Account is not active: " + number);
            }
        }

        long currencies = accounts.values().stream().map(Account::getCurrency).distinct().count();
        if (currencies > 1) {
            throw new InvalidTransactionException("All accounts in a transaction must use the same currency");
        }
        return accounts;
    }

    private void applyToBalance(Account account, EntryRequest entry) {
        boolean debitNormal = account.getType() == AccountType.ASSET
                || account.getType() == AccountType.EXPENSE;
        boolean increases = (entry.type() == EntryType.DEBIT) == debitNormal;
        account.setBalance(increases
                ? account.getBalance().add(entry.amount())
                : account.getBalance().subtract(entry.amount()));
    }

    private OutboxMessage buildOutboxMessage(Transaction transaction, List<LedgerEntry> entries) {
        List<Map<String, Object>> entryPayload = entries.stream()
                .map(entry -> Map.<String, Object>of(
                        "accountNumber", entry.getAccount().getAccountNumber(),
                        "type", entry.getType().name(),
                        "amount", entry.getAmount()))
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", transaction.getTransactionId());
        payload.put("idempotencyKey", transaction.getIdempotencyKey());
        payload.put("referenceId", transaction.getReferenceId());
        payload.put("entries", entryPayload);

        return OutboxMessage.builder()
                .aggregateType("TRANSACTION")
                .aggregateId(String.valueOf(transaction.getTransactionId()))
                .eventType("TransactionPosted")
                .payload(jsonMapper.writeValueAsString(payload))
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return toResponse(transaction,
                ledgerEntryRepository.findByTransactionTransactionId(transaction.getTransactionId()));
    }

    private TransactionResponse toResponse(Transaction transaction, List<LedgerEntry> entries) {
        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getIdempotencyKey(),
                transaction.getReferenceId(),
                transaction.getStatus(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                entries.stream()
                        .map(entry -> new TransactionResponse.EntryResponse(
                                entry.getAccount().getAccountNumber(), entry.getType(), entry.getAmount()))
                        .toList());
    }
}