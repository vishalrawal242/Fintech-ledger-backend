package com.fintech.ledger.controller;

import com.fintech.ledger.dto.PostTransactionRequest;
import com.fintech.ledger.dto.TransactionResponse;
import com.fintech.ledger.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> postTransaction(
            @Valid @RequestBody PostTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ledgerService.postTransaction(request));
    }

    @GetMapping("/transactions/{transactionId}")
    public TransactionResponse getTransaction(@PathVariable Long transactionId) {
        return ledgerService.getTransaction(transactionId);
    }
}