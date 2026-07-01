# PRPilot

AI-powered code review platform. Listens for GitHub pull request events,
analyzes the diff using RAG over the codebase, and posts review comments
back to the PR — built as a Kafka-backed microservices system.

[![webhook-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/webhook-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/webhook-service-ci.yml)
[![ingestion-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/ingestion-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/ingestion-service-ci.yml)
[![review-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/review-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/review-service-ci.yml)
[![notification-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/notification-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/notification-service-ci.yml)

## Status

🚧 Actively in development. Backend complete, deployment and frontend in progress.

| Service | Status |
|---|---|
| `webhook-service` | ✅ Built & tested — receives GitHub PR webhooks, HMAC-SHA256 verified, publishes to Kafka |
| `ingestion-service` | ✅ Built & tested — clones repos, chunks code, generates embeddings, persists to pgvector |
| `review-service` | ✅ Built & tested — RAG retrieval + Claude-powered review, publishes to reviews.completed |
| `notification-service` | ✅ Built & tested — posts Claude review comments back to GitHub PRs |
| `api-gateway` | ⏳ Planned |
| `frontend` | ⏳ Planned — React dashboard |

**The full AI pipeline is working end-to-end**: a PR opens on GitHub → webhook fires
→ code is cloned, chunked, and embedded → Claude reviews using RAG over the codebase
→ the review is posted as a comment directly on the GitHub PR.

## Architecture

```
GitHub PR opened
      │
      ▼
webhook-service (:8081)
  HMAC-SHA256 verified
      │
      ▼ Kafka: pr.events
      │
      ├──────────────────────────────────────────┐
      ▼                                          ▼
ingestion-service (:8082)              review-service (:8083)
  clone repo (JGit)                     embed PR metadata (Voyage AI)
  chunk source files                    pgvector similarity search
  embed chunks (Voyage AI)              Claude API → review text
  store in pgvector                     publish to reviews.completed
      │                                          │
      ▼                                          ▼ Kafka: reviews.completed
  Postgres + pgvector                            │
  (code_chunks table)                   notification-service (:8084)
                                          post comment to GitHub PR
```

## Tech stack

- **Backend:** Java 21, Spring Boot 3.5, 4 microservices
- **Messaging:** Apache Kafka (event-driven, decoupled services)
- **Data:** PostgreSQL + pgvector (HNSW index, cosine similarity)
- **Schema migrations:** Flyway (versioned globally across services)
- **Repo cloning:** JGit (pure-Java, shallow clones, no shell dependency)
- **Embeddings:** Voyage AI (`voyage-code-2`, 1536-dim, code-specialized)
- **AI review:** Claude API (`claude-haiku-4-5`)
- **GitHub integration:** REST API (PR comment posting via PAT)
- **Testing:** JUnit 5, Testcontainers, Mockito, Awaitility (30 tests total)
- **CI:** GitHub Actions (4 workflows, one per service, CI-first development)
- **Frontend (planned):** React, TypeScript
- **Deployment (planned):** Railway + Vercel, custom domain

## Local development

Requires Docker, Java 21, Gradle, and the following env vars in `~/.zshrc`:

```bash
export VOYAGE_API_KEY="your-voyage-key"
export ANTHROPIC_API_KEY="your-anthropic-key"
export GITHUB_TOKEN_PRPILOT="your-github-pat"   # repo scope, for posting PR comments
```

Start infrastructure:

```bash
docker compose up -d   # Postgres + pgvector, Redis, Kafka
```

Run all services (each in its own terminal, `source ~/.zshrc` first):

```bash
cd services/webhook-service      && ./gradlew bootRun   # :8081
cd services/ingestion-service    && ./gradlew bootRun   # :8082
cd services/review-service       && ./gradlew bootRun   # :8083
cd services/notification-service && ./gradlew bootRun   # :8084
```

## Services

### webhook-service

Entry point for GitHub PR events.

- HMAC-SHA256 signature verification (constant-time comparison, prevents timing attacks)
- Publishes to Kafka `pr.events`, keyed by repo for per-repository ordering
- Idempotent producer (`acks=all`, `enable.idempotence=true`)
- **9 tests:** 6 HMAC unit tests (attack scenarios) + Testcontainers integration tests

```bash
cd services/webhook-service && ./gradlew test
```

### ingestion-service

Kafka consumer — turns a PR event into searchable, embedded code context.

- `ErrorHandlingDeserializer` prevents infinite retry loops on malformed messages
- JGit shallow clone (depth=1), source file filtering, 60-line chunking
- Voyage AI `voyage-code-2` embeddings, batched with exponential backoff retry
- pgvector HNSW index for sub-linear approximate nearest-neighbor search
- Flyway migrations, `ddl-auto: validate` (no auto-DDL anywhere)
- **11 tests:** smoke test + CodeChunker unit tests (boundaries, filtering, error isolation)

```bash
cd services/ingestion-service && ./gradlew test
```

### review-service

RAG-powered AI code reviewer.

- Separate Kafka consumer group — processes every PR event independently
- Embeds PR metadata as a query vector (`input_type: query` vs `document`)
- pgvector cosine similarity search retrieves top-8 most relevant code chunks
- Structured Claude prompt with retrieved context → 8,000+ char review
- Review status tracking: `PENDING` → `COMPLETED` / `FAILED`
- Idempotent: duplicate `deliveryId` events skipped (prevents double-billing)
- Publishes `ReviewCompletedEvent` to `reviews.completed` topic on success
- **7 tests:** 4 prompt-building unit tests + 2 Testcontainers integration tests
  with `@MockitoBean` for external APIs

```bash
cd services/review-service && ./gradlew test
```

### notification-service

Posts the AI review back to GitHub as a PR comment.

- Consumes `reviews.completed` Kafka topic
- GitHub REST API: `POST /repos/{owner}/{repo}/issues/{pr}/comments`
- Prepends `🤖 PRPilot AI Review` header for clear attribution
- No database — pure Kafka consumer + HTTP client, DataSource/JPA/Flyway excluded
- **4 tests:** unit tests + Testcontainers integration test with mocked GitHub client

```bash
cd services/notification-service && ./gradlew test
```

## License

MIT