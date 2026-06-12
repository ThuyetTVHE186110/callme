package com.callme.identity.service.impl;

import com.callme.common.exception.ForbiddenException;
import com.callme.common.security.AccountRole;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.identity.entity.Account;
import com.callme.identity.repository.AccountRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OWASP A01 / CLAUDE.md G.5 — the suspension lever behind the JWT filter's
 * per-request {@code AccountStatusPort} check. Admin-only, never self-targeting
 * (a single-admin deployment must not be able to brick its own suspension lever).
 */
class AccountServiceImplTest {

    private static final UUID TARGET_ACCOUNT_ID = UUID.randomUUID();

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder =
            mock(org.springframework.security.crypto.password.PasswordEncoder.class);
    private final AccountServiceImpl service = new AccountServiceImpl(accountRepository, passwordEncoder);

    private final AuthenticatedAccount admin = new AuthenticatedAccount(UUID.randomUUID(), null, AccountRole.ADMIN);
    private final AuthenticatedAccount customer = new AuthenticatedAccount(UUID.randomUUID(), UUID.randomUUID(), AccountRole.CUSTOMER);

    private Account targetAccount() {
        var account = new Account("0901234567", "hash", AccountRole.DRIVER, UUID.randomUUID());
        when(accountRepository.findById(TARGET_ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return account;
    }

    @Test
    void adminCanSuspendAnAccount() {
        var account = targetAccount();

        service.deactivate(TARGET_ACCOUNT_ID, admin);

        assertThat(account.isActive()).isFalse();
    }

    @Test
    void adminCanLiftASuspension() {
        var account = targetAccount();
        account.deactivate();

        service.reactivate(TARGET_ACCOUNT_ID, admin);

        assertThat(account.isActive()).isTrue();
    }

    @Test
    void nonAdminsCannotTouchSuspension() {
        assertThatThrownBy(() -> service.deactivate(TARGET_ACCOUNT_ID, customer))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.reactivate(TARGET_ACCOUNT_ID, customer))
                .isInstanceOf(ForbiddenException.class);
    }

    /** A single-admin deployment must never be able to lock out its only suspension lever. */
    @Test
    void anAdminCannotSuspendThemselves() {
        assertThatThrownBy(() -> service.deactivate(admin.accountId(), admin))
                .isInstanceOf(ForbiddenException.class);
    }

    /** CLAUDE.md §4.8.3 — suspension revokes live tokens; lifting it must not resurrect pre-incident sessions. */
    @Test
    void suspensionBumpsTheTokenVersion() {
        var account = targetAccount();
        int before = account.getTokenVersion();

        service.deactivate(TARGET_ACCOUNT_ID, admin);

        assertThat(account.getTokenVersion()).isEqualTo(before + 1);
    }

    /** CLAUDE.md §4.8.2 — current password gate + token revocation on change. */
    @Test
    void changingThePasswordRequiresTheCurrentOneAndRevokesAllTokens() {
        var account = targetAccount();
        var owner = new AuthenticatedAccount(TARGET_ACCOUNT_ID, account.getProfileId(), AccountRole.DRIVER);
        when(accountRepository.findById(TARGET_ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("old-pass", account.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("new-pass-123")).thenReturn("new-hash");
        int before = account.getTokenVersion();

        service.changePassword(owner, "old-pass", "new-pass-123");

        assertThat(account.getPasswordHash()).isEqualTo("new-hash");
        assertThat(account.getTokenVersion()).isEqualTo(before + 1);
    }

    @Test
    void changingThePasswordWithAWrongCurrentPasswordIsRejected() {
        var account = targetAccount();
        var owner = new AuthenticatedAccount(TARGET_ACCOUNT_ID, account.getProfileId(), AccountRole.DRIVER);
        when(passwordEncoder.matches("wrong", account.getPasswordHash())).thenReturn(false);
        int before = account.getTokenVersion();

        assertThatThrownBy(() -> service.changePassword(owner, "wrong", "new-pass-123"))
                .isInstanceOf(com.callme.common.exception.UnauthorizedException.class);

        assertThat(account.getTokenVersion()).isEqualTo(before);
    }
}
