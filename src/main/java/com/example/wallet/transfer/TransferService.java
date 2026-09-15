package com.example.wallet.transfer;

import com.example.wallet.account.Account;
import com.example.wallet.account.AccountRepository;
import com.example.wallet.account.AccountStatus;
import com.example.wallet.exception.AccountBlockedException;
import com.example.wallet.exception.AccountNotFoundException;
import com.example.wallet.exception.ForbiddenOperationException;
import com.example.wallet.exception.InsufficientBalanceException;
import com.example.wallet.transaction.Transaction;
import com.example.wallet.transaction.TransactionRepository;
import com.example.wallet.transaction.TransactionStatus;
import com.example.wallet.transaction.TransactionType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransferService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository) {

        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public void transfer(
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount) {

        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException(
                    "Source and destination accounts must be different"
            );
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero"
            );
        }


        Long firstAccountId =
                Math.min(sourceAccountId, destinationAccountId);

        Long secondAccountId =
                Math.max(sourceAccountId, destinationAccountId);

        Account firstAccount = accountRepository
                .findByIdForUpdate(firstAccountId)
                .orElseThrow(() ->
                        new AccountNotFoundException(firstAccountId)
                );

        Account secondAccount = accountRepository
                .findByIdForUpdate(secondAccountId)
                .orElseThrow(() ->
                        new AccountNotFoundException(secondAccountId)
                );

        Account sourceAccount =
                sourceAccountId.equals(firstAccountId)
                        ? firstAccount
                        : secondAccount;

        Account destinationAccount =
                destinationAccountId.equals(firstAccountId)
                        ? firstAccount
                        : secondAccount;


        verifyAccountOwnership(sourceAccount);

        if (sourceAccount.getStatus() == AccountStatus.BLOCKED) {
            throw new AccountBlockedException(sourceAccountId);
        }

        if (destinationAccount.getStatus() == AccountStatus.BLOCKED) {
            throw new AccountBlockedException(destinationAccountId);
        }

        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException();
        }


        // Remove money from source account.
        sourceAccount.setBalance(
                sourceAccount.getBalance().subtract(amount)
        );

        // Add money to destination account.
        destinationAccount.setBalance(
                destinationAccount.getBalance().add(amount)
        );


        // Record the successful transfer.
        Transaction transaction = new Transaction(
                sourceAccount,
                destinationAccount,
                amount,
                TransactionType.TRANSFER,
                TransactionStatus.SUCCESS,
                Instant.now()
        );

        transactionRepository.save(transaction);
    }


    private void verifyAccountOwnership(Account account) {

        String username = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        if (!account.getUser().getUsername().equals(username)) {
            throw new ForbiddenOperationException(
                    "You can only operate on your own account"
            );
        }
    }
}