package com.example.wallet.config;

import com.example.wallet.account.Account;
import com.example.wallet.account.AccountRepository;
import com.example.wallet.account.AccountStatus;
import com.example.wallet.user.User;
import com.example.wallet.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initData(
            UserRepository userRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {

            if (userRepository.count() > 0) {
                return;
            }

            User amine = new User(
                    "Amine",
                    passwordEncoder.encode("Amine01"),
                    "USER"
            );

            User ahmed = new User(
                    "Ahmed",
                    passwordEncoder.encode("Ahmed01"),
                    "USER"
            );

            User ali = new User(
                    "Ali",
                    passwordEncoder.encode("Ali01"),
                    "USER"
            );

            User admin = new User(
                    "Admin",
                    passwordEncoder.encode("Admin01"),
                    "ADMIN"
            );

            userRepository.save(amine);
            userRepository.save(ahmed);
            userRepository.save(ali);
            userRepository.save(admin);

            accountRepository.save(
                    new Account(amine, new BigDecimal("1000.00"), AccountStatus.ACTIVE)
            );

            accountRepository.save(
                    new Account(ahmed, new BigDecimal("1000.00"), AccountStatus.ACTIVE)
            );

            accountRepository.save(
                    new Account(ali, new BigDecimal("1000.00"), AccountStatus.ACTIVE)
            );

            System.out.println("Test data created successfully.");
        };
    }
}