package com.contest.leaderboard_service.repository;

import com.contest.leaderboard_service.entity.ContestMeta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestMetaRepository
        extends JpaRepository<ContestMeta, String> {
}