package com.callme.identity.adapter;

import com.callme.common.port.AccountStatusPort;
import com.callme.identity.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Implements the cross-module contract published in `common`. The infrastructure
 * module's JWT filter depends only on {@link AccountStatusPort}; Spring wires this
 * implementation in at runtime once `start` aggregates every module — identity may
 * depend on infrastructure (for token issuing) but never the other way around.
 */
@Service
public class AccountStatusPortImpl implements AccountStatusPort {

    private final AccountRepository accountRepository;

    public AccountStatusPortImpl(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public boolean isTokenValid(UUID accountId, int tokenVersion) {
        return accountRepository.findById(accountId)
                .map(account -> account.isActive() && account.getTokenVersion() == tokenVersion)
                .orElse(false);
    }
}
