package com.contest.leaderboard_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Data
@Entity
@Table(name = "leaderboard_entries",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"contest_id", "user_id"}))
public class LeaderboardEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "contest_id", nullable = false)
    private String contestId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    private String username;

    @Column(name = "solved_count")
    private int solvedCount = 0;

    @Column(name = "total_penalty")
    private int totalPenalty = 0;

    @Column(name = "total_score")
    private int totalScore = 0;

    @Column(name = "last_ac_at")
    private Instant lastAcAt;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}