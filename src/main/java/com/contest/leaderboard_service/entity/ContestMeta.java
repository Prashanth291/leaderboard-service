package com.contest.leaderboard_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Data
@Entity
@Table(name = "contest_meta")
public class ContestMeta {

    @Id
    @Column(name = "contest_id")
    private String contestId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    private String title;
}
