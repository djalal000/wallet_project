package com.example.wallet;

import com.example.wallet.account.Account;
import com.example.wallet.account.AccountRepository;
import com.example.wallet.account.AccountService;
import com.example.wallet.account.AccountStatus;
import com.example.wallet.exception.InsufficientBalanceException;
import com.example.wallet.transfer.TransferService;
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
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;


    // =========================================================
    // TEST 1
    //
    // Two withdrawals are executed concurrently
    // on the SAME account.
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

        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);


        // Act
        Future<String> withdrawal1 = executor.submit(() -> {

            setUserAuthentication();

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


        start.countDown();


        String result1 = withdrawal1.get();
        String result2 = withdrawal2.get();

        executor.shutdown();


        // Assert
        Account finalAccount =
                accountRepository.findById(accountId)
                        .orElseThrow();


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

            start.await();

            accountService.deposit(
                    accountId,
                    new BigDecimal("30.00")
            );

            return "DEPOSIT_SUCCESS";
        });


        Future<String> withdrawal = executor.submit(() -> {

            setUserAuthentication();

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


        start.countDown();


        String depositResult = deposit.get();
        String withdrawalResult = withdrawal.get();

        executor.shutdown();


        // Assert
        Account finalAccount =
                accountRepository.findById(accountId)
                        .orElseThrow();


        assertEquals(
                new BigDecimal("130.00"),
                finalAccount.getBalance()
        );


        assertEquals(
                "DEPOSIT_SUCCESS",
                depositResult
        );


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


        start.countDown();


        String result1 = deposit1.get();
        String result2 = deposit2.get();
        String result3 = withdrawal1.get();
        String result4 = withdrawal2.get();

        executor.shutdown();


        // Assert
        Account finalAccount =
                accountRepository.findById(accountId)
                        .orElseThrow();


        assertEquals(
                new BigDecimal("120.00"),
                finalAccount.getBalance()
        );


        assertEquals("SUCCESS", result1);
        assertEquals("SUCCESS", result2);
        assertEquals("SUCCESS", result3);
        assertEquals("SUCCESS", result4);
    }


    // =========================================================
    // TEST 4
    //
    // Two transfers happen concurrently in opposite directions:
    //
    // Account A -> Account B
    // Account B -> Account A
    //
    // This verifies that transfers do not deadlock
    // and that both balances remain correct.
    // =========================================================

    @Test
    @Order(4)
    void oppositeTransfersShouldCompleteWithoutDeadlock()
            throws Exception {

        // Arrange
        User user = userRepository.findByUsername("testuser")
                .orElseThrow();


        Account accountA = accountRepository.save(
                new Account(
                        user,
                        new BigDecimal("100.00"),
                        AccountStatus.ACTIVE
                )
        );


        Account accountB = accountRepository.save(
                new Account(
                        user,
                        new BigDecimal("100.00"),
                        AccountStatus.ACTIVE
                )
        );


        Long accountAId = accountA.getId();
        Long accountBId = accountB.getId();


        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);


        // Act

        Future<String> transferAtoB = executor.submit(() -> {

            setUserAuthentication();

            start.await();

            transferService.transfer(
                    accountAId,
                    accountBId,
                    new BigDecimal("30.00")
            );

            return "SUCCESS";
        });


        Future<String> transferBtoA = executor.submit(() -> {

            setUserAuthentication();

            start.await();

            transferService.transfer(
                    accountBId,
                    accountAId,
                    new BigDecimal("20.00")
            );

            return "SUCCESS";
        });


        // Start both transfers at the same time.
        start.countDown();


        /*
         * get() waits for both operations.
         *
         * If the locking strategy caused a deadlock,
         * these operations could remain blocked.
         */
        String result1 = transferAtoB.get();
        String result2 = transferBtoA.get();


        executor.shutdown();


        // Assert

        Account finalAccountA =
                accountRepository.findById(accountAId)
                        .orElseThrow();


        Account finalAccountB =
                accountRepository.findById(accountBId)
                        .orElseThrow();


        /*
         * Account A:
         *
         * 100 - 30 + 20 = 90
         *
         * Account B:
         *
         * 100 + 30 - 20 = 110
         */

        assertEquals(
                new BigDecimal("90.00"),
                finalAccountA.getBalance()
        );


        assertEquals(
                new BigDecimal("110.00"),
                finalAccountB.getBalance()
        );


        // Both transfers must succeed.
        assertEquals("SUCCESS", result1);
        assertEquals("SUCCESS", result2);
    }


    // =========================================================
    // Authentication helper
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