package com.fintech.ledger;

import com.fintech.ledger.dto.EntryRequest;
import com.fintech.ledger.dto.PostTransactionRequest;
import com.fintech.ledger.dto.TransactionResponse;
import com.fintech.ledger.entity.Account;
import com.fintech.ledger.entity.AccountType;
import com.fintech.ledger.entity.EntryType;
import com.fintech.ledger.entity.LedgerEntry;
import com.fintech.ledger.exception.InvalidTransactionException;
import com.fintech.ledger.repository.AccountRepository;
import com.fintech.ledger.repository.LedgerEntryRepository;
import com.fintech.ledger.repository.OutboxMessageRepository;
import com.fintech.ledger.repository.TransactionRepository;
import com.fintech.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "ledger.kafka.enabled=false")
@Import(TestcontainersConfiguration.class)
class LedgerIntegrationTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        outboxMessageRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        accountRepository.save(account("CASH-T1", AccountType.ASSET));
        accountRepository.save(account("SALES-T1", AccountType.REVENUE));
    }

    @Test
    void totalDebitsEqualTotalCreditsAfterPosting() {
        ledgerService.postTransaction(request("it-1", "100", "100"));
        ledgerService.postTransaction(request("it-2", "250.50", "250.50"));

        assertThat(totalOf(EntryType.DEBIT)).isEqualByComparingTo(totalOf(EntryType.CREDIT));
        assertThat(totalOf(EntryType.DEBIT)).isEqualByComparingTo("350.50");
    }

    @Test
    void sameIdempotencyKeyIsPostedOnlyOnce() {
        TransactionResponse first = ledgerService.postTransaction(request("it-3", "100", "100"));
        TransactionResponse second = ledgerService.postTransaction(request("it-3", "100", "100"));

        assertThat(second.transactionId()).isEqualTo(first.transactionId());
        assertThat(transactionRepository.count()).isEqualTo(1L);
        assertThat(ledgerEntryRepository.count()).isEqualTo(2L);
        assertThat(balanceOf("CASH-T1")).isEqualByComparingTo("100");
    }

    @Test
    void unbalancedTransactionIsRejectedAndNothingIsSaved() {
        assertThatThrownBy(() -> ledgerService.postTransaction(request("it-4", "100", "90")))
                .isInstanceOf(InvalidTransactionException.class);

        assertThat(transactionRepository.count()).isZero();
        assertThat(ledgerEntryRepository.count()).isZero();
    }

    @Test
    void outboxMessageIsSavedWithTheTransaction() {
        ledgerService.postTransaction(request("it-5", "100", "100"));

        assertThat(outboxMessageRepository.findByStatusOrderByCreatedAtAsc("PENDING")).hasSize(1);
    }

    private PostTransactionRequest request(String key, String debit, String credit) {
        return new PostTransactionRequest(key, "REF-1", "integration test", List.of(
                new EntryRequest("CASH-T1", EntryType.DEBIT, new BigDecimal(debit)),
                new EntryRequest("SALES-T1", EntryType.CREDIT, new BigDecimal(credit))));
    }

    private Account account(String number, AccountType type) {
        return Account.builder()
                .accountNumber(number)
                .type(type)
                .currency("INR")
                .status("ACTIVE")
                .balance(BigDecimal.ZERO)
                .build();
    }

    private BigDecimal totalOf(EntryType type) {
        return ledgerEntryRepository.findAll().stream()
                .filter(entry -> entry.getType() == type)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }
}