package com.fintech.ledger;

import com.fintech.ledger.dto.EntryRequest;
import com.fintech.ledger.dto.PostTransactionRequest;
import com.fintech.ledger.dto.TransactionResponse;
import com.fintech.ledger.entity.EntryType;
import com.fintech.ledger.entity.Transaction;
import com.fintech.ledger.exception.InvalidTransactionException;
import com.fintech.ledger.exception.ResourceNotFoundException;
import com.fintech.ledger.repository.AccountRepository;
import com.fintech.ledger.repository.LedgerEntryRepository;
import com.fintech.ledger.repository.OutboxMessageRepository;
import com.fintech.ledger.repository.TransactionRepository;
import com.fintech.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(accountRepository, transactionRepository,
                ledgerEntryRepository, outboxMessageRepository, JsonMapper.builder().build());
    }

    @Test
    void unbalancedTransactionIsRejected() {
        assertThatThrownBy(() -> ledgerService.postTransaction(request("k-1", "100", "90")))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("must equal");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void duplicateIdempotencyKeyReturnsExistingTransaction() {
        Transaction existing = Transaction.builder()
                .transactionId(1L)
                .idempotencyKey("k-2")
                .status("POSTED")
                .transactionDate(LocalDateTime.now())
                .build();
        when(transactionRepository.findByIdempotencyKey("k-2")).thenReturn(Optional.of(existing));
        when(ledgerEntryRepository.findByTransactionTransactionId(1L)).thenReturn(List.of());

        TransactionResponse response = ledgerService.postTransaction(request("k-2", "100", "100"));

        assertThat(response.transactionId()).isEqualTo(1L);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void unknownAccountIsRejected() {
        when(accountRepository.findAllForUpdate(any())).thenReturn(List.of());

        assertThatThrownBy(() -> ledgerService.postTransaction(request("k-3", "100", "100")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(transactionRepository, never()).save(any());
    }

    private PostTransactionRequest request(String key, String debit, String credit) {
        return new PostTransactionRequest(key, "REF-1", "test", List.of(
                new EntryRequest("CASH-T1", EntryType.DEBIT, new BigDecimal(debit)),
                new EntryRequest("SALES-T1", EntryType.CREDIT, new BigDecimal(credit))));
    }
}