---
paths:
  - "src/main/java/com/company/officecommute/controller/**/*.java"
  - "src/main/java/com/company/officecommute/auth/**/*.java"
---

# Auth Rules (Controllers)

- Auth is session-based (`JSESSIONID`).
- Login invalidates any existing session, then stores `currentEmployeeId` / `currentRole` on the new one (session-fixation defense).
- Read the current user in controllers via `@RequestAttribute("currentEmployeeId") Long employeeId`.
- Use `@ManagerOnly` for endpoints restricted to `currentRole == MANAGER`.
