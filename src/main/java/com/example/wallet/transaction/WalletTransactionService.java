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
    public void recordFailedTransaction(
            Account sourceAccount,
            Account destinationAccount,
            BigDecimal amount,
            TransactionType type) {

        Transaction transaction = new Transaction(
                sourceAccount,
                destinationAccount,
                amount,
                type,
                TransactionStatus.FAILED,
                Instant.now()
        );

        transactionRepository.save(transaction);
    }
}