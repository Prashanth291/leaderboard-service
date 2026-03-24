package com.contest.leaderboard_service.service;

import com.contest.leaderboard_service.dto.ContestStartedEvent;
import com.contest.leaderboard_service.dto.LeaderboardUpdateDto;
import com.contest.leaderboard_service.dto.SubmissionResultEvent;
import com.contest.leaderboard_service.entity.*;
import com.contest.leaderboard_service.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final LeaderboardEntryRepository  entryRepo;
    private final ProblemAttemptRepository    attemptRepo;
    private final ContestMetaRepository       contestMetaRepo;
    private final ContestProblemRepository    contestProblemRepo;
    private final SimpMessagingTemplate       messagingTemplate;
    private final LeaderboardRedisService     redisService;

    // ─────────────────────────────────────────
    // Called when CONTEST_STARTED arrives
    // ─────────────────────────────────────────
    @Transactional
    public void handleContestStarted(ContestStartedEvent event) {

        ContestMeta meta = new ContestMeta();
        meta.setContestId(event.getContestId());
        meta.setStartTime(event.getStartTime());
        meta.setEndTime(event.getEndTime());
        meta.setTitle(event.getTitle());
        contestMetaRepo.save(meta);

        for (ContestStartedEvent.ProblemInfo p : event.getProblems()) {
            ContestProblem cp = new ContestProblem();
            cp.setContestId(event.getContestId());
            cp.setProblemId(p.getProblemId());
            cp.setProblemOrder(p.getOrder());
            cp.setProblemLabel(p.getLabel());
            cp.setTitle(p.getTitle());
            cp.setScore(p.getScore());
            contestProblemRepo.save(cp);
        }

        log.info("Contest saved: contestId={} title={} problems={}",
                event.getContestId(),
                event.getTitle(),
                event.getProblems().size());
    }

    /*
        / Called when SUBMISSION_RESULT arrives
    */
    @Transactional
    public void processResult(SubmissionResultEvent event) {
        log.info("Processing: verdict={} userId={} problemId={}",
                event.getVerdict(),
                event.getUserId(),
                event.getProblemId());

        if ("AC".equals(event.getVerdict())) {
            handleAccepted(event);
        } else {
            handleWrongAttempt(event);
        }
    }

    // ─────────────────────────────────────────
    // AC path — full update
    // ─────────────────────────────────────────
    private void handleAccepted(SubmissionResultEvent event) {

        // 1. Idempotency — if already solved ignore completely
        ProblemAttempt attempt = getOrCreateAttempt(event);
        if (attempt.isSolved()) {
            log.info("Already solved — ignoring duplicate AC");
            return;
        }

        // 2. Mark problem as solved
        attempt.setSolved(true);
        attempt.setSolvedAt(event.getSubmittedAt());

        // 3. Compute penalty at AC moment
        ContestMeta contest = contestMetaRepo
                .findById(event.getContestId())
                .orElseThrow(() -> new RuntimeException(
                        "Contest not found: " + event.getContestId()));

        long minutesElapsed = ChronoUnit.MINUTES.between(
                contest.getStartTime(),
                event.getSubmittedAt()
        );

        int penalty = (int) minutesElapsed + (attempt.getWrongAttempts() * 10);
        attempt.setPenaltyForProblem(penalty);

        log.info("Penalty computed: minutesElapsed={} wrongAttempts={} penalty={}",
                minutesElapsed,
                attempt.getWrongAttempts(),
                penalty);

        // 4. Compute score — Codeforces style
        int baseScore = contestProblemRepo
                .findByContestIdAndProblemId(
                        event.getContestId(),
                        event.getProblemId())
                .map(ContestProblem::getScore)
                .orElse(100);

        int earnedScore = computeScore(baseScore, attempt.getWrongAttempts());
        attempt.setScoreEarned(earnedScore);

        attemptRepo.save(attempt);

        log.info("Score computed: baseScore={} wrongAttempts={} earnedScore={}",
                baseScore,
                attempt.getWrongAttempts(),
                earnedScore);

        // 5. Update leaderboard entry
        LeaderboardEntry entry = getOrCreateEntry(event);
        entry.setSolvedCount(entry.getSolvedCount() + 1);
        entry.setTotalPenalty(entry.getTotalPenalty() + penalty);
        entry.setTotalScore(entry.getTotalScore() + earnedScore);
        entry.setLastAcAt(event.getSubmittedAt());
        entry.setUpdatedAt(Instant.now());
        entryRepo.save(entry);

        // 6. Update Redis sorted set — O(log n)
        redisService.updateRank(
                event.getContestId(),
                event.getUserId(),
                entry.getTotalScore(),
                entry.getTotalPenalty()
        );

        // 7. Get rank from Redis instantly
        int newRank = redisService.getUserRank(
                event.getContestId(),
                event.getUserId()
        );

        log.info("Leaderboard updated: userId={} solved={} score={} penalty={} rank={}",
                event.getUserId(),
                entry.getSolvedCount(),
                entry.getTotalScore(),
                entry.getTotalPenalty(),
                newRank);

        // 8. Broadcast
        broadcast(event, entry, newRank, penalty, earnedScore);
    }

    // ─────────────────────────────────────────
    // Codeforces style scoring
    // Deduct 50 per wrong attempt, floor at 30% of base
    // ─────────────────────────────────────────
    private int computeScore(int baseScore, int wrongAttempts) {
        int floor = (int)(baseScore * 0.3);
        int score  = baseScore - (50 * wrongAttempts);
        return Math.max(score, floor);
    }

    // ─────────────────────────────────────────
    // WA path — only increment counter
    // ─────────────────────────────────────────
    private void handleWrongAttempt(SubmissionResultEvent event) {

        ProblemAttempt attempt = getOrCreateAttempt(event);

        if (!attempt.isSolved()) {
            attempt.setWrongAttempts(attempt.getWrongAttempts() + 1);
            attemptRepo.save(attempt);

            log.info("Wrong attempt recorded: userId={} problemId={} totalWrong={}",
                    event.getUserId(),
                    event.getProblemId(),
                    attempt.getWrongAttempts());
        }

        // Broadcast WA so frontend can flash red — score and penalty unchanged
        LeaderboardEntry entry = getOrCreateEntry(event);
        int currentRank = redisService.getUserRank(
                event.getContestId(),
                event.getUserId()
        );
        broadcast(event, entry, currentRank, 0, 0);
    }

    // ─────────────────────────────────────────
    // Broadcast delta to WebSocket clients
    // ─────────────────────────────────────────
    private void broadcast(SubmissionResultEvent event,
                           LeaderboardEntry entry,
                           int newRank,
                           int penaltyAdded,
                           int scoreEarned) {

        String label = contestProblemRepo
                .findByContestIdAndProblemId(
                        event.getContestId(),
                        event.getProblemId())
                .map(ContestProblem::getProblemLabel)
                .orElse("?");

        LeaderboardUpdateDto dto = new LeaderboardUpdateDto();
        dto.setContestId(event.getContestId());
        dto.setUserId(event.getUserId());
        dto.setUsername(event.getUsername());
        dto.setProblemId(event.getProblemId());
        dto.setProblemLabel(label);
        dto.setVerdict(event.getVerdict());
        dto.setSolvedCount(entry.getSolvedCount());
        dto.setTotalPenalty(entry.getTotalPenalty());
        dto.setTotalScore(entry.getTotalScore());
        dto.setScoreEarned(scoreEarned);
        dto.setNewRank(newRank);
        dto.setPenaltyAdded(penaltyAdded);

        messagingTemplate.convertAndSend(
                "/topic/leaderboard/" + event.getContestId(),
                dto
        );

        log.info("WebSocket broadcast sent: topic=/topic/leaderboard/{} verdict={} score={}",
                event.getContestId(),
                event.getVerdict(),
                scoreEarned);
    }

    // ─────────────────────────────────────────
    // Helpers — get or create DB rows
    // ─────────────────────────────────────────
    private ProblemAttempt getOrCreateAttempt(SubmissionResultEvent e) {
        return attemptRepo
                .findByContestIdAndUserIdAndProblemId(
                        e.getContestId(), e.getUserId(), e.getProblemId())
                .orElseGet(() -> {
                    ProblemAttempt a = new ProblemAttempt();
                    a.setContestId(e.getContestId());
                    a.setUserId(e.getUserId());
                    a.setProblemId(e.getProblemId());
                    return a;
                });
    }

    private LeaderboardEntry getOrCreateEntry(SubmissionResultEvent e) {
        return entryRepo
                .findByContestIdAndUserId(e.getContestId(), e.getUserId())
                .orElseGet(() -> {
                    LeaderboardEntry entry = new LeaderboardEntry();
                    entry.setContestId(e.getContestId());
                    entry.setUserId(e.getUserId());
                    entry.setUsername(e.getUsername());
                    return entry;
                });
    }
}