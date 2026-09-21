# good-news-ai

Starting point for a new application. What is in place is the scaffolding, not a product: sign-in
with a tenant Kennung, the app shell with its sidebar and theming, user administration, company
data with branches and logo, a tenant's own AI access, tenant management by a super-user — and one
empty PrimeNG test page where the first real feature will go.

## Repository layout

| Path                                         | Contents                                                                                  |
| -------------------------------------------- | ----------------------------------------------------------------------------------------- |
| [`frontend/`](frontend/README.md)            | Angular 22 single-page app — pnpm, PrimeNG, Transloco, NgRx Signals, Tailwind             |
| [`backend/`](backend/AGENTS.md)              | Spring Boot 4.1 service — Gradle (Kotlin DSL), Java 25, JPA, Flyway                       |
| [`style-guide/`](style-guide/style-guide.md) | Per-file-type style guides (TypeScript, templates, SCSS, a11y, tests, npm, git, markdown) |
| [`.claude/skills/`](SKILLS.md)               | Task-specific agent skills, indexed in [`SKILLS.md`](SKILLS.md)                           |
| [`scripts/`](scripts/)                       | Repo verification — shared check runner, full-suite verify, Claude Code Stop hook         |

## Prerequisites

- **Node.js 26+** with **Corepack enabled** (`corepack enable`) — pinned in [`.nvmrc`](.nvmrc);
  pnpm is pinned via `packageManager` in [`frontend/package.json`](frontend/package.json)
- **Java 25** (the Gradle wrapper handles Gradle itself)
- **Docker** — provides PostgreSQL via [`backend/compose.yaml`](backend/compose.yaml) in dev and
  via Testcontainers in tests

## Quick start

Frontend:

```bash
cd frontend
pnpm install
pnpm start        # dev server on http://localhost:4200/
```

Backend:

```bash
cd backend
./gradlew bootRun # starts PostgreSQL via Docker Compose automatically
```

The dev profile seeds the tenant `musterfirma` with `admin` / `user` and the super-user `super`,
all with the password `secret`.

Full verification (lint, format, unit tests, builds, backend):

```bash
node scripts/verify.mjs
```

## Working in this repository

Guidance for agents and contributors is layered: the root [`AGENTS.md`](AGENTS.md) holds
monorepo-wide rules, each package has its own `AGENTS.md`, and coding rules live in the
[style guides](style-guide/style-guide.md). Module boundaries in the frontend are enforced by
[Sheriff](https://sheriff.softarc.io) ([`frontend/sheriff.config.ts`](frontend/sheriff.config.ts)).
