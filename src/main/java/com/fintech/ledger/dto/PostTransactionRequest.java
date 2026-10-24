package com.fintech.ledger.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PostTransactionRequest(
        @NotBlank @Size(max = 100) String idempotencyKey,
        @Size(max = 100) String referenceId,
        @Size(max = 255) String description,
        @NotNull @Size(min = 2) @Valid List<EntryRequest> entries
) {
}