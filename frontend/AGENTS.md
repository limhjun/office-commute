# AGENTS.md — frontend

Admin SPA for the office-commute backend. Root-level rules (spec-first, error envelope, secrets policy) still apply here.

## Build / Dev
- Package manager: **pnpm** (`pnpm-lock.yaml` is committed — never use npm/yarn or commit their lockfiles).
- `pnpm install` · `pnpm dev` (port 5173; backend must be running on 8080) · `pnpm build` (`tsc --noEmit` + vite) · `pnpm lint` · `pnpm gen:api`

## Stack
- React 18 + TypeScript + Vite, Mantine 7 (`core`/`hooks`/`form`/`dates`/`notifications`) + Tabler Icons, TanStack Query 5, React Router 6, openapi-fetch.
- Path alias `@` → `src/` (see `vite.config.ts` / `tsconfig.json`).

## API contract (spec-first, extended to the frontend)
- Types are generated from `../openapi.yml` via openapi-typescript: `pnpm gen:api` → `src/api/schema.d.ts` (committed). **Never hand-edit `schema.d.ts`**; regenerate after any `openapi.yml` change.
- All HTTP goes through the typed `api` client (`src/api/client.ts`) and `unwrap()` (`src/lib/errors.ts`) inside TanStack Query `queryFn`/`mutationFn`. Don't use raw `fetch`.
- `unwrap()` throws `ApiError`, which maps the backend error envelope: `ErrorResult { code, message }` and `ValidationErrorResult.fieldErrorResults` → `fieldErrors` for Mantine form field errors. Map new domain error codes to user-facing copy where they surface.

## Auth / networking
- Session-cookie auth (`JSESSIONID`), same-origin by design: dev via the Vite proxy (`/api` → `localhost:8080` in `vite.config.ts`), deploy by serving `dist/` behind the same origin as the backend. **No CORS setup, no absolute API base URLs** — keep `baseUrl: ''`. All backend endpoints live under `/api/**`; keep it that way (SPA routes must never collide with API paths).
- Session/role restore on boot via `GET /api/auth/me` (`src/auth/AuthContext.tsx`); role-based route guards in `src/routes/guards.tsx` (manager: teams/employees/overtime; member: my commute/annual leave).
- TanStack Query is configured not to retry 401/403.

## Structure
- `src/hooks/` — one file per resource (`useTeams`, `useEmployees`, `useCommute`, `useAnnualLeave`, `useOvertime`) wrapping TanStack Query.
- `src/pages/` — one page per route; `src/components/AppLayout.tsx` — Mantine AppShell.
- `src/lib/` — `errors` (ApiError/unwrap), `download` (Excel blob + `Content-Disposition` filename), `notify`, `month` utils.
