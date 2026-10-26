-- One Postgres server, one database per service.
-- No service reads another service's database.
-- Tables are created later by each service's Flyway migrations, not here.
CREATE DATABASE auth_db;
CREATE DATABASE policy_db;
CREATE DATABASE budget_db;
CREATE DATABASE orchestrator_db;
