package com.contest.leaderboard_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Data
@Entity
@Table(name = "problem_attempts",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"contest_id", "user_id", "problem_id"}))
public class ProblemAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "contest_id")
    private String contestId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "problem_id")
    private String problemId;

    private boolean solved = false;

    @Column(name = "wrong_attempts")
    private int wrongAttempts = 0;

    @Column(name = "penalty_for_problem")
    private int penaltyForProblem = 0;

    @Column(name = "score_earned")
    private int scoreEarned = 0;

    @Column(name = "solved_at")
    private Instant solvedAt;
}