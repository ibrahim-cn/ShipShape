# Dockerfile analysis fixtures

These fixtures support repeatable static-analysis tests without network access, Docker execution, or shell command execution.

## fixture-a

`fixture-a/Dockerfile` represents a hardened baseline:

- explicit non-latest version tag
- explicit `WORKDIR`
- `COPY` rather than `ADD`
- explicit non-root `USER`
- explicit `HEALTHCHECK CMD`
- explicit `CMD`

`fixture-a/.dockerignore` exists to test that the shared pipeline can report `.dockerignore` availability. Its rules are not parsed by this task.

Expected standalone Dockerfile result: `PASS`.

## fixture-b

`fixture-b/Dockerfile` represents a legacy baseline with explicit warnings:

- `:latest` tag
- broad `COPY . .`
- `chmod -R 777`
- no explicit user, working directory, startup command, or health check

Expected standalone Dockerfile result: `WARNING`.

The analyzer must never return instruction arguments in evidence. Expected evidence format is `KEYWORD [line N]`.
