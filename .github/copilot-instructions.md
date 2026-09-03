## Purpose

This file gives immediate, repo-specific guidance for AI coding agents to be productive in this project (a Vue 3 frontend + Node/Express backend). Focus on the files and workflows referenced below.

## Big picture

- **Frontend**: Vue 3 SPA under `src/` (entry: `src/main.js`). Router: `src/router/index.js` (uses dynamic imports). State: `src/store/index.js` (Vuex-based, token/user persisted to `localStorage` or `sessionStorage`). Key views: `src/views/*` (e.g. `MyHistory.vue`, `ChartGenerator.vue`).
- **Backend**: Express server in `backend/` (entry: `backend/app.js`). Routes live in `backend/routes/*.js`, business logic in `backend/services/*.js`, DB access via `backend/db.js` (exports `promisePool`). Static assets served from `backend/storage` at the `/storage` path.

## Run & debug (developer workflows)

- Install dependencies at repo root and backend: run in two terminals.

```bash
# frontend
cd hello
npm install
npm run serve

# backend
cd hello/backend
npm install
npm run dev    # nodemon (recommended for development)
# or: npm run start
```

- Linting: `cd hello/backend && npm run lint` and `cd hello && npm run lint` (frontend lint via Vue CLI).
- Build frontend for production: `cd hello && npm run build`.

## Environment & secrets

- Backend expects SMTP configuration environment variables used by `backend/services/verificationService.js`: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `SMTP_FROM`.
- JWT secret referenced in `backend/routes/auth.js` via `process.env.JWT_SECRET` (falls back to `'your-secret-key'` if missing).
- Database pool is defined in `backend/db.js` with hardcoded credentials; update this file or replace with env-driven config when provisioning a real DB.

## Project-specific patterns & conventions

- Route registration: all API mount points are added in `backend/app.js`. When adding a new route file (e.g. `backend/routes/newFeature.js`) register it there with `app.use('/api/newFeature', require('./routes/newFeature'))`.
- Services: put business logic that may call DB or external services in `backend/services/` (example: `verificationService.js` handles SMTP, code generation and DB persistence for verification codes).
- DB: use `backend/db.js`'s `promisePool` for queries (see `backend/routes/auth.js` usage: `const db = require('../db').promisePool`).
- Static storage: files under `backend/storage/` are served at `/storage`. Generated charts and history JSONs are persisted here — check `storage/generated_charts/` and `storage/history/`.
- Frontend persistence: `src/store/index.js` writes `user` into `localStorage` or `sessionStorage` depending on presence of a token (`localStorage.getItem('token') ? localStorage : sessionStorage`). Follow this pattern when accessing token/user in components.
- Router lazy-loading: routes use dynamic imports `() => import('../views/SomeView.vue')`. Preserve that pattern to keep bundle size small.

## Integration points & external dependencies

- Email delivery: `nodemailer` used by `backend/services/verificationService.js` — ensure SMTP env vars are set before testing email flows.
- Authentication: JWT tokens issued in `backend/routes/auth.js`. Frontend should store/attach tokens per existing patterns (check `src/store` and `TheAuth.vue` / auth-related components).
- File upload: `multer` is in `package.json` — uploads land under `backend/uploads` (see tree). Confirm upload endpoints in `backend/routes/*` if modifying upload behavior.

## How AI agents should modify code here (practical tips)

- When adding API endpoints, update `backend/app.js` and add route file under `backend/routes/`. Put non-HTTP logic into `backend/services/` and use `backend/db.js` for DB queries.
- When changing DB schema/queries, search for raw SQL strings in `backend/routes/` and `backend/services/` — many files use direct SQL (e.g. `SELECT * FROM users WHERE ...`).
- When changing auth token shape or expiry, update `backend/routes/auth.js` and verify frontend `src/store` and components that read `user`/`token`.
- For frontend changes, follow the lazy-route pattern and update `src/router/index.js`. Update `src/store/index.js` mutations when altering `user` shape.

## Examples (quick references)

- Register routes in backend: `backend/app.js` (see `app.use('/api/auth', authRoutes)`).
- Email send flow: `backend/routes/auth.js` calls `verificationService.verifyCode()` which uses `backend/services/verificationService.js`.
- Token persistence: `src/store/index.js` uses `localStorage.getItem('token')` to decide storage location.

## What I (AI agent) should ask you if unclear

- Do you want DB credentials moved to environment variables instead of `backend/db.js` hardcodes?
- Which environment should be used to test email sending (real SMTP vs. mocked)?
- Any coding style rules beyond `backend/package.json`'s `eslint-config-google` and frontend ESLint settings?

---
If you want, I can now open any of the listed files and insert cross-references or expand examples for particular change types (e.g. adding a new route + tests). What would you like me to refine? 
