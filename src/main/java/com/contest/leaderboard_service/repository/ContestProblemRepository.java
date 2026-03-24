package com.contest.leaderboard_service.repository;

import com.contest.leaderboard_service.entity.ContestProblem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContestProblemRepository
        extends JpaRepository<ContestProblem, String> {

    Optional<ContestProblem> findByContestIdAndProblemId(
            String contestId, String problemId);
}

