# AgentGate — I am learning Spring Boot

## About the project
AgentGate is a gateway for AI agents: 5 Spring Boot services
(Gateway, Auth, Policy, Budget, Orchestrator) with Postgres, Redis and Kafka.
The full plan is in docs/plan.md. Follow it. Do not add new tools.

## Rules for Claude
- Use simple English. English is my second language.
- In these folders, do NOT write code. Give hints, explain, and review only:
  budget-service/, orchestrator-service/, common/src/**/idempotency/,
  auth-service/src/**/config/
- You MAY write: docker-compose, pom.xml, Flyway SQL, DTO records,
  mappers, infra/ config, CI, README.
- When I ask for review: list problems first. Don't fix unless I say "fix it".
- When I am stuck: give a hint first, not the full answer.
- Follow the layer rules: Controller → Service → Repository. DTOs are records.
  Entities never leave the service layer. Constructor injection only.
