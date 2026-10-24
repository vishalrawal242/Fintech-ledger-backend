package com.fintech.ledger.dto;

import com.fintech.ledger.entity.EntryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record EntryRequest(
        @NotBlank String accountNumber,
        @NotNull EntryType type,
        @NotNull @Positive BigDecimal amount
) {
}