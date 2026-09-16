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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the wallet project.
 *
 * 1.  Valid login
 * 2.  Invalid password
 * 3.  Deposit
 * 4.  Negative deposit
 * 5.  Withdrawal
 * 6.  Insufficient balance
 * 7.  User cannot operate another user's account
 * 8.  Admin can view all accounts
 * 9.  Normal user cannot view all accounts
 * 10. Transfer between different users
 * 11. Transfer from and  to  the same account
 * 12. Concurrent withdrawals
 * 13. Concurrent deposit + withdrawal
 * 14. Multiple concurrent operations
 * 15. Opposite transfers / deadlock prevention
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WalletApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    // TEST 1 - Valid login

    @Test
    @Order(1)
    void loginWithValidCredentialsShouldReturnToken()
            throws Exception {

        String response = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "username": "Amine",
                                            "password": "Amine01"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("token"));
    }

    // TEST 2 - Invalid login

    @Test
    @Order(2)
    void loginWithInvalidPasswordShouldReturnUnauthorized()
            throws Exception {

        mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "username": "Amine",
                                            "password": "Amine001"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());
    }


    // TEST 3 - Deposit

    @Test
    @Order(3)
    void depositShouldReturnOk()
            throws Exception {

        Account account = createAccount("Amine", "100.00");

        String token = login("Amine", "Amine01");

        mockMvc.perform(
                        post("/accounts/" + account.getId() + "/deposit")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "amount": 50.00
                                        }
                                        """)
                )
                .andExpect(status().isOk());

        Account updatedAccount = accountRepository
                .findById(account.getId())
                .orElseThrow();

        assertEquals(
                new BigDecimal("150.00"),
                updatedAccount.getBalance()
        );
    }

    // TEST 4 - Invalid deposit

    @Test
    @Order(4)
    void depositWithNegativeAmountShouldReturnBadRequest()
            throws Exception {

        Account account = createAccount("Amine", "100.00");

        String token = login("Amine", "Amine01");

        mockMvc.perform(
                        post("/accounts/" + account.getId() + "/deposit")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "amount": -50.00
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        Account updatedAccount = accountRepository
                .findById(account.getId())
                .orElseThrow();

        // Balance must not change.
        assertEquals(
                new BigDecimal("100.00"),
                updatedAccount.getBalance()
        );
    }

    // TEST 5 - Successful withdrawal

    @Test
    @Order(5)
    void withdrawShouldReturnOk()
            throws Exception {

        Account account = createAccount("Amine", "100.00");

        String token = login("Amine", "Amine01");

        mockMvc.perform(
                        post("/accounts/" + account.getId() + "/withdraw")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "amount": 40.00
                                        }
                                        """)
                )
                .andExpect(status().isOk());

        Account updatedAccount = accountRepository
                .findById(account.getId())
                .orElseThrow();

        assertEquals(
                new BigDecimal("60.00"),
                updatedAccount.getBalance()
        );
    }


    // TEST 6 - Insufficient balance

    @Test
    @Order(6)
    void withdrawWithInsufficientBalanceShouldReturnBadRequest()
            throws Exception {

        Account account = createAccount("Amine", "100.00");

        String token = login("Amine", "Amine01");

        mockMvc.perform(
                        post("/accounts/" + account.getId() + "/withdraw")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "amount": 200.00
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        Account updatedAccount = accountRepository
                .findById(account.getId())
                .orElseThrow();

        // Balance must remain unchanged.
        assertEquals(
                new BigDecimal("100.00"),
                updatedAccount.getBalance()
        );
    }



    // TEST 7 - User cannot operate another user's account

    @Test
    @Order(7)
    void userCannotWithdrawFromAnotherUsersAccount()
            throws Exception {

        Account amineAccount = createAccount("Amine", "100.00");
        Account ahmedAccount = createAccount("Ahmed", "100.00");

        String amineToken = login("Amine", "Amine01");

        mockMvc.perform(
                        post("/accounts/" + ahmedAccount.getId() + "/withdraw")
                                .header("Authorization", "Bearer " + amineToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "amount": 20.00
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());

        Account updatedAhmedAccount = accountRepository
                .findById(ahmedAccount.getId())
                .orElseThrow();

        assertEquals(
                new BigDecimal("100.00"),
                updatedAhmedAccount.getBalance()
        );
    }



    // TEST 8 - Admin can see all accounts

    @Test
    @Order(8)
    void adminCanGetAllAccounts()
            throws Exception {

        String adminToken = login("Admin", "Admin01");

        mockMvc.perform(
                        get("/accounts")
                                .header(
                                        "Authorization",
                                        "Bearer " + adminToken
                                )
                )
                .andExpect(status().isOk());
    }



    // TEST 9 - Normal user cannot see all accounts


    @Test
    @Order(9)
    void normalUserCannotGetAllAccounts()
            throws Exception {

        String userToken = login("Amine", "Amine01");

        mockMvc.perform(
                        get("/accounts")
                                .header(
                                        "Authorization",
                                        "Bearer " + userToken
                                )
                )
                .andExpect(status().isForbidden());
    }



    // TEST 10 - Successful transfer

    @Test
    @Order(10)
    void transferShouldReturnOk()
            throws Exception {

        Account sourceAccount =
                createAccount("Amine", "100.00");

        Account destinationAccount =
                createAccount("Ahmed", "100.00");

        String token = login("Amine", "Amine01");

        mockMvc.perform(
                        post("/transfers")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "sourceAccountId": %d,
                                            "destinationAccountId": %d,
                                            "amount": 30.00
                                        }
                                        """.formatted(
                                        sourceAccount.getId(),
                                        destinationAccount.getId()
                                ))
                )
                .andExpect(status().isOk());

        Account updatedSource =
                accountRepository
                        .findById(sourceAccount.getId())
                        .orElseThrow();

        Account updatedDestination =
                accountRepository
                        .findById(destinationAccount.getId())
                        .orElseThrow();

        assertEquals(
                new BigDecimal("70.00"),
                updatedSource.getBalance()
        );

        assertEquals(
                new BigDecimal("130.00"),
                updatedDestination.getBalance()
        );
    }



    // TEST 11 - Transfer  from and  to  the same account

    @Test
    @Order(11)
    void transferToSameAccountShouldReturnBadRequest()
            throws Exception {

        Account account = createAccount("Amine", "100.00");

        String token = login("Amine", "Amine01");

        mockMvc.perform(
                        post("/transfers")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "sourceAccountId": %d,
                                            "destinationAccountId": %d,
                                            "amount": 30.00
                                        }
                                        """.formatted(
                                        account.getId(),
                                        account.getId()
                                ))
                )
                .andExpect(status().isBadRequest());

        Account updatedAccount =
                accountRepository
                        .findById(account.getId())
                        .orElseThrow();

        assertEquals(
                new BigDecimal("100.00"),
                updatedAccount.getBalance()
        );
    }



    // CONCURRENCY TESTS
    // TEST 12
    // Two withdrawals are executed concurrently
    // on the SAME account.

    @Test
    @Order(12)
    void concurrentWithdrawalsShouldKeepBalanceConsistent()
            throws Exception {

        Account account = createAccount("Amine", "100.00");

        Long accountId = account.getId();

        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {

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


            // Start both operations at approximately the same time.
            start.countDown();


            String result1 =
                    withdrawal1.get(5, TimeUnit.SECONDS);

            String result2 =
                    withdrawal2.get(5, TimeUnit.SECONDS);


            Account finalAccount =
                    accountRepository
                            .findById(accountId)
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

        } finally {

            executor.shutdownNow();
        }
    }


    // TEST 13

    // Deposit and withdrawal happen concurrently
    // on the SAME account.

    @Test
    @Order(13)
    void concurrentDepositAndWithdrawShouldKeepBalanceConsistent()
            throws Exception {

        Account account = createAccount("Amine", "100.00");

        Long accountId = account.getId();

        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {

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


            String depositResult =
                    deposit.get(5, TimeUnit.SECONDS);

            String withdrawalResult =
                    withdrawal.get(5, TimeUnit.SECONDS);


            Account finalAccount =
                    accountRepository
                            .findById(accountId)
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

        } finally {

            executor.shutdownNow();
        }
    }


    // TEST 14

    // Several operations happen concurrently
    // on the SAME account.

    @Test
    @Order(14)
    void multipleConcurrentOperationsShouldKeepBalanceConsistent()
            throws Exception {

        Account account = createAccount("Amine", "100.00");

        Long accountId = account.getId();

        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(4);

        try {

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


            String result1 =
                    deposit1.get(5, TimeUnit.SECONDS);

            String result2 =
                    deposit2.get(5, TimeUnit.SECONDS);

            String result3 =
                    withdrawal1.get(5, TimeUnit.SECONDS);

            String result4 =
                    withdrawal2.get(5, TimeUnit.SECONDS);


            Account finalAccount =
                    accountRepository
                            .findById(accountId)
                            .orElseThrow();


            assertEquals(
                    new BigDecimal("120.00"),
                    finalAccount.getBalance()
            );


            assertEquals("SUCCESS", result1);
            assertEquals("SUCCESS", result2);
            assertEquals("SUCCESS", result3);
            assertEquals("SUCCESS", result4);

        } finally {

            executor.shutdownNow();
        }
    }


    // TEST 15
    // Two transfers happen concurrently in opposite directions:
    // A -> B = 30
    // B -> A = 20

    @Test
    @Order(15)
    void oppositeTransfersShouldCompleteWithoutDeadlock()
            throws Exception {

        Account accountA =
                createAccount("Amine", "100.00");

        Account accountB =
                createAccount("Ahmed", "100.00");

        Long accountAId = accountA.getId();
        Long accountBId = accountB.getId();

        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {

            Future<String> transferAtoB = executor.submit(() -> {

                try {
                    // This thread acts as Amine
                    setAuthentication("Amine");

                    start.await();

                    transferService.transfer(
                            accountAId,
                            accountBId,
                            new BigDecimal("30.00")
                    );

                    return "SUCCESS";

                } finally {
                    SecurityContextHolder.clearContext();
                }
            });


            Future<String> transferBtoA = executor.submit(() -> {

                try {
                    // This thread acts as Ahmed
                    setAuthentication("Ahmed");

                    start.await();

                    transferService.transfer(
                            accountBId,
                            accountAId,
                            new BigDecimal("20.00")
                    );

                    return "SUCCESS";

                } finally {
                    SecurityContextHolder.clearContext();
                }
            });


            // Start both transfers at approximately the same time.
            start.countDown();


            /*
             * The timeout is intentional.
             *
             * If the two transfers deadlock,
             * get() will not wait forever.
             */
            String result1 =
                    transferAtoB.get(5, TimeUnit.SECONDS);

            String result2 =
                    transferBtoA.get(5, TimeUnit.SECONDS);


            Account finalAccountA =
                    accountRepository
                            .findById(accountAId)
                            .orElseThrow();

            Account finalAccountB =
                    accountRepository
                            .findById(accountBId)
                            .orElseThrow();


            // A: 100 - 30 + 20 = 90
            assertEquals(
                    new BigDecimal("90.00"),
                    finalAccountA.getBalance()
            );

            // B: 100 + 30 - 20 = 110
            assertEquals(
                    new BigDecimal("110.00"),
                    finalAccountB.getBalance()
            );


            assertEquals(
                    "SUCCESS",
                    result1
            );

            assertEquals(
                    "SUCCESS",
                    result2
            );

        } finally {
            executor.shutdownNow();
        }
    }


    // =========================================================
    // HELPERS
    // =========================================================


    /**
     * Creates a new account for an existing test user.
     *
     * This keeps API tests independent from each other's balances.
     */
    private Account createAccount(
            String username,
            String balance
    ) {

        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow();

        return accountRepository.save(
                new Account(
                        user,
                        new BigDecimal(balance),
                        AccountStatus.ACTIVE
                )
        );
    }


    /**
     * Login through the real HTTP API and return the JWT token.
     */
    private String login(
            String username,
            String password
    ) throws Exception {

        String response =
                mockMvc.perform(
                                post("/auth/login")
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content("""
                                                {
                                                    "username": "%s",
                                                    "password": "%s"
                                                }
                                                """.formatted(
                                                username,
                                                password
                                        ))
                        )
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();


        /*
         * The current LoginResponse is expected to return:
         *
         * {
         *     "token": "..."
         * }
         *
         * We extract the JWT without adding another dependency.
         */
        int tokenStart =
                response.indexOf("\"token\"");


        assertTrue(
                tokenStart >= 0,
                "Login response does not contain a token: " + response
        );


        int colon =
                response.indexOf(":", tokenStart);


        int firstQuote =
                response.indexOf("\"", colon + 1);


        int secondQuote =
                response.indexOf("\"", firstQuote + 1);


        String token =
                response.substring(
                        firstQuote + 1,
                        secondQuote
                );


        assertNotNull(token);
        assertTrue(!token.isBlank());


        return token;
    }


    /**
     * Sets the authenticated user for the service-level
     * concurrency tests.
     *
     * Each worker thread has its own SecurityContext.
     */
    private void setAuthentication(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        null
                )
        );
    }

    private void setUserAuthentication() {
        setAuthentication("Amine");
    }
}