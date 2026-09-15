package com.example.wallet.transaction;

import com.example.wallet.account.Account;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class WalletTransactionService {

    private final TransactionRepository transactionRepository;

    public WalletTransactionService(
            TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedWithdrawal(
            Account account,
            BigDecimal amount) {

        Transaction transaction = new Transaction(
                account,
                null,
                amount,
                TransactionType.WITHDRAW,
                TransactionStatus.FAILED,
                Instant.now()
        );

        transactionRepository.save(transaction);
    }
}