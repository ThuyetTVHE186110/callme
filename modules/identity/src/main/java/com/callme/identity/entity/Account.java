package com.callme.identity.entity;

import com.callme.common.security.AccountRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * The login identity behind a Customer or Driver profile. Kept separate from the
 * domain profile entities (Customer/Driver live in their own bounded contexts) so
 * identity owns "who can log in" while driver/identity own "who this person is in
 * the business domain". `profileId` bridges the two — it is the Customer.id for
 * CUSTOMER accounts, or the Driver.id for DRIVER accounts (see DriverRegistrationPort).
 */
@Entity
@Table(name = "accounts", uniqueConstraints = @UniqueConstraint(columnNames = "phoneNumber"))
@Getter
@NoArgsConstructor(force = true)
public class Account {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountRole role;

    @Column(nullable = false)
    private UUID profileId;

    private boolean active;

    public Account(String phoneNumber, String passwordHash, AccountRole role, UUID profileId) {
        this.phoneNumber = phoneNumber;
        this.passwordHash = passwordHash;
        this.role = role;
        this.profileId = profileId;
        this.active = true;
    }

    public void deactivate() {
        if (!this.active) {
            throw new IllegalStateException("Account already deactivated: " + this.id);
        }
        this.active = false;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }
}
