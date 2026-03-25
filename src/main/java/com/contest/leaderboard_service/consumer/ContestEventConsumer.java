package com.contest.leaderboard_service.consumer;

import com.contest.leaderboard_service.dto.ContestStartedEvent;
import com.contest.leaderboard_service.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContestEventConsumer {

    private final LeaderboardService leaderboardService;

    @KafkaListener(
            topics  = "contest-events-v2",
            groupId = "leaderboard-service-v2",
            containerFactory = "contestKafkaListenerContainerFactory"
    )
    public void consume(ContestStartedEvent event) {
        log.info("Received contest started -> contestId={} title={}",
                event.getContestId(),
                event.getTitle());
        leaderboardService.handleContestStarted(event);
    }
}