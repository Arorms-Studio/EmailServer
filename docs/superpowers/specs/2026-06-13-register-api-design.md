# Design: Register API + DTO package relocation

**Date:** 2026-06-13
**Status:** Approved (brainstorming)
**Author:** Claude

## Goal

Add a public user-registration endpoint to the EmailServer (which currently
has `login`, `refresh`, `me`), and as a cleanup move the DTO package out
of `controllers/` into a top-level `dtos/` package.

## Non-goals (explicitly out of scope)

- Email verification flow
- Password complexity rules beyond a minimum length
- Registration rate-limiting / anti-abuse
- Role / permission model
- Forgot-password / change-password
- Account lockout / failed-login throttling
- Automated JUnit / Testcontainers tests (project has no test suite today)

## Scope decision

Single design doc. Both changes are tightly related: the new endpoint's
DTO `RegisterRequest` lives in the new package, so the move and the
addition share one PR-sized unit of work.

---

## Functional requirements

### Register endpoint

- `POST /api/auth/register`
- **Public** (no authentication required). `/api/auth/**` is already
  on the `permitAll` list in `SecurityConfig`, so no SecurityConfig
  change is required.
- **Request body** (`application/json`):
  ```json
  { "username": "alice", "password": "secret123" }
  ```
  - `username`: 1–64 characters. No format restriction. Uniqueness
    enforced by the existing DB-level `@Column(unique=true, length=64)`
    on `User.username`.
  - `password`: 8–72 characters (8 minimum, 72 is the BCrypt input
    limit). No complexity rules.
  - `emailAddress` is **not** accepted in the request — the system
    derives it from the username as `{username}@arorms.cn` via the
    existing `@PrePersist` hook on `User`.
- **Success response** (`200 OK`):
  - Body: `TokenResponse` (`accessToken`, `tokenType`, `expiresIn`)
    — same shape as the existing `/api/auth/login` response.
  - Header: `Set-Cookie: REFRESH_TOKEN=...; HttpOnly; Secure;
    SameSite=Strict; Path=/api/auth/refresh; Max-Age=604800`.
  - Semantics: the user is auto-logged-in on successful registration.
- **Error responses**:

  | Condition | HTTP | `error` code | Message |
  |---|---|---|---|
  | `username` null / blank / > 64 chars | 400 | `bad_request` | `Username must be 1-64 characters` |
  | `password` null / blank / < 8 / > 72 chars | 400 | `bad_request` | `Password must be 8-72 characters` |
  | `username` already exists | 409 | `username_taken` | `Username already taken: <username>` |
  | Concurrent duplicate insert (race) | 409 | `username_taken` | same as above |
  | Malformed JSON | 400 | `bad_request` | `Malformed JSON` (existing handler) |
  | Anything else | 500 | `internal` | `Internal server error` (existing handler) |

### DTO package relocation

- Move all three existing DTO files from
  `cn.arorms.infra.email.controllers.dto` to
  `cn.arorms.infra.email.dtos`.
- Update every `import` in the codebase that references the old
  package.
- No behavior change. No new DTOs except `RegisterRequest`.

---

## Component design

### Files added (1)

```
src/main/java/cn/arorms/infra/email/dtos/RegisterRequest.java
```

```java
package cn.arorms.infra.email.dtos;

public record RegisterRequest(String username, String password) {
}
```

(Plus `DuplicateUsernameException` — see "exception handlers".)

### Files moved (3 → 3, same content, package line changed)

```
src/main/java/cn/arorms/infra/email/controllers/dto/LoginRequest.java
src/main/java/cn/arorms/infra/email/controllers/dto/TokenResponse.java
src/main/java/cn/arorms/infra/email/controllers/dto/UserInfoResponse.java
```

↓

```
src/main/java/cn/arorms/infra/email/dtos/LoginRequest.java
src/main/java/cn/arorms/infra/email/dtos/TokenResponse.java
src/main/java/cn/arorms/infra/email/dtos/UserInfoResponse.java
```

Each file's first line changes from
`package cn.arorms.infra.email.controllers.dto;` to
`package cn.arorms.infra.email.dtos;`. No other lines change.

### Files modified (3)

1. `services/AuthService.java`
   - Remove imports: `cn.arorms.infra.email.controllers.dto.LoginRequest`,
     `.TokenResponse`, `.UserInfoResponse`.
   - Add imports: `cn.arorms.infra.email.dtos.LoginRequest`,
     `.TokenResponse`, `.UserInfoResponse`, `.RegisterRequest`;
     `cn.arorms.infra.email.entities.User`;
     `cn.arorms.infra.email.exception.DuplicateUsernameException`;
     `org.springframework.dao.DataIntegrityViolationException`;
     `org.springframework.security.crypto.password.PasswordEncoder`.
   - Add field: `private final PasswordEncoder passwordEncoder;`.
   - Constructor: add `PasswordEncoder passwordEncoder` parameter and
     assign to the new field.
   - Visibility change: `private AuthLoginResult mintTokens(...)` →
     `AuthLoginResult mintTokens(...)` (package-private). This lets the
     new `register()` method reuse it within the same class while
     keeping it hidden from controllers and other services.
   - Add new method:
     ```java
     public AuthLoginResult register(RegisterRequest req) {
         if (req == null
                 || !StringUtils.hasText(req.username())
                 || req.username().length() > 64) {
             throw new IllegalArgumentException("Username must be 1-64 characters");
         }
         if (!StringUtils.hasText(req.password())
                 || req.password().length() < 8
                 || req.password().length() > 72) {
             throw new IllegalArgumentException("Password must be 8-72 characters");
         }
         String username = req.username();
         if (userRepository.findByUsername(username).isPresent()) {
             throw new DuplicateUsernameException(username);
         }
         User user = new User();
         user.setUsername(username);
         user.setPassword(passwordEncoder.encode(req.password()));
         try {
             user = userRepository.save(user);
         } catch (DataIntegrityViolationException e) {
             throw new DuplicateUsernameException(username);
         }
         return mintTokens(user.getUsername(), user.getEmailAddress());
     }
     ```

2. `controllers/AuthController.java`
   - Remove imports: `cn.arorms.infra.email.controllers.dto.*` (3 lines).
   - Add imports: `cn.arorms.infra.email.dtos.LoginRequest`,
     `.TokenResponse`, `.UserInfoResponse`, `.RegisterRequest`.
   - Add new method (placed before `login()` so endpoint order reads
     `register → login → refresh → me`):
     ```java
     @PostMapping("/register")
     public ResponseEntity<TokenResponse> register(@RequestBody RegisterRequest req) {
         AuthLoginResult result = authService.register(req);
         return ResponseEntity.ok()
                 .header(HttpHeaders.SET_COOKIE, result.refreshCookie().toString())
                 .body(result.tokenResponse());
     }
     ```

3. `exception/GlobalExceptionHandler.java`
   - Add import: `org.springframework.http.HttpStatus`.
   - Add two new handlers (placed near existing handlers):
     ```java
     @ExceptionHandler(IllegalArgumentException.class)
     public ResponseEntity<ErrorBody> badRequest(IllegalArgumentException ex, HttpServletRequest req) {
         return ResponseEntity.badRequest()
                 .body(ErrorBody.of("bad_request", ex.getMessage(), req.getRequestURI()));
     }

     @ExceptionHandler(DuplicateUsernameException.class)
     public ResponseEntity<ErrorBody> conflict(DuplicateUsernameException ex, HttpServletRequest req) {
         return ResponseEntity.status(HttpStatus.CONFLICT)
                 .body(ErrorBody.of("username_taken", ex.getMessage(), req.getRequestURI()));
     }
     ```

### New exception type (1)

`src/main/java/cn/arorms/infra/email/exception/DuplicateUsernameException.java`:

```java
package cn.arorms.infra.email.exception;

public class DuplicateUsernameException extends RuntimeException {
    public DuplicateUsernameException(String username) {
        super("Username already taken: " + username);
    }
}
```

### Files NOT modified (explicitly listed for the implementer)

| File | Why unchanged |
|---|---|
| `config/SecurityConfig.java` | `/api/auth/**` already in `permitAll`; new `/register` is covered |
| `entities/User.java` | Existing `@PrePersist` already derives `emailAddress` |
| `security/JwtService.java` | Unchanged |
| `security/RefreshCookieFactory.java` | Unchanged |
| `security/AppUserDetailsService.java` | Unchanged |
| `repositories/UserRepository.java` | `save()` inherited from `JpaRepository` is sufficient |
| `config/CorsConfig.java` / `CorsProperties.java` / `JwtProperties.java` | Unchanged |
| `pom.xml` | Not adding `spring-boot-starter-validation` — manual checks match existing style |

---

## Data flow

```
Client                                  Server
─────                                   ──────
POST /api/auth/register
{ "username": "alice",
  "password": "secret123" }
                                        │
                                        ▼
                            AuthController.register(req)
                                        │
                                        ▼
                            AuthService.register(req)
                                        │
                          ┌─────────────┴─────────────┐
                          ▼                           ▼
              validate length / non-blank    userRepository
              (IllegalArgumentException      .findByUsername(...)
                if invalid → 400)                  │
                          │              ┌─────────┘
                          │              ▼ (if present)
                          │   DuplicateUsernameException → 409
                          │
                          ▼ (validation passed, username free)
                     new User()
                       .setUsername(username)
                       .setPassword(passwordEncoder.encode(plain))
                                        │
                                        ▼
                            userRepository.save(user)
                                        │  (JPA @PrePersist)
                                        │  ├─ emailAddress = username + "@arorms.cn"
                                        │  ├─ enabled = true
                                        │  └─ createdAt = Instant.now()
                                        ▼
                            mintTokens(username, emailAddress)
                                        │
                                        ├─ jwtService.issueAccess  → access JWT
                                        ├─ jwtService.issueRefresh → refresh JWT
                                        ├─ TokenResponse.bearer(access, ttlSeconds)
                                        └─ cookieFactory.create(refresh)
                                        │
                                        ▼
                            ResponseEntity.ok()
                              .header(SET_COOKIE, refreshCookie)
                              .body(tokenResponse)
                                        │
                                        ▼
HTTP/1.1 200 OK
Content-Type: application/json
Set-Cookie: REFRESH_TOKEN=...; HttpOnly; Secure; SameSite=Strict;
            Path=/api/auth/refresh; Max-Age=604800

{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

### Concurrency note

`findByUsername` then `save` is not atomic. If two requests race for
the same username, the DB-level `@Column(unique=true, length=64)`
constraint on `User.username` causes the second `save` to throw
`DataIntegrityViolationException`. The service catches that and re-throws
as `DuplicateUsernameException`, so both the single-threaded and the
racing path converge on the same `409 username_taken` response.

---

## Dependency injection changes

| Bean | Previously injected into | Now also injected into | Notes |
|---|---|---|---|
| `PasswordEncoder` (BCrypt) | `SecurityConfig.authenticationManager(...)` | `AuthService` constructor | Already a Spring bean; no new bean definition needed |
| `UserRepository` | `AuthService` | `AuthService` (unchanged) | — |
| `JwtService` | `AuthService` | `AuthService` (unchanged) | — |
| `RefreshCookieFactory` | `AuthService` | `AuthService` (unchanged) | — |
| `JwtProperties` | `AuthService` | `AuthService` (unchanged) | — |
| `AuthenticationManager` | `AuthService` | `AuthService` (unchanged) | — |

No new beans. No removed beans. No circular dependencies.

---

## Test strategy

The project has no automated test suite (`src/test/java/` is empty, only
`src/test/curl/` shell scripts exist). Per YAGNI and consistency with
project style, this change does not introduce JUnit. Validation is via
curl scripts and database inspection.

### Manual curl acceptance (post-implementation)

| # | Scenario | Expected |
|---|---|---|
| 1 | Register a fresh user | `200` + `TokenResponse` body + `Set-Cookie: REFRESH_TOKEN=...` |
| 2 | Re-register the same username | `409` + `{"error":"username_taken", ...}` |
| 3 | Missing `username` in body | `400` + `Username must be 1-64 characters` |
| 4 | `username` 65+ chars | `400` + `Username must be 1-64 characters` |
| 5 | `password` 7 chars or shorter | `400` + `Password must be 8-72 characters` |
| 6 | DB inspection: `select username, email_address, enabled from users;` | row with the new username, `email_address = username@arorms.cn`, `enabled = t` |
| 7 | Use the access token from #1 to call `GET /api/auth/me` | `200` + `{"username":"alice","emailAddress":"alice@arorms.cn"}` |
| 8 | Use the refresh cookie from #1 to call `POST /api/auth/refresh` | `200` + new `TokenResponse` + new `Set-Cookie` |
| 9 | Regression: `POST /api/auth/login` with the new credentials | behaves exactly as before (DTO relocation did not break login) |
| 10 | `grep -rn "controllers.dto" src/` | empty (no stragglers left in old package) |

A new shell script `src/test/curl/register.sh` (style to match existing
curl scripts in that directory) will cover cases 1, 2, 7, 8.

---

## Migration / impact

- Compile-time impact: any file still importing
  `cn.arorms.infra.email.controllers.dto.*` after the move will fail to
  compile. This is a built-in safety net.
- Runtime impact: none. The relocation is a no-op at runtime.
- Rollback: revert the commit. No DB migration, no schema change.
- Files touched (count): 1 new DTO + 1 new exception + 3 moves + 3
  modifications = 8 files, plus one new curl script (optional).

---

## Decisions log

- **Auto-login on register (vs. require explicit login).** Chosen for
  better UX and one fewer round-trip. Approved during brainstorming.
- **Manual validation, no `spring-boot-starter-validation`.** Matches
  existing `AuthService.login` style (`StringUtils.hasText`). Avoids
  adding a dependency for what amounts to two length checks.
- **No password complexity rules.** Approved during brainstorming as
  "loosest option" (min 8 chars, no format). 72-char cap is the BCrypt
  input ceiling, not a product rule.
- **Concurrency: catch `DataIntegrityViolationException` → 409.**
  Both pre-check and race path produce the same client-visible error
  code.
- **Visibility change `private mintTokens` → package-private.** Keeps
  it inaccessible from controllers and other services, but reachable
  from the new `register()` method in the same class. (If a stricter
  reviewer prefers, an alternative is to extract `mintTokens` to a
  separate `TokenIssuer` helper, but that's an unnecessary layer for
  one method.)
- **DTO package: `dtos` (plural, not `dto`).** Per user request.

---

## Open questions

None at design time. The implementation plan (next step) is expected to
be straightforward.
