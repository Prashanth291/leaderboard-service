package com.contest.leaderboard_service.repository;

import com.contest.leaderboard_service.entity.ProblemAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProblemAttemptRepository
        extends JpaRepository<ProblemAttempt, String> {

    Optional<ProblemAttempt> findByContestIdAndUserIdAndProblemId(
            String contestId, String userId, String problemId);
}