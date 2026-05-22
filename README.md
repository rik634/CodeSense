<div align="center">

# 🧠 CodeSense

### AI-Powered Codebase Assistant

**Upload any Java project. Ask questions in plain English. Get instant, context-aware answers.**

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0-brightgreen?logo=spring)](https://spring.io/projects/spring-ai)
[![OpenAI](https://img.shields.io/badge/OpenAI-GPT--4o--mini-412991?logo=openai)](https://openai.com/)
[![pgvector](https://img.shields.io/badge/pgvector-PostgreSQL-336791?logo=postgresql)](https://github.com/pgvector/pgvector)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis)](https://redis.io/)

</div>

---

## ✨ What is CodeSense?

CodeSense turns any Java codebase into a conversational knowledge base. Drop in a ZIP of your source code and start asking questions — no manual documentation, no grep, no context-switching.

| What you can do | How |
|---|---|
| 💬 Ask questions about your code | RAG-powered Q&A with GPT-4o-mini |
| 🔍 Find relevant code semantically | Vector similarity search via pgvector |
| 🔄 Have multi-turn conversations | Session-aware memory across questions |
| 📋 Get structured code reviews | AI-generated review with actionable feedback |
| ⚡ Stream answers in real time | Server-Sent Events (SSE) |
| 🆓 Run fully offline | Ollama + llama3.2 (no API key needed) |

---

## 🏗️ Architecture

```
Browser / curl
      │
      ▼
┌─────────────────────────────────────────────┐
│         Spring Boot REST API  :8080          │
│                                             │
│  POST /api/ingest/upload   → embed → store  │
│  GET  /api/chat/ask        → RAG Q&A        │
│  GET  /api/chat/stream     → SSE streaming  │
│  POST /api/chat/conversation → multi-turn   │
│  GET  /api/search          → semantic search│
│  POST /api/review          → code review    │
└────────┬──────────┬──────────┬──────────────┘
         │          │          │
    ┌────▼───┐  ┌───▼──┐  ┌───▼──────┐
    │pgvector│  │Redis │  │OpenAI API│
    │(vectors│  │(cache│  │gpt-4o-mini
    │+ HNSW) │  │1h TTL│  │embed-3-sm│
    └────────┘  └──────┘  └──────────┘
```

**Key design decisions:**
- **RAG pipeline** — code is chunked, embedded, and stored in pgvector; queries retrieve the top-K relevant chunks before calling the LLM
- **Redis caching** — identical queries return cached responses instantly (1 hr TTL), cutting OpenAI costs
- **Resilience4j** — rate limiter + circuit breaker protect against API overload
- **Dual LLM support** — swap between OpenAI (prod) and Ollama (local/free) via Spring profiles

---

## 🚀 Quick Start

### Option A — Local (free, no API key)

```bash
# 1. Start Postgres + Redis + Ollama
docker-compose up -d

# 2. Run with local profile (uses llama3.2 + nomic-embed-text)
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun

# 3. Open the UI
open http://localhost:8080
```

> ⏳ First run pulls Ollama models (~4 GB). Subsequent starts are instant.

### Option B — Production (OpenAI)

```bash
cp .env.example .env
# Add your OPENAI_API_KEY to .env

docker-compose up -d
./gradlew bootRun
```

---

## 📖 API Usage

### Ingest a project
```bash
curl -X POST http://localhost:8080/api/ingest/upload \
     -F "projectName=my-app" \
     -F "file=@my-app-src.zip"
```

### Ask a question
```bash
curl "http://localhost:8080/api/chat/ask?project=my-app&q=What+does+UserService+do"
```

### Stream an answer (SSE)
```bash
curl "http://localhost:8080/api/chat/stream?project=my-app&q=Explain+the+auth+flow"
```

### Multi-turn conversation
```bash
# Turn 1
curl -X POST http://localhost:8080/api/chat/conversation \
     -H "Content-Type: application/json" \
     -d '{"sessionId":"abc-123","project":"my-app","message":"How does auth work?"}'

# Turn 2 — remembers previous context
curl -X POST http://localhost:8080/api/chat/conversation \
     -H "Content-Type: application/json" \
     -d '{"sessionId":"abc-123","project":"my-app","message":"Now refactor that to use Optional"}'
```

### Semantic code search
```bash
curl "http://localhost:8080/api/search?project=my-app&q=exception+handling+patterns&topK=5"
```

### Code review
```bash
curl -X POST http://localhost:8080/api/review \
     -H "Content-Type: application/json" \
     -d '{"code":"public User find(String email) { return repo.findByEmail(email); }"}'
```

> 📦 A full Postman collection is included: `CodeSense.postman_collection.json`

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3, Spring AI 1.0 |
| LLM (prod) | OpenAI `gpt-4o-mini` |
| LLM (local) | Ollama `llama3.2` |
| Embeddings (prod) | `text-embedding-3-small` (1536 dims) |
| Embeddings (local) | `nomic-embed-text` (768 dims) |
| Vector store | PostgreSQL + pgvector (HNSW index) |
| Cache | Redis 7 (1 hr TTL, LRU eviction) |
| Resilience | Resilience4j — rate limiter + circuit breaker |
| Metrics | Micrometer + Prometheus |
| Tests | JUnit 5 + Testcontainers (pgvector + Ollama) |
| Build | Gradle 9 |
| Containerization | Docker + Docker Compose |

---

## 🧪 Running Tests

```bash
# Full test suite (requires Docker for Testcontainers)
./gradlew test

# Specific integration tests
./gradlew test --tests "RagPipelineIntegrationTest,MultiTurnChatIntegrationTest"
```

> Tests spin up real pgvector and Ollama containers via Testcontainers — no mocks, no surprises.

---

## 📁 Project Structure

```
src/main/java/com/codesense/
├── config/          # AI model, vector store, and cache configuration
├── controller/      # REST endpoints (chat, ingest, search, review)
├── model/           # Domain models (CodeChunk, SearchResult, CodeReview…)
├── service/         # Core logic (RAG, ingestion, multi-turn, search, review)
└── CodesenseApplication.java
```

---

<div align="center">

Built with ❤️ using Spring AI · pgvector · OpenAI · Redis

</div>
