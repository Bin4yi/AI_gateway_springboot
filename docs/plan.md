# AgentGate — Spring Boot Microservices Project Plan

Oct 22, 2026 · @Binula

## What we are building

**AgentGate** is a gateway that sits between AI agents and company tools. Every agent action must pass 3 checks: who is the agent, is it allowed, and does the company still have budget.

The goal: finish it before the career fair (early January 2027) and use it to get Java / Spring Boot backend jobs.

**The story for recruiters (one sentence):**

> "I built a secure gateway for AI agents in Spring Boot. 1,000 agents can spend the same budget at the same time, and it never overspends."

**The one hard problem:** many agents from one company use the same budget at the same moment. Without care, two agents both see "100 tokens left" and both spend 100. Now the company has spent 200. This is the same problem as double-booking a concert seat. Our job is to make this impossible and prove it with a load test.

**Why this idea is good for you:**

- It uses your real strength: OAuth 2.0 and token exchange (RFC 8693) from WSO2.
- It shows the classic backend skills recruiters check: concurrency, Saga, Outbox, idempotency, circuit breakers, tracing.
- It is new. Most student projects are shops and food apps.

## The big picture

[embedded content: AgentGate architecture · 5 services, Kafka, audit]

Solid lines are calls. The dashed line means services download the Auth Service's public keys once, so they can check tokens without asking Auth every time. The Budget Service is the star of the project.

## The 5 services

Each service has one job and its own database. No service reads another service's database.

### 1. Gateway Service

**Job:** the front door. All requests come in here.

- **Tech:** Spring Cloud Gateway, Redis rate limiter
- **Does:** checks the JWT, limits requests per agent (for example 20 per second), sends the request to the right service
- **Endpoints:** none of its own. It routes `/api/**` to the other services.

### 2. Auth Service

**Job:** gives tokens to agents.

- **Tech:** Spring Authorization Server (supports token exchange, RFC 8693)
- **Does:** a user logs in and gets a token. The agent swaps the user's token for a **smaller token** with fewer rights. Example: the user can read and write, but the agent token can only read.
- **Endpoints:**
  - `POST /oauth2/token` (normal login and token exchange)
  - `GET /oauth2/jwks` (public keys, so other services can check tokens)

### 3. Policy Service

**Job:** says "allow" or "deny" for one action.

- **Tech:** Spring Boot, Postgres, Caffeine cache
- **Does:** reads rules like "agent `support-bot` can call tool `send_email` only on weekdays and only for its own tenant"
- **Endpoints:**
  - `POST /policies/check` → `{ allowed: true/false, reason }`
  - `POST /policies` and `GET /policies` (admin)

### 4. Budget Service (the star)

**Job:** holds each tenant's token budget. Never lets it go below zero.

- **Tech:** Redis (Lua script for fast atomic reserve), Postgres (the true record)
- **Does:** reserve budget, then later commit or refund it
- **Endpoints:**
  - `POST /budgets/{tenantId}/reserve` (needs an `Idempotency-Key` header)
  - `GET /budgets/{tenantId}`
  - Commit and refund happen from Kafka events, not REST

### 5. Action Orchestrator

**Job:** runs one agent action from start to end. This is where the Saga lives.

- **Tech:** Spring Boot, Postgres, Kafka, Resilience4j
- **Does:** calls Policy, calls Budget, runs the tool (a fake tool for the demo), then sends a Kafka event: done or failed
- **Endpoints:**
  - `POST /actions` → starts an action, returns `actionId`
  - `GET /actions/{actionId}` → status

**Extra (small, not a full service):** an **Audit consumer** inside the Orchestrator project, or a tiny 6th app. It reads all Kafka events and saves them as a log nobody can change.

## How services talk

Two ways. **Need an answer now → REST. Telling others "this happened" → Kafka.**

| Who calls | Who answers | How | Why |
| --- | --- | --- | --- |
| Gateway | Orchestrator | REST | The agent waits for an `actionId` |
| Orchestrator | Policy | REST (with circuit breaker) | Must know allow/deny before going on |
| Orchestrator | Budget (reserve) | REST (with circuit breaker) | Must know the budget is held before running the tool |
| Orchestrator | Budget (commit / refund) | Kafka | Nobody needs to wait for this |
| Orchestrator | Audit | Kafka | Just a log |
| All services | Auth | REST, once at start | Download public keys to check JWTs |

Spring tools: `RestClient` (or `OpenFeign`) for REST. `KafkaTemplate` to send, `@KafkaListener` to receive.

### Kafka topics and events

| Topic | Event | Sent by | Read by | Main fields |
| --- | --- | --- | --- | --- |
| `action-events` | `ActionStarted` | Orchestrator | Audit | actionId, tenantId, agentId, tool |
| `action-events` | `ActionCompleted` | Orchestrator | Budget, Audit | actionId, reservationId, realCost |
| `action-events` | `ActionFailed` | Orchestrator | Budget, Audit | actionId, reservationId, reason |
| `budget-events` | `BudgetCommitted` | Budget | Audit | reservationId, amount |
| `budget-events` | `BudgetRefunded` | Budget | Audit | reservationId, amount |
| `action-events.DLT` | any event that failed 3 times | Kafka retry | You (manual check) | the original event |

**Key rule:** use `tenantId` as the Kafka message key. Then all events for one tenant go to the same partition and stay in order.

**Event format:** JSON with an `eventId` (UUID), `eventType`, `occurredAt`, and `payload`. The `eventId` lets readers skip duplicates.

## Database tables

One Postgres server, but **one separate database per service**. Use Flyway for all table changes.

### Auth DB

| Table | Columns |
| --- | --- |
| `users` | id, username, password\_hash, tenant\_id |
| `oauth2_registered_client` | Spring Authorization Server makes this (agents are clients) |
| `oauth2_authorization` | Spring Authorization Server makes this (issued tokens) |

### Policy DB

| Table | Columns |
| --- | --- |
| `policies` | id, tenant\_id, agent\_id, tool, effect (ALLOW/DENY), conditions (JSONB), created\_at |

### Budget DB

| Table | Columns |
| --- | --- |
| `budgets` | tenant\_id (PK), total\_limit, used, reserved, **version** (for optimistic locking) |
| `reservations` | id, tenant\_id, action\_id, estimated\_amount, actual\_cost, status (HELD / COMMITTED / REFUNDED / EXPIRED), expires\_at |
| `processed_events` | event\_id (PK), processed\_at |
| `idempotency_keys` | key (PK), response\_json, created\_at |

### Orchestrator DB

| Table | Columns |
| --- | --- |
| `actions` | id, tenant\_id, agent\_id, tool, input (JSONB), status, reservation\_id, created\_at, updated\_at |
| `outbox` | id, aggregate\_id, event\_type, payload (JSONB), created\_at, sent\_at (null = not sent yet) |
| `audit_log` | event\_id (PK), tenant\_id, event\_type, payload, received\_at, prev\_hash, curr\_hash (insert only, hash chain) |

### Redis keys (Budget Service)

| Key | What it holds |
| --- | --- |
| `budget:{tenantId}` | a hash: limit, used, reserved |
| `idem:{key}` | the saved answer for a repeated request (expires in 24 hours) |
| `rate:{agentId}` | Gateway rate-limit counter |

**Redis vs Postgres:** Redis is the fast gate. Postgres is the truth.

1. The Lua script reserves in Redis. Losers are rejected at once and never touch Postgres.
2. Each winner writes a `reservations` row (`HELD`) to Postgres **before** the API returns 200. If that write fails, undo the Redis reserve.
3. If Redis restarts, rebuild it from Postgres: `reserved` = sum of `HELD` rows, `used` = sum of committed costs.
4. Turn on Redis AOF (`appendfsync everysec`) so restarts lose almost nothing.

Why: copying Redis to Postgres on a timer can lose the last few seconds if Redis crashes. Then the system overspends.

## Main flow: one agent action

[embedded content: One agent action · the Saga with 3 checks]

The money is never lost: it is either committed, refunded, or freed by the cleanup job.

**In detail (happy path):**

1. The agent sends `POST /actions` with its token and an `Idempotency-Key`.
2. The Gateway checks the token and the rate limit, then sends the request to the Orchestrator.
3. The Orchestrator saves the action as `STARTED`.
4. The Orchestrator asks the Policy Service: allowed? If no → status `DENIED`, stop.
5. The Orchestrator asks the Budget Service to reserve, for example, 50 tokens. Redis runs the Lua script: if `limit - used - reserved >= 50`, then `reserved += 50`. All in one step. If not enough → status `NO_BUDGET`, stop.
6. The Orchestrator runs the tool (fake tool for the demo).
7. In ONE database transaction: set status `COMPLETED` and write `ActionCompleted` into the `outbox` table.
8. The outbox poller sends the event to Kafka.
9. The Budget Service reads it: `reserved -= 50`, `used += realCost`. It saves the `eventId` so a repeat does nothing.

**If the tool fails at step 6:** status `FAILED`, an `ActionFailed` event goes out the same way, and the Budget Service refunds the 50 tokens.

## System design concepts (and where they live)

These are the things interviewers ask about. Each one has a clear home in the code, so you can open the file and show it.

| Concept | Simple meaning | Where in AgentGate | Spring tool |
| --- | --- | --- | --- |
| **Atomic update** | Read and change in one step, so no one can jump in between | Budget reserve | Redis Lua script |
| **Optimistic locking** | Save only if nobody changed the row since you read it | `budgets` table in Postgres | `@Version` in JPA |
| **Idempotency** | Same request sent twice = done only once | Budget `reserve`, all Kafka readers | Custom `@Idempotent` annotation (Spring AOP) + Redis |
| **Saga** | A long job across services. If one step fails, undo the earlier steps | Orchestrator: reserve → run → commit or refund | Plain Java state machine + Kafka |
| **Transactional Outbox** | Save the data and the event in ONE DB transaction. Send the event later. No lost events | Orchestrator `outbox` table | `@Scheduled` poller + `KafkaTemplate` |
| **Circuit breaker** | If a service keeps failing, stop calling it for a short time | Orchestrator → Policy, Orchestrator → Budget | Resilience4j |
| **Retry with backoff** | Try again, waiting longer each time | Same calls + Kafka readers | Resilience4j, Spring Kafka `DefaultErrorHandler` |
| **Dead letter topic** | Messages that keep failing go to a side box | `action-events.DLT` | Spring Kafka `DeadLetterPublishingRecoverer` |
| **Rate limiting** | Max N requests per second per agent | Gateway | `RequestRateLimiter` + Redis |
| **Token exchange (zero trust)** | Agent gets a smaller token, not the user's full one | Auth Service | Spring Authorization Server |
| **Caching** | Keep hot data in memory | Policy rules | Caffeine + `@Cacheable` |
| **Timeout / expiry** | Free held budget if nobody finishes | `reservations.expires_at` | `@Scheduled` cleanup job |
| **Distributed tracing** | Follow one request across all services | Every service | Micrometer Tracing + OpenTelemetry → Tempo |
| **Virtual threads** | Cheap threads, handle many waiting calls | All services | `spring.threads.virtual.enabled=true` |

**Not in this project on purpose:** two-phase commit (2PC). Be ready to say why: it locks many databases at once and gets stuck if one service is down. Saga is the normal answer.

## Design upgrades (from review)

All of these are worth adding. Each one is small code but a strong interview answer.

| Upgrade | Problem it fixes | How | Week |
| --- | --- | --- | --- |
| Reserve estimate, commit actual cost | Explains WHY we reserve first: the real cost is known only after the tool runs | Reserve 50. Tool reports 32. Then `reserved -= 50`, `used += 32`. If actual is more than the estimate, charge only up to the estimate and log it | 6 |
| Postgres ledger for reservations | A Redis crash could lose reservations → overspend | See "Redis vs Postgres" in Database tables | 3 |
| `@Idempotent` with `SET NX` | Two same requests 2 ms apart both run | `SET idem:{key} IN_PROGRESS NX EX 30`. Duplicate while running → 409. When done → save `COMPLETED` + response. If the method throws → delete the key | 4 |
| Outbox with `FOR UPDATE SKIP LOCKED` | 3 copies of the Orchestrator would send each event 3 times | Each copy locks its own rows. Others skip them | 5 |
| Audit hash chain | Someone edits the audit table directly | `curr_hash = SHA-256(prev_hash + event)`. Add `GET /audit/verify` | 6 |
| `act` claim in the agent token | Can't tell which agent acted for which user. One agent could pretend to be another | Token has `sub` = user, `act.sub` = agent, narrow `scope`. Policy checks `act.sub` = `agentId` | 2 |
| Fake tool modes | Hard to demo failures | Input field `simulate`: `SLOW_3S`, `HTTP_500`, `VARIABLE_COST` | 6 |

**Two honest notes:**

- The hash chain finds edits, but a person with full DB access can rebuild the whole chain. Fix: write the latest hash to the logs every hour. Say this limit in interviews. It shows you understand it.
- Keep one chain **per tenant**. Kafka keeps each tenant's events in order (key = `tenantId`), so building the chain is safe.

## Tech stack

| Area | Use | Note |
| --- | --- | --- |
| Language | Java 21 | Virtual threads, records, pattern matching |
| Framework | Spring Boot 3.x | Check the latest stable version when you start |
| Build | Maven, multi-module | One parent `pom.xml`, one module per service |
| Gateway | Spring Cloud Gateway |  |
| Security | Spring Authorization Server, Spring Security OAuth2 Resource Server |  |
| Database | PostgreSQL 16 + Flyway |  |
| Cache / locks | Redis 7 |  |
| Messages | Kafka (KRaft mode, no ZooKeeper) |  |
| Resilience | Resilience4j |  |
| Observability | Micrometer, OpenTelemetry, Prometheus, Grafana, Tempo (traces), Loki (logs) |  |
| Tests | JUnit 5, Testcontainers, k6 |  |
| Run | Docker Compose |  |
| CI | GitHub Actions |  |
| Docs | Mermaid diagrams in README, OpenAPI (springdoc) |  |

### What we skip on purpose

| Skip | Why |
| --- | --- |
| RabbitMQ | Kafka already does messaging |
| gRPC, GraphQL | More work, little extra value for this story |
| Debezium | A simple polling outbox is enough and easier to explain |
| Kubernetes | Add only at the very end if there is time. Docker Compose is enough for recruiters |
| Eureka / service discovery | Docker Compose names (like `http://budget-service:8080`) are enough |
| A real LLM | Use a fake tool that waits 200 ms and returns a cost. The project is about the backend, not the AI |
| A frontend | Grafana dashboards + Swagger UI are your "frontend" |

## Repo folder structure

One GitHub repo (a "monorepo"). Easier for recruiters: one link, one `docker compose up`.

```
agentgate/
├── README.md                 ← diagram, demo GIF, load-test result, how to run
├── docker-compose.yml        ← starts everything
├── pom.xml                   ← parent Maven file
├── common/                   ← shared code: event classes, @Idempotent annotation
├── gateway-service/
├── auth-service/
├── policy-service/
├── budget-service/
│   └── src/main/resources/
│       ├── db/migration/     ← Flyway SQL files
│       └── scripts/reserve.lua
├── orchestrator-service/
├── infra/
│   ├── grafana/dashboards/   ← saved dashboards (JSON)
│   ├── prometheus.yml
│   └── otel-collector.yml
├── load-tests/
│   └── budget-race.js        ← the k6 script
├── docs/
│   ├── architecture.md
│   └── decisions/            ← short notes: "why Kafka", "why no 2PC"
└── .github/workflows/ci.yml
```

## Code structure inside each service

Every service uses the same classic enterprise layers: **Controller → Service → Repository (DAO)**, with DTOs at the edge. Recruiters know this shape, so they can read any of your 5 services fast.

[embedded content: Layers inside one service · request goes left to right]

The answer goes back the same way: Service returns an entity, the Mapper turns it into a Response DTO, the Controller sends it as JSON.

**MVC here** = Spring MVC for REST. Model = the DTO, View = the JSON, Controller = the controller. No HTML pages.

### Package layout (Budget Service example)

```
com.agentgate.budget
├── controller/   ReservationController
├── dto/          ReserveRequest, ReserveResponse   (Java records)
├── mapper/       ReservationMapper                 (MapStruct)
├── service/      ReservationService (interface), ReservationServiceImpl
├── entity/       Budget, Reservation               (JPA @Entity)
├── repository/   BudgetRepository, ReservationRepository  (Spring Data JPA)
├── dao/          BudgetRedisDao                    (runs the Lua script)
├── messaging/    ActionEventListener
├── exception/    GlobalExceptionHandler, BudgetExceededException
└── config/       RedisConfig, KafkaConfig
```

**DAO vs Repository:** in Spring, a Repository IS a DAO. Use `repository/` for JPA (Spring writes the SQL). Use `dao/` for data code you write by hand, like the Redis Lua script.

### 6 layer rules

1. Controller → Service → Repository. Never skip a layer.
2. Entities never leave the service layer. Controllers only see DTOs.
3. DTOs are Java records, checked with `@Valid` (`@NotNull`, `@Positive`).
4. Constructor injection only. No `@Autowired` on fields.
5. Throw your own exceptions. One `@RestControllerAdvice` turns them into `ProblemDetail` JSON (RFC 9457).
6. `@Transactional` only on service methods.

### Design patterns you can point to

| Pattern | Where in AgentGate | Simple meaning |
| --- | --- | --- |
| Layered / MVC | Every service | Each layer has one job |
| DTO | `dto/` | Carry data in and out, hide the entity |
| Repository / DAO | `repository/`, `dao/` | Hide database code behind an interface |
| Mapper | `mapper/` | Convert DTO ↔ Entity in one place |
| Facade | `ActionService` in Orchestrator | One method hides calls to Policy, Budget and the tool |
| Strategy | Policy rule checkers (time, tenant, tool); fake tool modes | Swap behavior by choosing a class |
| State | Action status: `STARTED → COMPLETED / FAILED` | Allowed moves between statuses |
| Template Method | Base Kafka listener: check `eventId` → `handle()` → save `eventId` | Fixed steps, one step filled in by subclasses |
| Proxy | `@Idempotent`, `@Transactional`, `@Cacheable` (Spring AOP) | Extra code runs around your method |
| Observer | Kafka listeners | Many readers react to one event |
| Builder | JWT claims, test data | Build a complex object step by step |
| Circuit Breaker | Resilience4j calls | Stop calling a broken service for a while |

Use these from Week 1. Changing the structure later is slow.

## Testing and proof

The tests are the proof. Recruiters trust a screenshot of a passing test more than words.

| Test type | Tool | What it proves |
| --- | --- | --- |
| Unit tests | JUnit 5, Mockito | Business rules work (budget math, policy rules) |
| Integration tests | Testcontainers (real Postgres, Redis, Kafka in Docker) | The service works with real tools, not fakes |
| Concurrency test | JUnit + `ExecutorService` with 200 threads | 200 parallel reserves on a budget of 100 → exactly 100 succeed |
| Saga test | Testcontainers + Awaitility | If the tool fails, the budget is refunded |
| Duplicate test | JUnit | Same `Idempotency-Key` twice → budget changes once |
| Load test | k6 | 1,000 virtual agents, 1 tenant, 60 seconds → **overspend = 0** |
| Chaos test (manual) | `docker stop policy-service` | Circuit breaker opens, system says "try later", nothing breaks |

### The main load test

1. Give tenant `acme` a budget of 10,000 tokens.
2. Start 1,000 virtual agents in k6. Each one tries to spend 50 tokens, many times.
3. At the end, check: `used + reserved ≤ 10,000`. Also check that the number of successful actions × 50 = used.
4. Take a screenshot of the Grafana dashboard: requests per second, p99 latency, budget left. Put it in the README.

### CI (GitHub Actions)

On every push: build → unit tests → Testcontainers tests. Show the green badge at the top of the README.

## 10-week build plan

You learn Spring while you build. About 4–5 hours a day on the project, and keep 1–2 hours a day for DSA and interview prep.

[embedded content: Build plan · 10 weeks in 5 phases]

### Week 0 (Oct 22–25): get ready

- [ ] Install Java 21, IntelliJ, Docker Desktop (give it 8 GB RAM)
- [ ] Create the GitHub repo, parent `pom.xml`, empty modules
- [ ] `docker-compose.yml` with Postgres, Redis, Kafka. Check all 3 start

### Week 1 (Oct 26 – Nov 1): Policy Service

- [ ] Learn: Spring Boot basics, REST controllers, Spring Data JPA, Flyway (Spring Start Here helps here)
- [ ] Build: `policies` table, `POST /policies`, `GET /policies`, `POST /policies/check`
- [ ] Add Caffeine cache with `@Cacheable`
- [ ] First unit tests

### Week 2 (Nov 2 – Nov 8): Auth + Gateway

- [ ] Learn: Spring Security, OAuth2 Resource Server (Spring Security in Action helps here)
- [ ] Build: Auth Service with Spring Authorization Server, login + token exchange
- [ ] Build: Gateway routes, JWT check
- [ ] Policy Service accepts only valid JWTs

### Week 3 (Nov 9 – Nov 15): Budget Service

- [ ] Learn: Spring Data Redis, Lua scripts, `@Version`
- [ ] Build: `reserve.lua`, `POST /budgets/{tenantId}/reserve`, `budgets` + `reservations` tables
- [ ] **Race test:** 200 threads, budget 100 → exactly 100 succeed

### Week 4 (Nov 16 – Nov 22): Idempotency + Orchestrator v1

- [ ] Learn: Spring AOP, Testcontainers
- [ ] Build: `@Idempotent` annotation backed by Redis
- [ ] Build: Orchestrator `POST /actions`, calls Policy + Budget with `RestClient`, fake tool
- [ ] Testcontainers tests for Budget

### Week 5 (Nov 23 – Nov 29): Outbox + Kafka

- [ ] Learn: Spring Kafka (`KafkaTemplate`, `@KafkaListener`)
- [ ] Build: `outbox` table + `@Scheduled` poller
- [ ] Build: Budget listener for commit, `processed_events` table

### Week 6 (Nov 30 – Dec 6): Failure path

- [ ] Fake tool fails 10% of the time → `ActionFailed` → refund
- [ ] Cleanup job for reservations older than 5 minutes
- [ ] Dead letter topic + Audit consumer
- [ ] Saga test with Awaitility

### Week 7 (Dec 7 – Dec 13): Resilience

- [ ] Learn: Resilience4j
- [ ] Circuit breaker + retry + timeout on Orchestrator → Policy and → Budget
- [ ] Gateway rate limiter with Redis
- [ ] Chaos test: stop Policy, check the system stays safe

### Week 8 (Dec 14 – Dec 20): Observability

- [ ] Micrometer + OpenTelemetry in all services
- [ ] Prometheus, Grafana, Tempo, Loki in Docker Compose
- [ ] One Grafana dashboard: requests, p99 latency, budget left, circuit state
- [ ] Turn on virtual threads

### Week 9 (Dec 21 – Dec 27): Load test + CI

- [ ] k6 script: 1,000 agents, one tenant
- [ ] Fix what breaks. Record overspend = 0 and p99 latency
- [ ] GitHub Actions: build + all tests

### Week 10 (Dec 28 – Jan 3): Show it

- [ ] README (see checklist below), Mermaid diagram, demo GIF
- [ ] 3 short decision notes in `docs/decisions/`
- [ ] Practice the 4 interview answers out loud
- [ ] Spare days for anything that slipped

## README and interview

A recruiter gives your repo about 60 seconds. The README must work in that time.

### README checklist (top to bottom)

- [ ] Project name + one-sentence story + CI badge
- [ ] Architecture diagram (Mermaid)
- [ ] A short GIF: start an action, see the trace in Grafana
- [ ] Load-test result: screenshot + "1,000 agents, 0 overspend, p99 = X ms"
- [ ] "Run it" in 3 lines: `git clone`, `docker compose up`, open `localhost:3000`
- [ ] Table: system design concept → which file to look at
- [ ] "Design decisions" links (why Kafka, why Saga, why no 2PC)
- [ ] "What I would do next" (Kubernetes, multi-region, real LLM)

### What to say in interviews

Practice these 4 answers out loud. Short and clear is best.

1. **"Tell me about your project."** → "It's a gateway for AI agents. It checks who the agent is, if it's allowed, and if there's budget. The hard part was stopping overspend when 1,000 agents spend at once."
2. **"How did you stop overspend?"** → "A Redis Lua script checks and reserves in one step. Nobody can jump in between. I proved it with a k6 test: 1,000 agents, zero overspend."
3. **"What if the tool fails after you took the budget?"** → "That's my Saga. The Orchestrator sends ActionFailed. The Budget Service reads it and refunds. If nobody finishes, a cleanup job frees it after 5 minutes."
4. **"What if Kafka is down when you save?"** → "I use the Outbox. I save the action and the event in one DB transaction. A poller sends it later. So the event is never lost."

**Tip:** write these answers on a card. Say them slowly. A short answer you say clearly is better than a long one.

## Rules and risks

### 5 rules

1. **Each week must end with something that runs.** No week of "setup only".
2. **Don't add new tools.** If an idea is not in this doc, write it in "What I would do next" and move on.
3. **Keep it separate from your FYP.** No Entailment Gap or thesis code here.
4. **Write the code yourself.** Use AI to explain and review, not to write whole services. In interviews you must explain every line.
5. **Commit every day** with clear messages. Recruiters look at the commit history.

### Risks

| Risk | What to do |
| --- | --- |
| Spring Boot is still new to you, so week 1–2 is slow | It's fine. Weeks 1–2 are planned as "learn while building". |
| Spring Authorization Server token exchange is hard to set up | Give it max 4 days. If stuck, do a simple custom `/token/exchange` endpoint and note it in the README. |
| Kafka + Testcontainers + Docker eat your laptop's RAM | Run only the services you need. Give Docker 8 GB. Use one Kafka broker. |
| Falling behind | Cut in this order: Loki logs → Policy cache → Gateway rate limit. Never cut the Budget race test or the Saga. |
| Too much time on the project, no time for DSA / interview prep | Keep 1–2 hours a day for DSA and system design questions, separate from this project. |

## How to work with Claude

AI writes the boring parts, you write the brain. Use **Claude Code** in the repo to build and review. Use **Claude chat** to learn ideas and keep this doc.

| Who writes | What | Why |
| --- | --- | --- |
| **You, by hand** | Lua reserve script, Budget logic, Saga flow, `@Idempotent` aspect, outbox poller, Kafka listeners, token exchange config, race test | Interviewers ask about these. You must explain every line |
| **You start, AI reviews** | Controllers, entities, services, other tests | You learn Spring, AI catches mistakes |
| **AI writes, you read** | `docker-compose.yml`, `pom.xml`, Flyway SQL, DTO records, mappers, Grafana / Prometheus config, GitHub Actions, k6 draft | Boring setup, little learning value |

**Test:** "Can I explain this code in an interview without looking?" If no, delete it and write it yourself.

### Daily loop (about 4–5 hours)

1. **Learn (30–60 min):** docs or book for today's topic. Ask chat: "Explain X with a tiny example, not my project code."
2. **Write it yourself (2–3 hours):** follow this week's checklist.
3. **Review (30 min):** in Claude Code: "Review this. Don't change code. List bugs and better ways."
4. **Boilerplate (30 min):** let Claude Code write the "AI writes" parts.
5. **Note (10 min):** 3 lines: what I built, what I learned, one interview answer.

Stuck for more than 30 minutes? Ask for a **hint**, not the answer.

The `CLAUDE.md` file in the repo root holds the rules Claude Code follows.
