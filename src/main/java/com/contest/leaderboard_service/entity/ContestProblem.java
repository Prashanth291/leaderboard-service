package com.contest.leaderboard_service.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "contest_problems",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"contest_id", "problem_id"}))
public class ContestProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "contest_id")
    private String contestId;

    @Column(name = "problem_id")
    private String problemId;

    @Column(name = "problem_order")
    private int problemOrder;

    @Column(name = "problem_label")
    private String problemLabel;

    private String title;

    @Column(name = "score")
    private int score = 100;  // default 100 if not set
}
