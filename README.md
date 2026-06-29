# PRPilot

AI-powered code review platform. Listens for GitHub pull request events,
analyzes the diff using RAG over the codebase, and posts review comments
back to the PR — built as a Kafka-backed microservices system.

[![webhook-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/webhook-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/webhook-service-ci.yml)

## Status

🚧 Actively in development. Not yet deployed.

| Service | Status |
|---|---|
| `webhook-service` | ✅ Built — receives GitHub PR webhooks, verifies HMAC-SHA256 signatures, publishes events to Kafka |
| `ingestion-service` | 🔜 In progress — clones repos, chunks code, generates embeddings, stores in pgvector |
| `review-service` | ⏳ Planned — RAG retrieval + Claude-powered PR review |
| `notification-service` | ⏳ Planned — posts review comments back to GitHub |
| `api-gateway` | ⏳ Planned |
| `frontend` | ⏳ Planned — React dashboard |

## Architecture

```
GitHub PR event
      │
      ▼
webhook-service ──► Kafka (pr.events) ──► ingestion-service ──► Postgres + pgvector
                                                                       │
                                                                       ▼
                                                            review-service (Claude + RAG)
                                                                       │
                                                                       ▼
                                                            notification-service ──► GitHub PR comment
```

## Tech stack

- **Backend:** Java 21, Spring Boot 3.5
- **Messaging:** Apache Kafka
- **Data:** PostgreSQL + pgvector, Redis
- **AI:** Claude API (Anthropic)
- **Frontend (planned):** React, TypeScript
- **Infra:** Docker Compose (local), GitHub Actions (CI)

## Local development

Requires Docker, Java 21, and Gradle.

```bash
# Start local infrastructure (Postgres + pgvector, Redis, Kafka)
docker compose up -d

# Run a service, e.g. webhook-service
cd services/webhook-service
./gradlew bootRun
```

## webhook-service

Entry point for GitHub PR events.

- Validates incoming webhooks via HMAC-SHA256 signature verification
  (constant-time comparison, prevents forged requests)
- Publishes validated events to a Kafka topic (`pr.events`), keyed by
  repository for per-repo ordering
- Idempotent Kafka producer (`acks=all`) for delivery guarantees
- Tested with JUnit 5 (unit tests on signature verification) and
  Testcontainers (integration tests against a real Kafka broker)

```bash
cd services/webhook-service
./gradlew test       # run all tests
./gradlew bootRun     # start the service on :8081
```

## License

MIT
