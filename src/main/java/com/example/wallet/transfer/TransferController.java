package com.example.wallet.transfer;

import com.example.wallet.transfer.dto.TransferRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<Void> transfer(
            @Valid @RequestBody TransferRequest request) {

        transferService.transfer(
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount()
        );

        return ResponseEntity.ok().build();
    }
}