package com.contest.leaderboard_service.repository;

import com.contest.leaderboard_service.entity.LeaderboardEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeaderboardEntryRepository
        extends JpaRepository<LeaderboardEntry, String> {

    Optional<LeaderboardEntry> findByContestIdAndUserId(
            String contestId, String userId);

    List<LeaderboardEntry> findByContestIdOrderBySolvedCountDescTotalPenaltyAsc(
            String contestId, Pageable pageable);

    @Query("""
        SELECT COUNT(e) + 1 FROM LeaderboardEntry e
        WHERE e.contestId = :contestId
        AND (
            e.solvedCount > :solvedCount
            OR (e.solvedCount = :solvedCount
                AND e.totalPenalty < :totalPenalty)
        )
    """)
    int computeRank(
            @Param("contestId")    String contestId,
            @Param("solvedCount")  int    solvedCount,
            @Param("totalPenalty") int    totalPenalty);
}
