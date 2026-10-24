package com.fintech.ledger.dto;

import com.fintech.ledger.entity.EntryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TransactionResponse(
        Long transactionId,
        String idempotencyKey,
        String referenceId,
        String status,
        String description,
        LocalDateTime transactionDate,
        List<EntryResponse> entries
) {
    public record EntryResponse(String accountNumber, EntryType type, BigDecimal amount) {
    }
}