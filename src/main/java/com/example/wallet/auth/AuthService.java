package com.example.wallet.auth;

import com.example.wallet.account.Account;
import com.example.wallet.auth.dto.LoginRequest;
import com.example.wallet.auth.dto.LoginResponse;
import com.example.wallet.exception.ForbiddenOperationException;
import com.example.wallet.exception.InvalidCredentialsException;
import com.example.wallet.user.User;
import com.example.wallet.user.UserRepository;
import com.example.wallet.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(
                user.getUsername(),
                user.getRole()
        );

        return new LoginResponse(token);
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