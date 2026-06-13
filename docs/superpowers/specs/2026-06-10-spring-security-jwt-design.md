# Spring Security 7 + JWT 认证系统 — 设计 Spec

- **日期**: 2026-06-10
- **作者**: (本次 brainstorming 输出)
- **状态**: 待审阅
- **范围**: 给 `EmailServer` 项目补全 Spring Security 7 鉴权链与 JWT 颁发/校验
- **路线**: A — Spring Security 7 OAuth2 Resource Server + Nimbus JOSE/JWT(对称 HS256)
- **存储形态**: 完全无状态(双 JWT 都不进 DB)

---

## 1. 背景与目标

### 1.1 现状

- `EmailServer` 是 Spring Boot 4.0.6 + Java 21 的 JPA 后端,持久化邮件元数据到 PostgreSQL。
- `spring-boot-starter-security` 与 `spring-boot-starter-oauth2-resource-server` 已引入,但**无任何 SecurityConfig、Controller 之外没有 security 类**。
- 已有 4 个实体 `User / Mailbox / Mail / MailRecipient`、3 个 `JpaRepository`(注意:当前 `JpaRepository<Long, X>` 泛型反了,**不在本 spec 修复**)、`MailService` + `MailController`,后者已用 `@AuthenticationPrincipal UserDetails`。
- `User` 实体已有 `emailAddress` 字段(以及一个有 bug 的 `@PrePersist`)。
- 已有 `spring-boot-starter-mail`,`MailService` 走 JavaMailSender → 本机 Postfix(25 端口,localhost 信任)。
- 整个体系里**没有 Dovecot**;SMTP 收件、用户认证都由 Spring 自己接。

### 1.2 目标

1. 给所有非认证端点(目前就是 `MailController` 代表的 `POST /api/mail/send`)加上"必须带 `Authorization: Bearer <access>`"门禁。
2. 提供 `POST /api/auth/login`、`POST /api/auth/refresh`、`GET /api/auth/me` 三个端点,完成登录、双 token 续期、自查。
3. access 走 `Authorization` 头,refresh 走 `HttpOnly + Secure + SameSite=Strict` cookie。
4. 全部配置可由 `application.properties` + 环境变量覆盖,生产部署不需要改代码。

### 1.3 非目标(显式)

- 注册、邮箱验证、忘记密码、登出 endpoint、密码修改 — 后续工单。
- RBAC / 细粒度权限 — 暂只有 `ROLE_USER`。
- Refresh 轮换 / 黑名单 / 服务端登出 — 暂不做。
- RS256 / JWKS / 外部 IdP — 暂不做,等上多实例。
- 限流、2FA、审计日志入库 — 暂不做。

---

## 2. 已确认的决策(产品/架构层)

| # | 决策 | 结论 |
|---|---|---|
| D-1 | JWT 形态 | access + refresh 双 token,均无状态 |
| D-2 | 交付范围 | 核心三件套(login / refresh / me + 受保护业务) |
| D-3 | Token 传输 | access:`Authorization: Bearer`;refresh:HttpOnly cookie |
| D-4 | 角色粒度 | 只 `ROLE_USER` |
| D-5 | 实现路线 | 路线 A(Spring Security 7 OAuth2 RS + Nimbus JOSE,HS256) |
| D-6 | 签名 | HS256(对称 `SecretKey`) |
| D-7 | `sub` claim | 直接是 `username`(`username` 不可改,产品已确认) |
| D-8 | 测试方式 | 不写 JUnit,落 `auth-flow.http` 在 IDEA HTTP Client 手测 |

---

## 3. 架构总览

### 3.1 请求流

```
浏览器 / 客户端
     │
     │ ① POST /api/auth/login  {username, password}
     ▼
┌────────────────────────────────────────────┐
│ SecurityFilterChain (无状态)                │
│  1. 全部放行到 AuthController              │
└────────────┬───────────────────────────────┘
             ▼
   AuthController.login
     • UserDetailsService 加载用户
     • BCryptPasswordEncoder 校验
     • JwtService 发 access (15 min) + refresh (7 d)
     • 响应: { accessToken, tokenType:"Bearer", expiresIn:900 }
     • Set-Cookie: REFRESH_TOKEN=<jwt>; HttpOnly; Secure; SameSite=Strict
                                                  Path=/api/auth/refresh
                                                  Max-Age=604800
             │
             ▼
   ── 后续调用业务接口 ──
             │
             │ ② POST /api/mail/send  Authorization: Bearer <accessToken>
             ▼
   SecurityFilterChain
     • BearerTokenAuthenticationFilter 解析头
     • NimbusJwtDecoder 用 HMAC secret 验签
     • SecurityContextHolder 注入 Authentication(ROLE_USER)
     • 命中 /api/mail/** 放行 → MailController
     │
             │ ③ access 过期
             │    POST /api/auth/refresh   (Cookie: REFRESH_TOKEN=<jwt>)
             ▼
   AuthController.refresh
     • @CookieValue 取 REFRESH_TOKEN
     • JwtService.parse 校验 tokenType=REFRESH, exp, iss
     • 重新加载用户(确认 enabled=true、账号仍存在)
     • 重新发新 access + 覆盖 Set-Cookie(本轮不轮换/不吊销旧 refresh)
     • 响应: { accessToken, expiresIn:900 }
     │
             │ ④ GET /api/auth/me  Authorization: Bearer <accessToken>
             ▼
   SecurityFilterChain → 命中已认证 → AuthController.me → 返 {username, emailAddress}
```

### 3.2 包结构(新增部分)

```
cn.arorms.infra.email
├── config/
│   ├── SecurityConfig.java
│   └── JwtProperties.java
├── security/
│   ├── AppUserPrincipal.java
│   ├── AppUserDetailsService.java
│   ├── JwtService.java
│   └── JwtAuthEntryPoint.java
├── controllers/
│   ├── AuthController.java
│   └── dto/
│       ├── LoginRequest.java
│       ├── TokenResponse.java
│       └── UserInfoResponse.java
└── exception/
    ├── ErrorBody.java
    ├── InvalidRefreshTokenException.java
    └── GlobalExceptionHandler.java
```

---

## 4. 配置

### 4.1 `application.properties` 增量

> **配置前缀**:`application.jwt.*` 与 `application.cors.*`(项目内统一,不与 Spring Boot `application.name` 等内置 key 冲突,因为这些是带二级 namespace 的)。

```properties
# ===== JWT =====
application.jwt.secret=${MAIL_JWT_SECRET:change-me-please-change-me-please-32B}
application.jwt.issuer=arorms-mail
application.jwt.audience=arorms-mail-client
application.jwt.access-ttl=PT15M
application.jwt.refresh-ttl=P7D

# ===== Cookie =====
application.jwt.refresh-cookie-name=REFRESH_TOKEN
application.jwt.refresh-cookie-path=/api/auth/refresh
application.jwt.refresh-cookie-secure=true
application.jwt.refresh-cookie-same-site=Strict
application.jwt.refresh-cookie-max-age=604800

# ===== CORS =====
application.cors.allowed-origins=https://mail.arorms.cn
```

### 4.2 启动时校验

- `JwtProperties` `@PostConstruct` 断言 `secret.getBytes(StandardCharsets.UTF_8).length >= 32`,否则 `IllegalStateException` fail-fast。

### 4.3 测试 profile(不写 JUnit,但 IDEA HTTP Client 用同一份)

`src/test/resources/application-test.properties` 同结构,secret 固定为 `test-secret-32-bytes-long-xxxxxx-yyyyyyy`,`refresh-cookie-secure=false`。

---

## 5. Token 形状

### 5.1 Claim 设计

| 字段 | 来源 | 全称 / 含义 | 标准 / 自定义 |
|---|---|---|---|
| `iss` | JwtService | Issuer = `arorms-mail` | RFC 7519 标准 |
| `aud` | JwtService | Audience = `arorms-mail-client` | RFC 7519 标准 |
| `sub` | JwtService | Subject = `username`(产品确认 username 不可改) | RFC 7519 标准 |
| `iat` | JwtService | Issued At(epoch 秒) | RFC 7519 标准 |
| `exp` | JwtService | Expiration Time(epoch 秒) | RFC 7519 标准 |
| `jti` | JwtService | JWT ID(UUID v4),为未来吊销预留 | RFC 7519 标准 |
| `tokenType` | JwtService | `"ACCESS"` 或 `"REFRESH"` | 自定义(全称) |
| `username` | JwtService | 用户名冗余存,日志/审计用 | 自定义(全称) |
| `emailAddress` | JwtService | 邮箱冗余存,日志/审计用 | 自定义(全称) |

### 5.2 Access Token 形状

```json
{
  "iss": "arorms-mail",
  "aud": "arorms-mail-client",
  "sub": "alice",
  "username": "alice",
  "emailAddress": "alice@arorms.cn",
  "tokenType": "ACCESS",
  "iat": 1718022000,
  "exp": 1718022900,
  "jti": "5e1f7c2a-...-uuid"
}
```

### 5.3 Refresh Token 形状(只差 `tokenType` 和 `exp`)

```json
{
  "iss": "arorms-mail",
  "aud": "arorms-mail-client",
  "sub": "alice",
  "username": "alice",
  "emailAddress": "alice@arorms.cn",
  "tokenType": "REFRESH",
  "iat": 1718022000,
  "exp": 1718626800,
  "jti": "9d4b...-uuid"
}
```

---

## 6. 关键类

### 6.1 `JwtProperties`(`@ConfigurationProperties("app.jwt")`)

字段:`secret / issuer / audience / accessTtl(Duration) / refreshTtl(Duration) / refreshCookieName / refreshCookiePath / refreshCookieSecure / refreshCookieSameSite / refreshCookieMaxAge(int,秒)`。`@PostConstruct` 校验 secret 长度。

### 6.2 `SecurityConfig`

Bean 列表:
- `SecurityFilterChain`:STATELESS、CSRF off、CORS、permit `/api/auth/**`、其它 `authenticated()`、注册 `JwtAuthEntryPoint` + `AccessDeniedHandler`
- `PasswordEncoder` → `BCryptPasswordEncoder`(strength 10)
- `AuthenticationManager`(暴露 `DaoAuthenticationProvider` + `UserDetailsService` + `PasswordEncoder`)
- `JwtDecoder` → `NimbusJwtDecoder.withSecretKey(...).macAlgorithm(MacAlgorithm.HS256).build()`,加 `OAuth2TokenValidator` 链:`JwtTimestampValidator` + 自定义 `claimValidator` 验 `iss == props.issuer` 且 `aud contains props.audience`
- `JwtEncoder` → `NimbusJwtEncoder` 用 `ImmutableSecretKeySpec(secret, "HmacSHA256")`
- `CorsConfigurationSource`(读 `app.cors.allowed-origins`,逗号分隔;`allowCredentials=true`)

### 6.3 `AppUserPrincipal implements UserDetails`

不可变 record 风格(但保留 getter 形式以兼容 Security API):
- `userId: Long`、`username: String`、`emailAddress: String`、`passwordHash: String`、`enabled: boolean`
- `getAuthorities()` → `List.of(new SimpleGrantedAuthority("ROLE_USER"))`
- `isAccountNonExpired/Locked/CredentialsNonExpired` 全部 `true`
- `isEnabled()` → `enabled`

### 6.4 `AppUserDetailsService implements UserDetailsService`

- `loadUserByUsername(String username)`:
  1. `userRepository.findByUsername(username)` 拿 `Optional<User>`
  2. 空 → `throw new UsernameNotFoundException("User not found")`
  3. `user.enabled == false` → `throw new DisabledException("User disabled")`
  4. 包成 `AppUserPrincipal` 返回

### 6.5 `JwtService`

方法(签名只接受业务字段,不依赖任何实体 / 主体类型,便于单元测试和复用):
- `String issueAccess(String username, String emailAddress)`:HS256,claim 见 §5.2,`exp = now + accessTtl`
- `String issueRefresh(String username, String emailAddress)`:HS256,claim 见 §5.3,`exp = now + refreshTtl`
- `Jwt parse(String token)`:Nimbus 解析并验证签名/iss/aud/exp
- `Jwt parseAccess(String token)`:先 `parse`,再断言 `tokenType == "ACCESS"`,否则 `JwtException`
- `Jwt parseRefresh(String token)`:先 `parse`,再断言 `tokenType == "REFRESH"`,否则 `JwtException`

### 6.6 `JwtAuthEntryPoint`

实现 `AuthenticationEntryPoint`:
- 写 `401` + `Content-Type: application/json;charset=UTF-8` + `WWW-Authenticate: Bearer error="invalid_token"`
- Body: `ErrorBody.of("unauthorized", publicMessage(ex), req.getRequestURI())`
- `publicMessage`:`BadCredentialsException` → `"Invalid credentials"`;`InsufficientAuthenticationException` → `"Authentication required"`;其它 → `"Unauthorized"`

### 6.7 `RefreshCookieFactory`

- `ResponseCookie create(String token)`:属性全从 `JwtProperties` 读
- `ResponseCookie clear()`:同名 + `Max-Age=0`(给未来 logout 用,本轮不挂 endpoint)

### 6.8 `AuthController`

| 端点 | 鉴权 | 处理 |
|---|---|---|
| `POST /api/auth/login` | permitAll | 委派 `AuthService.login(req)`,写 Set-Cookie,返 `TokenResponse` |
| `POST /api/auth/refresh` | permitAll | 委派 `AuthService.refresh(cookieValue)`,写 Set-Cookie,返 `TokenResponse` |
| `GET /api/auth/me` | authenticated | 委派 `AuthService.me(principal)`,返 `UserInfoResponse` |

### 6.9 `AuthService`

- `login(LoginRequest req)`:
  1. `authManager.authenticate(new UsernamePasswordAuthenticationToken(req.username(), req.password()))`
  2. 拿 `AppUserPrincipal`(从 `Authentication.getPrincipal()`)
  3. `String access = jwtService.issueAccess(principal.getUsername(), principal.getEmailAddress())`
  4. `String refresh = jwtService.issueRefresh(principal.getUsername(), principal.getEmailAddress())`
  5. 返 `TokenResponse(access, "Bearer", jwtProperties.getAccessTtl().toSeconds())` + 写 cookie
- `refresh(String refreshCookieValue)`:
  1. 空 / null → `throw new InvalidRefreshTokenException("Refresh token missing")`
  2. `Jwt jwt = jwtService.parseRefresh(cookieValue)`
  3. `User user = userRepository.findByUsername(jwt.getSubject()).orElseThrow(() -> new InvalidRefreshTokenException("User not found"))`
  4. `!user.isEnabled()` → `throw new InvalidRefreshTokenException("User disabled")`
  5. 重新发 access + refresh cookie(本轮**不**轮换 / 不吊销旧的)
  6. 返 `TokenResponse`
- `me(AppUserPrincipal p)`:返 `new UserInfoResponse(p.username, p.emailAddress)`

> 备注:第 1 步失败抛 `BadCredentialsException`,由 `GlobalExceptionHandler` 兜成 401。**不**在 service 里手动包成"用户名或密码错"的 if/else,以免响应文案不一致。

### 6.10 `ErrorBody`

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(
        String error,
        String message,
        String path,
        Instant timestamp
) {
    public static ErrorBody of(String error, String message, String path) {
        return new ErrorBody(error, message, path, Instant.now());
    }
}
```

### 6.11 `GlobalExceptionHandler`(`@RestControllerAdvice`)

返回 `ResponseEntity<ErrorBody>`,MVC 阶段抛的所有异常在此汇总。完整映射见 §8。

> **实现约定**:用 `ResponseEntity.badRequest()` / `ResponseEntity.status(401)` / `ResponseEntity.status(403)` / `ResponseEntity.internalServerError()` 这一套静态工厂方法来组装响应,不手工 `ResponseEntity.status(HttpStatus.XXX).build()` 全套写。401 仍需挂 `WWW-Authenticate: Bearer error="invalid_token"` 头。

### 6.12 DTO(record)

- `LoginRequest(String username, String password)`
- `TokenResponse(String accessToken, String tokenType, long expiresIn)`
- `UserInfoResponse(String username, String emailAddress)`

---

## 7. 数据模型改动

### 7.1 `User` 实体

修 `@PrePersist` 字符串模板 bug,改为:

```java
@PrePersist
private void synchronizeEmail() {
    if (this.emailAddress == null || this.emailAddress.isBlank()) {
        this.emailAddress = this.username + "@arorms.cn";
    }
}
```

不强制覆盖,允许外部创建时显式 `setEmailAddress(...)`(以后接外部登录用户时有用)。

### 7.2 其它实体 / Repository

- `Mail / Mailbox / MailRecipient`:不动
- `UserRepository`:新增 `Optional<User> findByUsername(String username)`(`@Query` 派生都行)
- 其它 Repository:**不在本 spec 修复 `JpaRepository<Long, X>` 泛型反了的问题**

### 7.3 DDL 增量

```sql
ALTER TABLE users ADD COLUMN email_address VARCHAR(255) NOT NULL DEFAULT '';
-- 上线后跑一次性 backfill:UPDATE users SET email_address = username || '@arorms.cn' WHERE email_address = '';
-- 之后 ALTER TABLE users ALTER COLUMN email_address DROP DEFAULT;
-- 视情况加 UNIQUE 约束:ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email_address);
```

---

## 8. 端点契约

### 8.1 错误体形状(全局)

```json
{
  "error":   "unauthorized" | "bad_request" | "forbidden" | "internal",
  "message": "human readable, 给前端展示",
  "path":    "/api/auth/login",
  "timestamp": "2026-06-10T12:34:56Z"
}
```

### 8.2 `POST /api/auth/login`

**Request**
```json
{ "username": "alice", "password": "..." }
```

**Response 200**
```json
{
  "accessToken": "eyJ...",
  "tokenType":   "Bearer",
  "expiresIn":   900
}
```

**Set-Cookie**
```
REFRESH_TOKEN=eyJ...; Path=/api/auth/refresh; HttpOnly; Secure; SameSite=Strict; Max-Age=604800
```

**错误**
- 400 — 缺字段 / JSON 坏
- 401 — 用户不存在 / 密码错 / 账号被禁(响应文案统一,防用户名枚举)
- 500 — 其它

> **不**返回 `refreshToken` 字段。前端只能从 cookie 拿,不能 JS 读。

### 8.3 `POST /api/auth/refresh`

**Request**:无 body,只读 `Cookie: REFRESH_TOKEN=...`

**Response 200**
```json
{ "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 900 }
```

**Set-Cookie**:同 §8.2 形状(重新签发新的 refresh 覆盖)

**错误**
- 401 — cookie 缺 / 签名错 / 过期 / `tokenType != "REFRESH"` / 对应用户已不存在 / 账号被禁

**轮换策略(本轮)**:不轮换、不吊销旧 refresh。同一个 refresh 在 7 天内可重复使用,都会得到新的 access + 新的 refresh cookie(Set-Cookie 覆盖)。这是**显式简化**,后续工单 SEC-001。

### 8.4 `GET /api/auth/me`

**Request**:`Authorization: Bearer <access>`

**Response 200**
```json
{ "username": "alice", "emailAddress": "alice@arorms.cn" }
```

**错误**:401(access 缺失/过期/无效)。不返 `userId`(产品暂不要)。

### 8.5 受保护业务接口(例 `POST /api/mail/send`)

行为不变,只强制鉴权。401 / 200 沿用现有响应。

### 8.6 CORS

```
Access-Control-Allow-Origin:      <请求 origin,白名单回显,绝不 *>
Access-Control-Allow-Credentials: true
Access-Control-Allow-Methods:     GET,POST,OPTIONS
Access-Control-Allow-Headers:     Authorization, Content-Type
Access-Control-Max-Age:           3600
```

`OPTIONS` 预检必须 `permitAll()`,否则 `OPTIONS /api/auth/login` 在到达 controller 之前就被拦。

---

## 9. 错误处理总览

| 触发 | 阶段 | 出口 | 状态 |
|---|---|---|---|
| JWT 解析失败 / 过期 / 签名错 / 缺 `Authorization` | Filter | `JwtAuthEntryPoint` | 401 + `WWW-Authenticate: Bearer error="invalid_token"` |
| `BadCredentialsException` / `UsernameNotFoundException` / `DisabledException` / `JwtException` / `InvalidRefreshTokenException` | MVC | `GlobalExceptionHandler` | 401 |
| `MissingServletRequestCookieException`(refresh 缺 cookie) | MVC | `GlobalExceptionHandler` | 401 `"Refresh token missing"` |
| `MethodArgumentNotValidException` / `HttpMessageNotReadableException` | MVC | `GlobalExceptionHandler` | 400 |
| `AccessDeniedException` | MVC | `GlobalExceptionHandler` | 403 |
| 其它 `Exception` | MVC | `GlobalExceptionHandler` | 500 |

`GlobalExceptionHandler` 全部方法返回 `ResponseEntity<ErrorBody>`,带 `Content-Type: application/json;charset=UTF-8`。

---

## 10. 手动测试计划(IDEA HTTP Client)

`src/test/http/auth-flow.http`,14 个 case,见附录 A。

不写 JUnit / Mockito / `@SpringBootTest`,所有验收走这个文件 + 启动本地应用。

---

## 11. 安全 checklist(实施完成时逐条勾)

**Secret / 配置**
- [ ] `MAIL_JWT_SECRET` 走环境变量,生产 32+ 字节随机
- [ ] `.gitignore` 包含 `application-local.properties`、`.env`
- [ ] 仓库 `application.properties` 不含任何真实 secret
- [ ] `JwtProperties` 启动断言 secret 长度

**Cookie**
- [ ] `HttpOnly` + `Secure` + `SameSite=Strict` + `Path=/api/auth/refresh` + `Max-Age=604800`
- [ ] 不写 `Domain`
- [ ] 本地开发 `secure=false` 写在 `application-local.properties`

**JWT**
- [ ] 锁算法 `HS256`
- [ ] 必含 `iss / aud / sub / iat / exp / jti / tokenType / username / emailAddress`
- [ ] 校验 `iss / aud / exp`
- [ ] access 端点拒 `tokenType != "ACCESS"`,refresh 端点拒 `tokenType != "REFRESH"`
- [ ] 日志不打印 token / cookie / Authorization 头

**Filter Chain**
- [ ] `SessionCreationPolicy.STATELESS`
- [ ] `csrf().disable()`
- [ ] `cors()` 配 `allowCredentials=true` + 白名单 origin
- [ ] `/api/auth/**` permit,其它 `authenticated()`
- [ ] 自定义 `JwtAuthEntryPoint` + `AccessDeniedHandler`
- [ ] 401 一定带 `WWW-Authenticate: Bearer error="invalid_token"`

**用户态**
- [ ] `enabled=false` 与"密码错"响应文案一致
- [ ] 密码用 `BCryptPasswordEncoder`(cost 10)
- [ ] `findByUsername` 返 `Optional`,空 → `UsernameNotFoundException`

**HTTP 头**
- [ ] `/api/auth/*` 响应 `Cache-Control: no-store`
- [ ] 全局 `X-Content-Type-Options: nosniff`
- [ ] HSTS 走部署层

**日志**
- [ ] `LOGIN_SUCCESS` / `LOGIN_FAIL` 事件:`username / remoteIp / userAgent / reasonEnum / jti(短前缀)`
- [ ] 邮件异常只记 `messageId` + 掩码后的收件域

---

## 12. 未来工单(backlog)

| 编号 | 主题 | 触发 |
|---|---|---|
| SEC-001 | Refresh 轮换 + 重用检测 | "全部登出" 功能 |
| SEC-002 | 服务端 logout(`revoked_tokens` 表存 jti) | 立即吊销 |
| SEC-003 | 登录限流(按 IP + username 滑动窗口) | 被刷 |
| SEC-004 | 注册 + 邮箱验证 + 忘记密码 | 用户自助开户 |
| SEC-005 | RS256 + JWKS | 多实例 / 外部 IdP |
| SEC-006 | 外部 OAuth2 / OIDC(`oauth2-client` starter 现成) | 第三方登录 |
| SEC-007 | 多设备会话列表 + 远程踢人 | "我登录的设备" |
| SEC-008 | 2FA / TOTP | 二次认证要求 |
| SEC-009 | Spring Authorization Server | 本服务当 IdP |
| SEC-010 | 审计日志入库 | 等保 / 合规 |
| SEC-011 | `@PreAuthorize` 细粒度 | 出现 ADMIN 角色 |
| SEC-012 | `__Host-` cookie 前缀 | 浏览器策略收紧 |

---

## 附录 A — `auth-flow.http`

```http
@host = http://localhost:8080
@contentType = application/json
@badUser = no-such-user
@password = wrong-password

### 1) 登录 - 正确账号
POST {{host}}/api/auth/login
Content-Type: {{contentType}}

{ "username": "alice", "password": "alice-password" }

> {%
    client.global.set("access", response.body.accessToken);
    client.global.set("refreshCookie", response.headers.valueOf("Set-Cookie"));
%}

### 2) 登录 - 错误密码
POST {{host}}/api/auth/login
Content-Type: {{contentType}}

{ "username": "alice", "password": "{{password}}" }

### 3) 登录 - 用户不存在
POST {{host}}/api/auth/login
Content-Type: {{contentType}}

{ "username": "{{badUser}}", "password": "whatever" }

### 4) 登录 - 缺字段
POST {{host}}/api/auth/login
Content-Type: {{contentType}}

{ "username": "alice" }

### 5) /me 带 token
GET {{host}}/api/auth/me
Authorization: Bearer {{access}}

### 6) /me 不带 token
GET {{host}}/api/auth/me

### 7) /me 带 garbage token
GET {{host}}/api/auth/me
Authorization: Bearer not-a-real-jwt

### 8) 受保护接口
POST {{host}}/api/mail/send
Authorization: Bearer {{access}}
Content-Type: {{contentType}}

{ "to": "bob@arorms.cn", "subject": "hi", "content": "<p>hi</p>" }

### 9) 受保护接口无 token
POST {{host}}/api/mail/send
Content-Type: {{contentType}}

{ "to": "bob@arorms.cn", "subject": "hi", "content": "<p>hi</p>" }

### 10) refresh - 正常
POST {{host}}/api/auth/refresh

### 11) refresh - 拿 access 当 refresh
POST {{host}}/api/auth/refresh
Cookie: REFRESH_TOKEN={{access}}

### 12) refresh - 缺 cookie
POST {{host}}/api/auth/refresh

### 13) refresh - 篡改一字符
POST {{host}}/api/auth/refresh
Cookie: REFRESH_TOKEN={{access}}xxx

### 14) CORS 预检
OPTIONS {{host}}/api/auth/login
Origin: http://localhost:5173
Access-Control-Request-Method: POST
Access-Control-Request-Headers: content-type
```

---

## 附录 B — 关键不变量 / 风险点

1. **Refresh 不轮换**:同一个 refresh 在 7 天内可重复使用并获得新 access。任何拿到 refresh cookie 的人 7 天内能自由换 token。这是显式取舍,工单 SEC-001 跟进。
2. **Spring Security 默认 `JwtAuthenticationConverter` 不验 `tokenType`**:所以"拿 refresh 当 access 用"在 Filter 阶段不会被拦——但**业务层不依赖此断言做安全**。需要的话在 `SecurityConfig` 注入 `OAuth2TokenValidator<Jwt>` 验 `tokenType=ACCESS`,本轮先不在 filter 加,放进 `auth-flow.http` case 11 的人工/集成验证。
3. **错误文案统一**:`username 不存在` / `密码错` / `账号被禁` 三者响应体 `message` 字段**完全相同**,只在前端无差别展示。`reason` 仅出现在服务端日志。
4. **本地 cookie `Secure=false`**:开发用 `http://localhost`,临时改 `app.jwt.refresh-cookie-secure=false` 是**必须**的,否则浏览器不发。生产反向。
5. **生产 secret 必走环境变量**:`MAIL_JWT_SECRET` 由部署平台注入;`application.properties` 里的 `:change-me-please-change-me-please-32B` 仅是占位,JVM 启动时 secret < 32 字节直接 fail-fast。
