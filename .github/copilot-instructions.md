## Purpose

This file gives immediate, repo-specific guidance for AI coding agents to be productive in this project (a Vue 3 frontend + Spring Boot backend). Focus on the files and workflows referenced below.

> Note: the original Node/Express backend under `hello/backend/` was retired and deleted on 2026-09-13; that directory was renamed to `hello/data/`. It now only holds shared runtime data (`.env`, `uploads/`, `storage/`) that the Java backend still reads/writes — do NOT delete it.

## Big picture

- **Frontend**: Vue 3 SPA under `src/` (entry: `src/main.js`). Router: `src/router/index.js` (uses dynamic imports). State: `src/store/index.js` (Vuex-based, token/user persisted to `localStorage` or `sessionStorage`). Key views: `src/views/*` (e.g. `MyHistory.vue`, `ChartGenerator.vue`).
- **Backend**: Spring Boot app in `spring-backend/` (entry: `PgApplication.java`, port 3000). Controllers in `controller/`, business logic in `service/`, DB access via MyBatis-Plus (`mapper/` + `resources/mapper/*.xml`), auth via `security/` (stateless JWT, roles loaded from DB). There is no public static hosting of `backend/storage` — PDFs are streamed through authenticated endpoints.
- **Shared data directory**: `data/` (formerly `backend/`) holds `.env` (read at startup via `spring.config.import`), `uploads/` and `storage/`. Java defaults point there (`UPLOADS_DIR/HISTORY_DIR/CHARTS_DIR`, `RELATIVE_BASE=..`).

## Run & debug (developer workflows)

```bash
# frontend
cd hello
npm install
npm run serve     # http://localhost:8080

# backend (requires JDK 17+; scripts auto-select JDK)
cd hello/spring-backend
build.cmd         # mvn clean package (auto JDK selection)
run.cmd           # start jar (builds first if missing), port 3000
mvn-run.cmd       # dev mode: mvn spring-boot:run
verify.cmd        # start + full API regression, prints PASS/FAIL (baseline PASS=27 FAIL=0)
```

- Linting: `cd hello && npm run lint` (frontend lint via Vue CLI).
- Build frontend for production: `cd hello && npm run build`.
- IDE: repo root `.vscode/launch.json` runs `PgApplication` with working directory fixed to `spring-backend/` (required so `../backend/.env` and storage paths resolve).

## Environment & secrets

- Config is auto-imported from `../data/.env` (`spring.config.import: optional:file:../data/.env[.properties]`); env vars override it, then `application.yml` defaults.
- Required: `JWT_SECRET` (startup fails fast if missing). Optional per feature: `SMTP_*` (verification emails), `DEEPSEEK_API_KEY/URL`, `NSCC_API_KEY/URL` (LLM calls), `DB_HOST/DB_PORT/DB_USER/DB_PASSWORD/DB_NAME` (fallback `localhost/3306/root/000/X`).
- Never commit `.env` or paste its real values (JWT secret, SMTP auth code, LLM API keys) into docs, scripts, or commits.

## Project-specific patterns & conventions

- API contract: all JSON responses are unified as `{success, data, message}` (`common/Result.java`, `common/GlobalExceptionHandler.java`). Endpoint paths are unchanged from the retired Express version: `/api/auth`, `/api/verification`, `/api/datasets`, `/api/chat`, `/api/compile`, `/api/history`, `/api/conversations`, `/api/feedback`, `/api/notice`, `/api/admin/{users,notices,log,static}`.
- Adding an endpoint: create entity (`entity/`) + mapper (`mapper/`, complex SQL in `resources/mapper/*.xml`) → service in `service/` (`@Transactional` for multi-step writes) → controller in `controller/` returning `Result.ok(...)`. Throw `BusinessException` for business errors; the global handler maps them to status codes.
- Auth: endpoints requiring login are secured by `SecurityConfig`; `/api/admin/**` requires the ADMIN role (loaded from DB, never trusted from JWT claims). For admin checks outside that prefix use `@PreAuthorize("hasRole('ADMIN')")`.
- Data isolation: every user-scoped query/delete must filter by `user_id` (`LambdaQueryWrapper.eq`), mirroring the previous Express behavior.
- System logging: use `util/SystemLogWriter` (side-effect only, never throws); API call outcomes go to the `api_log` table.
- File storage: dataset files under `data/uploads/`, history JSONs under `data/storage/history/`, compiled PDFs under `data/storage/generated_charts/`. `generation_path` is stored relative to the project root (`data\...` prefix, resolved with `RELATIVE_BASE=..`) — preserve that convention.
- Frontend persistence: `src/store/index.js` writes `user` into `localStorage` or `sessionStorage` depending on presence of a token (`localStorage.getItem('token') ? localStorage : sessionStorage`). Follow this pattern when accessing token/user in components.
- Router lazy-loading: routes use dynamic imports `() => import('../views/SomeView.vue')`. Preserve that pattern to keep bundle size small.

## Integration points & external dependencies

- Email delivery: `JavaMailSender` in `service/VerificationService` — ensure SMTP env vars are set before testing email flows.
- Authentication: JWT issued by `security/JwtTokenProvider` (user 24h / admin 7d). Frontend stores/attaches tokens per existing patterns (check `src/store` and `TheAuth.vue` / auth-related components).
- File upload: Spring `MultipartFile` (≤100MB) — uploads land under `data/uploads` (see `DatasetController`).
- LLM calls: `client/LlmClient` (OpenAI-compatible `/chat/completions` via Spring `RestClient`) for Qwen3.5 (NSCC) and DeepSeek.
- LaTeX compile: `util/LatexCompiler` runs `xelatex -interaction=nonstopmode` with a 30s timeout and always cleans the temp dir.

## How AI agents should modify code here (practical tips)

- When adding API endpoints, follow the controller→service→mapper layering above and keep DTO field names snake_case to match the frontend contract (`@JsonProperty` where needed).
- When changing DB schema, add migration SQL under `hello/migrations/` (see existing files for style; `create_conversations_tables.sql` is the archived DDL source for the two conversation tables).
- When changing auth token shape or expiry, update `security/JwtTokenProvider` and verify frontend `src/store` and components that read `user`/`token`.
- For frontend changes, follow the lazy-route pattern and update `src/router/index.js`. Update `src/store/index.js` mutations when altering `user` shape.

## Examples (quick references)

- Adding an endpoint: `controller/DatasetController` → `service/DatasetService` → `mapper/DataFileMapper`.
- Email send flow: `controller/VerificationController` → `service/VerificationService`.
- Authenticated PDF streaming: `CompileController` `GET /api/compile/{id}/pdf` (ownership check by `user_id`).
- Token persistence: `src/store/index.js` uses `localStorage.getItem('token')` to decide storage location.

## What I (AI agent) should ask you if unclear

- Which environment should be used to test email sending (real SMTP vs. mocked)?
- Any coding style rules beyond the frontend ESLint settings?
