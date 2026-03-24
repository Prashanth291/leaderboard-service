package com.contest.leaderboard_service.dto;

import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
public class ContestStartedEvent {
    private String contestId;
    private String title;
    private Instant startTime;
    private Instant endTime;
    private List<ProblemInfo> problems;

    @Data
    public static class ProblemInfo {
        private String problemId;
        private int    order;
        private String label;
        private String title;
        private int    score;
    }
}
