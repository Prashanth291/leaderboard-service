package com.contest.leaderboard_service.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class SubmissionResultEvent {
    private String submissionId;
    private String userId;
    private String username;
    private String contestId;
    private String problemId;
    private String verdict;
    private Instant submittedAt;
    private int score;
}