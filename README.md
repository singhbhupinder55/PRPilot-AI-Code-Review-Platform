# PRPilot

AI-powered code review platform. Listens for GitHub pull request events,
analyzes the diff using RAG over the codebase, and posts review comments
back to the PR — built as a Kafka-backed microservices system.

[![webhook-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/webhook-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/webhook-service-ci.yml)
[![ingestion-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/ingestion-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/ingestion-service-ci.yml)
[![review-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/review-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/review-service-ci.yml)
[![notification-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/notification-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/notification-service-ci.yml)

## 🚀 Live Demo

**Webhook endpoint:** `https://prpilot-ai-code-review-platform-production.up.railway.app/webhooks/github`

To see PRPilot in action: open a pull request on any connected GitHub repo —
Claude will automatically post a code review comment within ~30 seconds.

## Status

✅ **Backend complete and deployed to production.**

| Service | Status |
|---|---|
| `webhook-service` | ✅ Live on Railway — receives GitHub PR webhooks, HMAC-SHA256 verified |
| `ingestion-service` | ✅ Live on Railway — clones repos, chunks code, generates embeddings |
| `review-service` | ✅ Live on Railway — RAG retrieval + Claude-powered review |
| `notification-service` | ✅ Live on Railway — posts review comments back to GitHub PRs |
| `frontend` | ⏳ Planned — React dashboard |

## Architecture

```
GitHub PR opened
      │
      ▼
webhook-service (:8081)          ← Railway
  HMAC-SHA256 verified
      │
      ▼ Kafka: pr.events         ← Confluent Cloud
      │
      ├─────────────────────────────────────┐
      ▼                                     ▼
ingestion-service (:8082)        review-service (:8083)
  JGit shallow clone               embed PR metadata (Voyage AI)
  chunk source files               pgvector similarity search
  embed chunks (Voyage AI)         Claude API → structured review
  store in pgvector                publish to reviews.completed
      │                                     │
      ▼                                     ▼ Kafka: reviews.completed
Neon Postgres + pgvector                    │
(code_chunks table)              notification-service (:8084)
                                   POST /repos/.../issues/.../comments
                                   → GitHub PR comment 🤖
```

## Tech stack

- **Backend:** Java 21, Spring Boot 3.5, 4 microservices
- **Messaging:** Apache Kafka (Confluent Cloud)
- **Data:** PostgreSQL + pgvector on Neon (HNSW index, cosine similarity)
- **Schema migrations:** Flyway (versioned globally across services)
- **Repo cloning:** JGit (pure-Java, shallow clones)
- **Embeddings:** Voyage AI (`voyage-code-2`, 1536-dim, code-specialized)
- **AI review:** Claude API (`claude-haiku-4-5`)
- **GitHub integration:** Webhooks (inbound) + REST API (outbound PR comments)
- **Testing:** JUnit 5, Testcontainers, Mockito, Awaitility — 31 tests total
- **CI:** GitHub Actions — 4 workflows, CI-first development
- **Deployment:** Railway (4 services), Neon (Postgres), Confluent Cloud (Kafka)
- **Frontend (planned):** React, TypeScript, Vercel

## Local development

Requires Docker, Java 21, Gradle, and env vars in `~/.zshrc`:

```bash
export VOYAGE_API_KEY="your-voyage-key"
export ANTHROPIC_API_KEY="your-anthropic-key"
export GITHUB_TOKEN_PRPILOT="your-github-pat"
```

Start infrastructure:

```bash
docker compose up -d   # Postgres + pgvector, Redis, Kafka
```

Run services (each in its own terminal, `source ~/.zshrc` first):

```bash
cd services/webhook-service      && ./gradlew bootRun   # :8081
cd services/ingestion-service    && ./gradlew bootRun   # :8082
cd services/review-service       && ./gradlew bootRun   # :8083
cd services/notification-service && ./gradlew bootRun   # :8084
```

## Services

### webhook-service

- HMAC-SHA256 signature verification (constant-time, prevents timing attacks)
- Kafka `pr.events` producer, keyed by repo for ordering guarantees
- Idempotent producer (`acks=all`, `enable.idempotence=true`)
- **9 tests:** HMAC unit tests + Testcontainers integration tests

### ingestion-service

- `ErrorHandlingDeserializer` prevents infinite retry on malformed messages
- JGit shallow clone (depth=1), 60-line chunking, source file filtering
- Voyage AI batched embedding with exponential backoff retry on rate limits
- pgvector HNSW index for sub-linear approximate nearest-neighbor search
- **11 tests:** smoke test + CodeChunker unit tests

### review-service

- Separate Kafka consumer group from ingestion
- Query embedding (`input_type: query`) + pgvector cosine similarity search
- Top-8 chunk retrieval → structured Claude prompt → 8,000+ char review
- Review status tracking: `PENDING` → `COMPLETED` / `FAILED`
- Idempotent: duplicate `deliveryId` skipped to prevent double-billing
- **7 tests:** prompt unit tests + Testcontainers integration tests

### notification-service

- Consumes `reviews.completed`, posts comment via GitHub REST API
- Prepends `🤖 PRPilot AI Review` header for clear attribution
- No database — pure Kafka consumer + HTTP client
- **4 tests:** unit tests + Testcontainers integration test

## Production infrastructure

| Component | Provider | Notes |
|---|---|---|
| 4 Spring Boot services | Railway (Hobby) | Auto-deploy from GitHub |
| Postgres + pgvector | Neon | Serverless, pgvector enabled |
| Kafka | Confluent Cloud | `pr.events` (3 partitions), `reviews.completed` (1 partition) |
| Embeddings | Voyage AI | `voyage-code-2`, 1536-dim |
| AI review | Anthropic | `claude-haiku-4-5` |

## License

MIT