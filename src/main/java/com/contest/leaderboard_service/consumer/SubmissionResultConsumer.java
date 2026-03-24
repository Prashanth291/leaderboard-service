package com.contest.leaderboard_service.consumer;

import com.contest.leaderboard_service.dto.SubmissionResultEvent;
import com.contest.leaderboard_service.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionResultConsumer {

    private final LeaderboardService leaderboardService;

    @KafkaListener(
            topics = "submission-result",
            groupId = "leaderboard-service",
            containerFactory = "submissionKafkaListenerContainerFactory"
    )
    public void consume(SubmissionResultEvent event) {
        log.info("Received submission result -> submissionId={} verdict={}",
                event.getSubmissionId(), event.getVerdict());
        leaderboardService.processResult(event);
    }
}