package com.example.wallet.account;

import com.example.wallet.account.dto.DepositRequest;
import com.example.wallet.account.dto.WithdrawRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<Void> deposit(
            @PathVariable Long id,
            @Valid @RequestBody DepositRequest request) {

        accountService.deposit(id, request.amount());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<Void> withdraw(
            @PathVariable Long id,
            @Valid @RequestBody WithdrawRequest request) {

        accountService.withdraw(id, request.amount());

        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<Account>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }


}