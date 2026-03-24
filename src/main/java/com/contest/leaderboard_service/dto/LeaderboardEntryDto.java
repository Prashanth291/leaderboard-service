package com.contest.leaderboard_service.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class LeaderboardEntryDto {
    private String  userId;
    private String  username;
    private int     solvedCount;
    private int     totalPenalty;
    private int     rank;
    private Instant lastAcAt;
    private int totalScore;
}