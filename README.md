# PRPilot

AI-powered code review platform. Listens for GitHub pull request events,
analyzes the diff using RAG over the codebase, and posts review comments
back to the PR — built as a Kafka-backed microservices system.

[![webhook-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/webhook-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/webhook-service-ci.yml)
[![ingestion-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/ingestion-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/ingestion-service-ci.yml)
[![review-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/review-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/review-service-ci.yml)

## Status

🚧 Actively in development. Not yet deployed.

| Service | Status |
|---|---|
| `webhook-service` | ✅ Built & tested — receives GitHub PR webhooks, HMAC-SHA256 verified, publishes to Kafka |
| `ingestion-service` | ✅ Built & tested — clones repos, chunks code, generates embeddings, persists to pgvector |
| `review-service` | ✅ Built & tested — RAG retrieval + Claude-powered review, persisted with status tracking |
| `notification-service` | ⏳ In progress — posts review comments back to GitHub PR |
| `api-gateway` | ⏳ Planned |
| `frontend` | ⏳ Planned — React dashboard |

**Core AI pipeline fully working end-to-end**, verified against a real GitHub
repo: webhook triggers ingestion (real embeddings via Voyage AI), pgvector
similarity search retrieves relevant code context, Claude generates a structured
8,000+ character code review stored in Postgres.

## Architecture

```
GitHub PR event
      │
      ▼
webhook-service ──► Kafka (pr.events) ──► ingestion-service
                         │                      │
                         │          clone → chunk → embed (Voyage AI)
                         │                      │
                         │               Postgres + pgvector
                         │                      │
                         └──────────► review-service
                                            │
                              embed query → similarity search
                                            │
                                     Claude API (RAG review)
                                            │
                                    reviews table (Postgres)
                                            │
                                  notification-service ──► GitHub PR comment
                                       (in progress)
```

## Tech stack

- **Backend:** Java 21, Spring Boot 3.5
- **Messaging:** Apache Kafka
- **Data:** PostgreSQL + pgvector (HNSW index, cosine similarity), Redis
- **Schema migrations:** Flyway (versioned globally across services)
- **Repo cloning:** JGit (pure-Java, shallow clones)
- **Embeddings:** Voyage AI (`voyage-code-2`, 1536-dim, code-specialized)
- **AI review:** Claude API (`claude-haiku-4-5`, model cascading planned)
- **Testing:** JUnit 5, Testcontainers, Mockito, Awaitility
- **Frontend (planned):** React, TypeScript
- **Infra:** Docker Compose (local), GitHub Actions (CI)

## Local development

Requires Docker, Java 21, Gradle, and the following env vars in `~/.zshrc`:

```bash
export VOYAGE_API_KEY="your-voyage-key"
export ANTHROPIC_API_KEY="your-anthropic-key"
```

Start infrastructure:

```bash
docker compose up -d
```

Run services (each in its own terminal, always `source ~/.zshrc` first):

```bash
cd services/webhook-service && ./gradlew bootRun    # :8081
cd services/ingestion-service && ./gradlew bootRun  # :8082
cd services/review-service && ./gradlew bootRun     # :8083
```

## webhook-service

Entry point for GitHub PR events.

- Validates incoming webhooks via HMAC-SHA256 signature verification
  (constant-time comparison prevents timing attacks)
- Publishes to Kafka topic `pr.events`, keyed by repo for ordering guarantees
- Idempotent producer (`acks=all`, `enable.idempotence=true`)
- **9 tests:** HMAC unit tests (6 attack scenarios) + Testcontainers
  integration tests (full HTTP-to-Kafka pipeline)

```bash
cd services/webhook-service
./gradlew test
./gradlew bootRun   # :8081
```

## ingestion-service

Kafka consumer that turns a PR event into searchable, embedded code context.

- Consumes `pr.events` with `ErrorHandlingDeserializer` (prevents infinite
  retry loop on malformed messages — a real production failure mode)
- Shallow-clones repos with JGit (depth=1), filters source files only
- Chunks source files into 60-line segments (line-based baseline strategy;
  AST-aware chunking is a planned v2 improvement)
- Persists chunks via Spring Data JPA + Flyway (`ddl-auto: validate`)
- Generates 1536-dim embeddings via Voyage AI `voyage-code-2` in batches,
  with exponential backoff retry on rate limits
- Stores vectors in pgvector with HNSW index for sub-linear similarity search
- **11 tests:** smoke test + CodeChunker unit tests covering boundary math,
  extension/directory filtering, unreadable file isolation

```bash
cd services/ingestion-service
./gradlew test
./gradlew bootRun   # :8082
```

## review-service

RAG-powered AI code reviewer using Claude.

- Separate Kafka consumer group from ingestion — both services process
  every PR event independently
- Embeds PR metadata as a **query** vector (`input_type: query` vs `document`
  — Voyage optimizes retrieval differently for each)
- pgvector cosine similarity search retrieves top-8 most relevant chunks
- Sends retrieved context + PR metadata to Claude with a structured prompt
- Persists reviews with status tracking: `PENDING` → `COMPLETED` / `FAILED`
- Idempotent: duplicate `deliveryId` events skipped to prevent double-billing
- Multi-service Flyway coordination: `ignore-migration-patterns: "*:Missing"`
  lets review-service coexist with V1/V2 migrations owned by ingestion-service
- **7 tests:** 4 prompt-building unit tests + 2 Testcontainers integration
  tests with `@MockitoBean` for external APIs (no real API credits in CI)

```bash
cd services/review-service
./gradlew test
./gradlew bootRun   # :8083
```

## License

MITa