# Leaderboard Service

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.6-green?style=for-the-badge&logo=springboot)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-3.7-black?style=for-the-badge&logo=apachekafka)
![Spring Kafka](https://img.shields.io/badge/Spring%20Kafka-3.2.5-green?style=for-the-badge&logo=spring)
![Redis](https://img.shields.io/badge/Redis-3.0-red?style=for-the-badge&logo=redis)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=for-the-badge&logo=postgresql)
![JPA](https://img.shields.io/badge/Spring%20Data%20JPA-Hibernate%206.5-green?style=for-the-badge&logo=hibernate)
![WebSocket](https://img.shields.io/badge/WebSocket-STOMP-purple?style=for-the-badge&logo=websocket)
![Maven](https://img.shields.io/badge/Maven-3.9+-red?style=for-the-badge&logo=apachemaven)
![Lombok](https://img.shields.io/badge/Lombok-Utility-gray?style=for-the-badge)

> Part of the [contest-manager](https://github.com/contest-manager) platform — a distributed coding contest system built with Java microservices.


---

## Overview

The Leaderboard Service is a real-time ranking engine for the contest-manager platform. It consumes Kafka events from the Contest and Execution services, computes Codeforces-style scores and penalties, maintains a live sorted leaderboard in Redis, and broadcasts rank changes over WebSocket to connected clients.

This service owns no user-facing write operations. It is purely reactive — it listens, computes, persists, and broadcasts.

---

## Platform Architecture

The contest-manager platform is composed of five independent microservices:

| Service | Responsibility | Port |
|---|---|---|
| **Auth Service** | JWT-based authentication and user management | 8081 |
| **Contest Service** | Contest and problem lifecycle management | 8082 |
| **Execution Service** | Sandboxed code execution and verdict evaluation | 8084 |
| **Leaderboard Service** | Real-time scoring, ranking, and WebSocket broadcast | 8085 |

Each service has its own PostgreSQL database. Communication between services is event-driven via Apache Kafka.

```
Contest Service ──────────────────────────────────────────────┐
  publishes → contest-events                                   │
                                                               ▼
Execution Service ──────────────────────────────────► Leaderboard Service
  publishes → submission-result                         ├── PostgreSQL (leaderboard_db)
                                                        ├── Redis (sorted sets)
                                                        └── WebSocket → clients
```

---

## Responsibilities

- Consumes `contest-events` topic → persists contest metadata and problem list
- Consumes `submission-result` topic → computes score and penalty on AC verdict
- Tracks wrong attempt count per user per problem per contest
- Applies Codeforces-style scoring with a 30% score floor
- Updates Redis sorted set on every AC for O(log n) rank queries
- Serves ranked leaderboard via REST with Redis → DB fallback
- Broadcasts live rank updates to WebSocket subscribers

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.6 |
| Messaging | Apache Kafka 3.7 / Spring Kafka 3.2.5 |
| Cache / Ranking | Redis 3.0 (sorted sets via Lettuce) |
| Database | PostgreSQL 16 / Spring Data JPA / Hibernate 6.5 |
| Real-time | Spring WebSocket + STOMP |
| Build | Maven |
| Utilities | Lombok |

---

## Database Schema

This service maintains four tables in `leaderboard_db`:

### `contest_meta`
Stores contest start/end times used for penalty calculation.

| Column | Type | Description |
|---|---|---|
| contest_id | VARCHAR PK | Matches Contest Service ID |
| title | VARCHAR | Contest title |
| start_time | TIMESTAMP | Used to compute minutes elapsed |
| end_time | TIMESTAMP | Contest end boundary |

### `contest_problems`
Stores problem metadata and base score per contest.

| Column | Type | Description |
|---|---|---|
| id | UUID PK | |
| contest_id | VARCHAR | FK reference |
| problem_id | VARCHAR | |
| problem_label | VARCHAR | A, B, C, D |
| problem_order | INT | Display order |
| score | INT | Base score for this problem in this contest |
| title | VARCHAR | |

### `leaderboard_entries`
One row per user per contest. Updated on every AC.

| Column | Type | Description |
|---|---|---|
| id | UUID PK | |
| contest_id | VARCHAR | |
| user_id | VARCHAR | |
| username | VARCHAR | |
| solved_count | INT | Total problems solved |
| total_score | INT | Cumulative Codeforces-style score |
| total_penalty | INT | Cumulative penalty minutes |
| last_ac_at | TIMESTAMP | Timestamp of most recent AC |
| updated_at | TIMESTAMP | |

**Unique constraint:** `(contest_id, user_id)`

### `problem_attempts`
One row per user per problem per contest. Tracks wrong attempts and earned score.

| Column | Type | Description |
|---|---|---|
| id | UUID PK | |
| contest_id | VARCHAR | |
| user_id | VARCHAR | |
| problem_id | VARCHAR | |
| solved | BOOLEAN | Whether AC has been received |
| wrong_attempts | INT | WA count before AC |
| penalty_for_problem | INT | Penalty activated at AC moment |
| score_earned | INT | Final score after WA deduction |
| solved_at | TIMESTAMP | |

**Unique constraint:** `(contest_id, user_id, problem_id)`

---

## Scoring Algorithm

This service implements a Codeforces-style scoring model.

### Score Formula

```
earnedScore = max(baseScore - (50 × wrongAttempts), baseScore × 0.3)
```

| Wrong Attempts | Score (base = 500) |
|---|---|
| 0 | 500 |
| 1 | 450 |
| 2 | 400 |
| 3 | 350 |
| 8+ | 150 (floor at 30%) |

### Penalty Formula

```
penalty = minutesElapsed + (wrongAttempts × 10)
```

Penalty is only activated at the moment of AC. Wrong attempts before AC are queued silently.

### Ranking Rules

1. Higher `totalScore` → better rank
2. Equal score → lower `totalPenalty` → better rank

### Redis Sorted Set Formula

Redis `ZRANGE` returns lowest score first, so rank is stored as:

```
redisScore = (10_000_000 - totalScore) × 1_000 + totalPenalty
```

This inverts the score so higher contest scores map to lower Redis scores, giving natural ascending rank order.

---

## Kafka Events

### Consumed — `contest-events`

Published by Contest Service when a contest starts.

```json
{
  "contestId": "contest_01",
  "title": "Weekly Round 1",
  "startTime": "2026-03-24T05:00:00Z",
  "endTime": "2026-03-24T07:00:00Z",
  "problems": [
    { "problemId": "prob_01", "order": 0, "label": "A", "title": "Two Sum", "score": 500 },
    { "problemId": "prob_02", "order": 1, "label": "B", "title": "Binary Search", "score": 300 }
  ]
}
```

### Consumed — `submission-result`

Published by Execution Service after code is evaluated.

```json
{
  "submissionId": "sub_001",
  "userId": "user_alice",
  "username": "alice",
  "contestId": "contest_01",
  "problemId": "prob_01",
  "verdict": "AC",
  "submittedAt": "2026-03-24T05:15:00Z"
}
```

Verdict values: `AC` (accepted) or `WA` (wrong answer). Other verdicts are treated as WA.

---

## REST API

### `GET /leaderboard/{contestId}`

Returns the ranked leaderboard for a contest. Reads from Redis sorted set. Falls back to PostgreSQL if Redis is empty and resyncs Redis automatically.

**Query params:**

| Param | Default | Description |
|---|---|---|
| page | 0 | Page number |
| size | 50 | Results per page |

**Response:**

```json
[
  {
    "userId": "user_charlie",
    "username": "charlie",
    "solvedCount": 2,
    "totalScore": 800,
    "totalPenalty": 28,
    "rank": 1,
    "lastAcAt": "2026-03-24T05:20:00Z"
  }
]
```

### `GET /actuator/health`

Spring Boot Actuator health check.

```json
{ "status": "UP" }
```

---

## WebSocket

Clients can subscribe to live leaderboard updates via STOMP over WebSocket.

**Endpoint:** `ws://localhost:8085/ws`

**Subscribe to:**
```
/topic/leaderboard/{contestId}
```

**Payload on every AC or WA:**

```json
{
  "contestId": "contest_01",
  "userId": "user_alice",
  "username": "alice",
  "problemId": "prob_01",
  "problemLabel": "A",
  "verdict": "AC",
  "solvedCount": 2,
  "totalScore": 750,
  "totalPenalty": 70,
  "scoreEarned": 250,
  "penaltyAdded": 55,
  "newRank": 2
}
```

---

## Configuration

```yaml
server:
  port: 8085

spring:
  application:
    name: leaderboard-service
  datasource:
    url: jdbc:postgresql://localhost:5432/leaderboard_db
    username: postgres
    password: your_password
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  data:
    redis:
      host: localhost
      port: 6379
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: leaderboard-service
      auto-offset-reset: earliest
```

---

## Running Locally

### Prerequisites

| Requirement | Version |
|---|---|
| Java | 21 |
| Maven | 3.9+ |
| PostgreSQL | 16 |
| Apache Kafka | 3.7+ |
| Redis | 3.0+ |

### 1. Create the database

```sql
CREATE DATABASE leaderboard_db;
```

### 2. Start Zookeeper

```powershell
& "C:\kf\bin\windows\zookeeper-server-start.bat" "C:\kf\config\zookeeper.properties"
```

### 3. Start Kafka

```powershell
& "C:\kf\bin\windows\kafka-server-start.bat" "C:\kf\config\server.properties"
```

### 4. Create Kafka topics

```powershell
& "C:\kf\bin\windows\kafka-topics.bat" --create --topic contest-events --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
& "C:\kf\bin\windows\kafka-topics.bat" --create --topic submission-result --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
```

### 5. Start Redis

```powershell
& "C:\redis\redis-server.exe" "C:\redis\redis.windows.conf"
```

### 6. Run the service

```bash
mvn spring-boot:run
```

Service starts on `http://localhost:8085`

---

## Key Design Decisions

**Why Redis sorted sets?**
Rank queries on a leaderboard need to be O(log n). A SQL `ORDER BY` on every request would not scale under concurrent reads during a live contest. Redis `ZADD` and `ZRANK` give sub-millisecond rank lookups.

**Why is penalty only activated at AC?**
This matches the Codeforces model. Wrong attempts are counted silently. If a user never solves the problem, their wrong attempts have no effect on the leaderboard. Penalty is only meaningful when the problem is solved.

**Why a DB fallback in the controller?**
Redis is an in-memory cache. If it restarts or is flushed, the leaderboard would appear empty. The fallback reads from PostgreSQL and resyncs Redis automatically, so the service is self-healing without any manual intervention.

**Why separate Kafka consumer factories?**
The two topics (`contest-events` and `submission-result`) deserialize into different DTO types. Using separate consumer factories with separate deserializer configurations prevents class cast errors and keeps consumer configuration explicit and isolated.

---

## Project Structure

```
leaderboard-service/
├── src/main/java/com/contest/leaderboard_service/
│   ├── config/
│   │   ├── KafkaConsumerConfig.java
│   │   └── WebSocketConfig.java
│   ├── consumer/
│   │   ├── ContestEventConsumer.java
│   │   └── SubmissionResultConsumer.java
│   ├── controller/
│   │   └── LeaderboardController.java
│   ├── dto/
│   │   ├── ContestStartedEvent.java
│   │   ├── SubmissionResultEvent.java
│   │   ├── LeaderboardEntryDto.java
│   │   └── LeaderboardUpdateDto.java
│   ├── entity/
│   │   ├── ContestMeta.java
│   │   ├── ContestProblem.java
│   │   ├── LeaderboardEntry.java
│   │   └── ProblemAttempt.java
│   ├── repository/
│   │   ├── ContestMetaRepository.java
│   │   ├── ContestProblemRepository.java
│   │   ├── LeaderboardEntryRepository.java
│   │   └── ProblemAttemptRepository.java
│   ├── service/
│   │   ├── LeaderboardService.java
│   │   └── LeaderboardRedisService.java
│   └── LeaderboardServiceApplication.java
└── src/main/resources/
    └── application.yml
```

---

## Related Services

| Service | Repository |
|---|---|
| Auth Service | [contest-manager/auth-service](https://github.com/Prashanth291/auth-service/) |
| Contest Service | [contest-manager/contest-service](https://github.com/Prashanth291/contest-service) |
| Execution Service | [contest-manager/execution-service](https://github.com/klsharsha/execution-service) |

---

## License

This project is part of a personal learning initiative and is not licensed for commercial use.
