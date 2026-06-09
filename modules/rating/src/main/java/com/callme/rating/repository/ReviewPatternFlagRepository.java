package com.callme.rating.repository;

import com.callme.rating.entity.ReviewPatternFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewPatternFlagRepository extends JpaRepository<ReviewPatternFlag, UUID> {

    /** CLAUDE.md F.3 — CSKH worklist of auto-raised retaliation-pattern flags, most recent first. */
    List<ReviewPatternFlag> findAllByOrderByFlaggedAtDesc();
}
