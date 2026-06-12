package com.callme.identity.service.impl;

import com.callme.common.exception.ForbiddenException;
import com.callme.common.exception.NotFoundException;
import com.callme.common.exception.UnauthorizedException;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.identity.entity.Account;
import com.callme.identity.repository.AccountRepository;
import com.callme.identity.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountServiceImpl(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void deactivate(UUID accountId, AuthenticatedAccount requester) {
        requireAdmin(requester);
        if (requester.accountId().equals(accountId)) {
            // An admin locking themselves out mid-action is never the intent — and in
            // a single-admin deployment it would brick the only suspension lever.
            throw new ForbiddenException("Không thể tự đình chỉ tài khoản của chính mình");
        }
        var account = findOrThrow(accountId);
        account.deactivate();
        accountRepository.save(account);
        // Security audit trail (OWASP A09) — who suspended whom; effective on the
        // target's next request via the JWT filter's AccountStatusPort check.
        log.warn("Account {} (role {}) deactivated by admin account {} — takes effect on their next request",
                accountId, account.getRole(), requester.accountId());
    }

    @Override
    public void reactivate(UUID accountId, AuthenticatedAccount requester) {
        requireAdmin(requester);
        var account = findOrThrow(accountId);
        account.reactivate();
        accountRepository.save(account);
        log.warn("Account {} (role {}) reactivated by admin account {}", accountId, account.getRole(), requester.accountId());
    }

    /** CLAUDE.md §4.8.2 — self-service; current password required, all live tokens revoked by the entity's tokenVersion bump. */
    @Override
    public void changePassword(AuthenticatedAccount requester, String currentPassword, String newPassword) {
        var account = findOrThrow(requester.accountId());
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            // OWASP A09 — a wrong "current password" on an authenticated session is a
            // hijacked-session signal, not a typo to swallow silently.
            log.warn("Password change rejected for account {} — current password mismatch", account.getId());
            throw new UnauthorizedException("Mật khẩu hiện tại không đúng");
        }
        account.changePassword(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
        log.warn("Password changed for account {} — all previously issued tokens are now revoked", account.getId());
    }

    private void requireAdmin(AuthenticatedAccount requester) {
        if (!requester.isAdmin()) {
            throw new ForbiddenException("Chỉ quản trị viên mới có thể đình chỉ/khôi phục tài khoản");
        }
    }

    private Account findOrThrow(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản: " + accountId));
    }
}
