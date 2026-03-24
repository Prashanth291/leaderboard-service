package com.contest.leaderboard_service.controller;

import com.contest.leaderboard_service.dto.LeaderboardEntryDto;
import com.contest.leaderboard_service.entity.LeaderboardEntry;
import com.contest.leaderboard_service.repository.LeaderboardEntryRepository;
import com.contest.leaderboard_service.service.LeaderboardRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardEntryRepository entryRepo;
    private final LeaderboardRedisService    redisService;

    @GetMapping("/{contestId}")
    public List<LeaderboardEntryDto> getLeaderboard(
            @PathVariable String contestId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("Leaderboard requested: contestId={}", contestId);

        // Get ordered userIds from Redis
        List<String> rankedUserIds = redisService.getRankedUserIds(contestId);

        if (rankedUserIds.isEmpty()) {
            // Fallback to DB if Redis is empty (cold start or Redis restart)
            log.info("Redis empty — falling back to DB for contestId={}", contestId);
            return entryRepo
                    .findByContestIdOrderBySolvedCountDescTotalPenaltyAsc(
                            contestId, PageRequest.of(page, size))
                    .stream()
                    .map(this::toDto)
                    .toList();
        }

        // Paginate the ranked list
        int startIdx = page * size;
        int endIdx   = Math.min(startIdx + size, rankedUserIds.size());
        List<String> pageUserIds = rankedUserIds.subList(startIdx, endIdx);

        // Fetch each user's full data from DB in rank order
        List<LeaderboardEntryDto> result = new ArrayList<>();
        for (int i = 0; i < pageUserIds.size(); i++) {
            String userId = pageUserIds.get(i);
            int rank = startIdx + i + 1;
            entryRepo.findByContestIdAndUserId(contestId, userId)
                    .ifPresent(entry -> {
                        LeaderboardEntryDto dto = toDto(entry);
                        dto.setRank(rank);
                        result.add(dto);
                    });
        }

        return result;
    }

    @GetMapping("/ping")
    public String ping() {
        return "leaderboard-service is running on port 8085";
    }

    private LeaderboardEntryDto toDto(LeaderboardEntry e) {
        LeaderboardEntryDto dto = new LeaderboardEntryDto();
        dto.setUserId(e.getUserId());
        dto.setUsername(e.getUsername());
        dto.setSolvedCount(e.getSolvedCount());
        dto.setTotalPenalty(e.getTotalPenalty());
        dto.setLastAcAt(e.getLastAcAt());
        dto.setTotalScore(e.getTotalScore());
        return dto;
    }
}