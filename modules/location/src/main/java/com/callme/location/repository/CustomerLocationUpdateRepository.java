package com.callme.location.repository;

import com.callme.location.entity.CustomerLocationUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerLocationUpdateRepository extends JpaRepository<CustomerLocationUpdate, UUID> {
}
