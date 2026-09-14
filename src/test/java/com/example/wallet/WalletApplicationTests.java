package com.example.wallet;

import com.example.wallet.account.Account;
import com.example.wallet.account.AccountRepository;
import com.example.wallet.account.AccountService;
import com.example.wallet.account.AccountStatus;
import com.example.wallet.exception.InsufficientBalanceException;
import com.example.wallet.user.User;
import com.example.wallet.user.UserRepository;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;


@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WalletApplicationTests {


    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;


    // =========================================================
    // TEST 1
    //
    // Two withdrawals are executed concurrently
    // on the SAME account.
    //
    // Initial balance = 100
    //
    // Withdrawal 1 = 70
    // Withdrawal 2 = 70
    //
    // Expected:
    // One withdrawal succeeds.
    // One withdrawal fails.
    // Final balance = 30
    // =========================================================

    @Test
    @Order(1)
    void concurrentWithdrawalsShouldKeepBalanceConsistent()
            throws Exception {

        // Arrange
        User user = userRepository.findByUsername("testuser")
                .orElseThrow();

        Account account = new Account(
                user,
                new BigDecimal("100.00"),
                AccountStatus.ACTIVE
        );

        account = accountRepository.save(account);

        Long accountId = account.getId();


        // Used to make both threads wait before starting.
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);


        // Act
        Future<String> withdrawal1 = executor.submit(() -> {

            setUserAuthentication();

            // Wait until both operations are ready.
            start.await();

            try {

                accountService.withdraw(
                        accountId,
                        new BigDecimal("70.00")
                );

                return "SUCCESS";

            } catch (InsufficientBalanceException e) {

                return "INSUFFICIENT_BALANCE";
            }
        });


        Future<String> withdrawal2 = executor.submit(() -> {

            setUserAuthentication();

            // Wait until both operations are ready.
            start.await();

            try {

                accountService.withdraw(
                        accountId,
                        new BigDecimal("70.00")
                );

                return "SUCCESS";

            } catch (InsufficientBalanceException e) {

                return "INSUFFICIENT_BALANCE";
            }
        });


        // Start both withdrawals concurrently.
        start.countDown();


        // Wait for both operations to finish.
        String result1 = withdrawal1.get();
        String result2 = withdrawal2.get();

        executor.shutdown();


        // Assert
        Account finalAccount =
                accountRepository.findById(accountId)
                        .orElseThrow();


        // Only one withdrawal can succeed:
        //
        // 100 - 70 = 30
        assertEquals(
                new BigDecimal("30.00"),
                finalAccount.getBalance()
        );


        int successfulWithdrawals = 0;

        if (result1.equals("SUCCESS")) {
            successfulWithdrawals++;
        }

        if (result2.equals("SUCCESS")) {
            successfulWithdrawals++;
        }


        // Exactly one withdrawal must succeed.
        assertEquals(
                1,
                successfulWithdrawals
        );
    }


    // =========================================================
    // TEST 2
    //
    // A deposit and a withdrawal are executed concurrently
    // on the SAME account.
    //
    // Initial balance = 100
    //
    // Deposit = 30
    // Withdrawal = 200
    //
    // Expected:
    // Deposit succeeds.
    // Withdrawal fails.
    // Final balance = 130
    // =========================================================

    @Test
    @Order(2)
    void concurrentDepositAndWithdrawShouldKeepBalanceConsistent()
            throws Exception {

        // Arrange
        User user = userRepository.findByUsername("testuser")
                .orElseThrow();

        Account account = new Account(
                user,
                new BigDecimal("100.00"),
                AccountStatus.ACTIVE
        );

        account = accountRepository.save(account);

        Long accountId = account.getId();


        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);


        // Act
        Future<String> deposit = executor.submit(() -> {

            setUserAuthentication();

            // Wait until both operations are ready.
            start.await();

            accountService.deposit(
                    accountId,
                    new BigDecimal("30.00")
            );

            return "DEPOSIT_SUCCESS";
        });


        Future<String> withdrawal = executor.submit(() -> {

            setUserAuthentication();

            // Wait until both operations are ready.
            start.await();

            try {

                accountService.withdraw(
                        accountId,
                        new BigDecimal("200.00")
                );

                return "WITHDRAW_SUCCESS";

            } catch (InsufficientBalanceException e) {

                return "INSUFFICIENT_BALANCE";
            }
        });


        // Start both operations concurrently.
        start.countDown();


        // Wait for both operations to finish.
        String depositResult = deposit.get();
        String withdrawalResult = withdrawal.get();

        executor.shutdown();


        // Assert
        Account finalAccount =
                accountRepository.findById(accountId)
                        .orElseThrow();


        // 100 + 30 = 130
        assertEquals(
                new BigDecimal("130.00"),
                finalAccount.getBalance()
        );


        // Deposit must succeed.
        assertEquals(
                "DEPOSIT_SUCCESS",
                depositResult
        );


        // Withdrawal of 200 must fail.
        assertEquals(
                "INSUFFICIENT_BALANCE",
                withdrawalResult
        );
    }


    // =========================================================
    // TEST 3
    //
    // Several operations are executed concurrently
    // on the SAME account.
    //
    // Initial balance = 100
    //
    // Deposit 30
    // Deposit 50
    // Withdraw 40
    // Withdraw 20
    //
    // Expected:
    //
    // 100 + 30 + 50 - 40 - 20 = 120
    //
    // All operations should succeed.
    // Final balance = 120
    // =========================================================

    @Test
    @Order(3)
    void multipleConcurrentOperationsShouldKeepBalanceConsistent()
            throws Exception {

        // Arrange
        User user = userRepository.findByUsername("testuser")
                .orElseThrow();

        Account account = new Account(
                user,
                new BigDecimal("100.00"),
                AccountStatus.ACTIVE
        );

        account = accountRepository.save(account);

        Long accountId = account.getId();


        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(4);


        // Act

        Future<String> deposit1 = executor.submit(() -> {

            setUserAuthentication();

            start.await();

            accountService.deposit(
                    accountId,
                    new BigDecimal("30.00")
            );

            return "SUCCESS";
        });


        Future<String> deposit2 = executor.submit(() -> {

            setUserAuthentication();

            start.await();

            accountService.deposit(
                    accountId,
                    new BigDecimal("50.00")
            );

            return "SUCCESS";
        });


        Future<String> withdrawal1 = executor.submit(() -> {

            setUserAuthentication();

            start.await();

            accountService.withdraw(
                    accountId,
                    new BigDecimal("40.00")
            );

            return "SUCCESS";
        });


        Future<String> withdrawal2 = executor.submit(() -> {

            setUserAuthentication();

            start.await();

            accountService.withdraw(
                    accountId,
                    new BigDecimal("20.00")
            );

            return "SUCCESS";
        });


        // Start all four operations concurrently.
        start.countDown();


        // Wait for all operations to finish.
        String result1 = deposit1.get();
        String result2 = deposit2.get();
        String result3 = withdrawal1.get();
        String result4 = withdrawal2.get();

        executor.shutdown();


        // Assert
        Account finalAccount =
                accountRepository.findById(accountId)
                        .orElseThrow();


        // Expected:
        //
        // 100 + 30 + 50 - 40 - 20 = 120
        assertEquals(
                new BigDecimal("120.00"),
                finalAccount.getBalance()
        );


        // All four operations must succeed.
        assertEquals("SUCCESS", result1);
        assertEquals("SUCCESS", result2);
        assertEquals("SUCCESS", result3);
        assertEquals("SUCCESS", result4);
    }


    // =========================================================
    // Authentication helper
    //
    // SecurityContextHolder is thread-local, so each worker
    // thread needs its own authenticated user.
    // =========================================================

    private void setUserAuthentication() {

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "testuser",
                        null,
                        null
                )
        );
    }
}