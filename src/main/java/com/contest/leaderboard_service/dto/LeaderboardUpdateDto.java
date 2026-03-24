package com.contest.leaderboard_service.dto;

import lombok.Data;

@Data
public class LeaderboardUpdateDto {
    private String contestId;
    private String userId;
    private String username;
    private String problemId;
    private String problemLabel;
    private String verdict;
    private int    solvedCount;
    private int    totalPenalty;
    private int    newRank;
    private int    penaltyAdded;
    private int totalScore;
    private int scoreEarned;
}