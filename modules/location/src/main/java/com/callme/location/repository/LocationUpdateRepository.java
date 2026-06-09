package com.callme.location.repository;

import com.callme.location.entity.LocationUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LocationUpdateRepository extends JpaRepository<LocationUpdate, UUID> {

    List<LocationUpdate> findByDriverIdOrderByRecordedAtDesc(UUID driverId);

    /** CLAUDE.md C.8 — the GPS trail for one trip's driving window, oldest first, ready to be summed into a travelled distance. */
    List<LocationUpdate> findByDriverIdAndRecordedAtBetweenOrderByRecordedAtAsc(UUID driverId, Instant from, Instant to);
}
