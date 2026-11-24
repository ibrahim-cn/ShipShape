# Compose fixtures for Task 3.1

- `fixture-a/compose.yaml`: corrected comparable Compose configuration with services `db`, `api`, and `worker`. All ten required `COMPOSE-001` through `COMPOSE-010` findings are `PASS`; image tags and healthchecks are explicit, users are non-root, privileged mode is disabled, ports use loopback bindings, restart policies are supported, and the database password is a runtime reference.
- `fixture-b/docker-compose.yml`: deliberately risky comparable configuration with the same service names. It demonstrates multiple affected services and mixed statuses in one upload:
  - `db`: `postgres:latest`, root user, hardcoded fake `DB_PASSWORD`, and broadly published PostgreSQL port.
  - `api`: privileged mode, root user, broad Docker API port, Docker socket mount, host root bind mount, `network_mode: host`, and missing healthcheck.
  - `worker`: untagged image, unresolved user and privileged values, host PID/IPC, IPv6 Redis port, disabled healthcheck, missing restart policy, read-only `/etc` mount, variable mount, and unreadable `env_file`.
- The `DB_PASSWORD: hunter2` value is a fake test credential. It is used only to prove that hardcoded-value detection works; the value is never included in explanations, evidence, logs, or API responses.
- Fixture A is the corrected counterpart to Fixture B and is used in both unit tests and real Spring-context multipart integration tests.
- Remaining limitations: the analyzer does not resolve Compose variables, merge override files, inspect `env_file`, read image metadata, verify registry state, resolve host paths, execute healthchecks, or infer public network reachability.
