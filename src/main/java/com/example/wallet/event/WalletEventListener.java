package com.example.wallet.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WalletEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(WalletOperationEvent event) {

        // Audit trace
        System.out.println(
                "AUDIT: transaction=" + event.transactionId()
                        + ", type=" + event.type()
                        + ", amount=" + event.amount()
        );

        // Notification simulation
        System.out.println(
                "Notification envoyée pour la transaction "
                        + event.transactionId()
        );
    }
}