package com.contest.leaderboard_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardRedisService {

    private final RedisTemplate<String, String> redisTemplate;

    private String key(String contestId) {
        return "leaderboard:" + contestId;
    }

    public void updateRank(String contestId, String userId,
                           int solvedCount, int totalPenalty) {

        double score = (100 - solvedCount) * 1_000_000.0 + totalPenalty;
        redisTemplate.opsForZSet().add(key(contestId), userId, score);

        log.info("Redis updated: contestId={} userId={} score={}",
                contestId, userId, score);
    }

    public List<String> getRankedUserIds(String contestId) {
        Set<String> ranked = redisTemplate.opsForZSet()
                .range(key(contestId), 0, -1);
        return ranked != null ? new ArrayList<>(ranked) : new ArrayList<>();
    }

    public int getUserRank(String contestId, String userId) {
        Long rank = redisTemplate.opsForZSet()
                .rank(key(contestId), userId);
        return rank != null ? (int)(rank + 1) : -1;
    }
}