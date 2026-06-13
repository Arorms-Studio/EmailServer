# Spring Security 7 + JWT 实施计划

- **关联 spec**: `docs/superpowers/specs/2026-06-10-spring-security-jwt-design.md`
- **日期**: 2026-06-10
- **目标**: 把 spec 落成可运行代码,以 IDEA HTTP Client 14 个 case 全绿为完成标志
- **执行模式**: 顺序执行(无并行子任务,单人一气呵成)
- **代码量估计**: ~13 个新文件 + 3 个改动文件 + 1 个 SQL 迁移 + 1 个 .http

---

## 阶段 0 — 准备与基线

| Step | 动作 | 验证 |
|---|---|---|
| 0.1 | 确认 `pom.xml` 已含 `spring-boot-starter-security`、`spring-boot-starter-oauth2-resource-server`、`spring-boot-starter-mail`(已有,无需改) | `mvn dependency:tree` 看到 `nimbus-jose-jwt` |
| 0.2 | 确认 `User.java` 当前已有 `emailAddress` 字段(已有) | 文件 line 18 有 `private String emailAddress` |
| 0.3 | `git status` 干净,新建 feature 分支 `feat/security-jwt`(可选,不做也行) | `git status` |

---

## 阶段 1 — 基础脚手架(配置 + 异常类型)

> 本阶段不接通业务,只把"地基"放下,改动安全可回滚。

### 1.1 新建 `cn.arorms.infra.email.config.JwtProperties`

`@ConfigurationProperties(prefix = "app.jwt")`,字段见 spec §4.1 + §6.1。`@PostConstruct` 校验 secret 长度 ≥ 32 字节,不足直接 `IllegalStateException`。

### 1.2 修改 `application.properties`

按 spec §4.1 增量追加 `app.jwt.*` 与 `app.cors.allowed-origins` 配置。secret 用占位符 `${MAIL_JWT_SECRET:change-me-please-change-me-please-32B}`。

### 1.3 新建 `cn.arorms.infra.email.exception.ErrorBody`

按 spec §6.10 完整实现(record + `@JsonInclude(NON_NULL)` + 静态工厂 `of(...)`)。

### 1.4 新建 `cn.arorms.infra.email.exception.InvalidRefreshTokenException extends RuntimeException`

只一个 `(String message)` 构造。

### 1.5 启用 `@ConfigurationPropertiesScan`

在 `EmailServerApplication` 上加 `@ConfigurationPropertiesScan` 注解(否则 `JwtProperties` 不生效)。

**阶段 1 验证**:
- `mvn compile` 通过
- 临时把 `app.jwt.secret=short` 改短,启动 → `IllegalStateException` fail-fast(确认完改回去)

---

## 阶段 2 — 用户态接入(`User` 修复 + Repository + UserDetailsService)

### 2.1 修复 `User.java` 的 `@PrePersist`

按 spec §7.1,改为 `if (this.emailAddress == null || this.emailAddress.isBlank())` 才回填,且用 `+` 拼接而非字面量 `${...}`。

### 2.2 改 `UserRepository`

- 修正泛型:`extends JpaRepository<User, Long>`(原代码反了 — **本步骤是 spec 显式排除项之外**,但不修这条 `findByUsername` 也写不对,顺手改)
- 新增 `Optional<User> findByUsername(String username)`

> 顺带,`MailRepository` / `MailboxRepository` 的同款泛型反向问题暂**不**修(spec §7.2),避免范围蔓延。

### 2.3 新建 `cn.arorms.infra.email.security.AppUserPrincipal`

按 spec §6.3。不可变,`getAuthorities()` 固定 `ROLE_USER`,`isEnabled()` 委派给字段。提供 `getUserId()` / `getEmailAddress()` getter 给上层取业务字段。

### 2.4 新建 `cn.arorms.infra.email.security.AppUserDetailsService`

按 spec §6.4。`@Service` 标注,Spring Security 会自动当 `UserDetailsService` bean 注入。

### 2.5 改 `MailController`

将 `@AuthenticationPrincipal UserDetails user` 替换为 `@AuthenticationPrincipal AppUserPrincipal user`,然后 `mailService.send(...)` 把 `user` 转成或直接传 `User` —— 注意当前 `MailService.send(User fromUser, ...)` 接的是 `User` 实体,而 `AppUserPrincipal` 不是 `User`。两种处理:

- **方案 A**(推荐):`MailService.send` 入参从 `User` 改成 `AppUserPrincipal`(只读 `userId / username / emailAddress`,不依赖 ORM 状态)
- **方案 B**:在 `MailController` 里 `userRepository.findById(principal.getUserId())` 再传给 service

阶段 2 选 A,顺手让 `MailService` 解耦 ORM 实体(对将来 SMTP-IN 收件投递服务复用 service 也有好处)。

### 2.6 创建 SQL 迁移文件

`docs/db/migrations/V2__add_email_address_to_users.sql`(项目暂未引 Flyway/Liquibase,先手动维护)。内容按 spec §7.3。

**阶段 2 验证**:
- `mvn compile` 通过
- 启动应用,无报错(此时 SecurityFilterChain 还是 Spring Boot 默认,所有端点要 401 — **预期**,下阶段处理)

---

## 阶段 3 — 核心 Security 配置

### 3.1 新建 `cn.arorms.infra.email.security.JwtService`

按 spec §6.5 实现 5 个方法。要点:
- `NimbusJwtEncoder` 用 `ImmutableSecret(secret.getBytes())`
- `NimbusJwtDecoder.withSecretKey(secretKeySpec).macAlgorithm(MacAlgorithm.HS256).build()` 在 `SecurityConfig` 里建 bean,`JwtService` 注入
- claim 写入用 `JwtClaimsSet.Builder` 全部 9 个字段
- `parseAccess` / `parseRefresh` 失败抛 `org.springframework.security.oauth2.jwt.JwtException`

### 3.2 新建 `cn.arorms.infra.email.security.JwtAuthEntryPoint`

按 spec §6.6。`@Component`。`publicMessage(...)` 不暴露根因。

### 3.3 新建 `cn.arorms.infra.email.security.RefreshCookieFactory`

按 spec §6.7。`@Component`,注入 `JwtProperties`。返回 `org.springframework.http.ResponseCookie`(用 `Set-Cookie` header 写)。

### 3.4 新建 `cn.arorms.infra.email.config.SecurityConfig`

按 spec §6.2 全部 bean:`SecurityFilterChain` / `PasswordEncoder` / `AuthenticationManager` / `JwtDecoder` / `JwtEncoder` / `CorsConfigurationSource`。

要点:
- `http.sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))`
- `http.csrf(csrf -> csrf.disable())`
- `http.cors(Customizer.withDefaults())` + `CorsConfigurationSource` bean
- `http.authorizeHttpRequests(...)` permit `/api/auth/**` 与 `OPTIONS /**`,其它 `authenticated()`
- `http.oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()).authenticationEntryPoint(jwtAuthEntryPoint))`
- `JwtDecoder` 加 `OAuth2TokenValidator` 链:`new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audValidator)`,其中 `audValidator` 检查 `aud` 包含 `props.audience`

**阶段 3 验证**:
- 启动应用
- `curl -i http://localhost:8080/api/mail/send -d '{}'` → 401 + `WWW-Authenticate: Bearer error="invalid_token"` 头
- `curl -i http://localhost:8080/api/auth/login` → 405(login 还未实现,但 SecurityFilterChain 已放行)

---

## 阶段 4 — 认证端点(`AuthController` + `AuthService`)

### 4.1 DTO

`controllers/dto/`:
- `LoginRequest(String username, String password)` — 加 `@NotBlank` 两个字段
- `TokenResponse(String accessToken, String tokenType, long expiresIn)`
- `UserInfoResponse(String username, String emailAddress)`

### 4.2 新建 `cn.arorms.infra.email.services.AuthService`

按 spec §6.9 全部 3 方法。
- `login` 抛出 `BadCredentialsException` / `DisabledException` 由全局兜
- `refresh` 自己抛 `InvalidRefreshTokenException`(对应"cookie 缺 / sub 找不到 / enabled=false")或让 `JwtException` 冒泡

### 4.3 新建 `cn.arorms.infra.email.controllers.AuthController`

按 spec §6.8 三端点。要点:
- `login`:`@RequestBody @Valid LoginRequest` + 在响应里 `ResponseEntity.ok().header(SET_COOKIE, cookie.toString()).body(tokenResponse)`
- `refresh`:`@CookieValue(value = "${app.jwt.refresh-cookie-name}", required = false) String cookie`,然后自己判 null 抛 `InvalidRefreshTokenException("Refresh token missing")`
- `me`:`@AuthenticationPrincipal AppUserPrincipal principal` —— 注意:Spring Security 默认 JWT 流程的 principal 是 `org.springframework.security.oauth2.jwt.Jwt`,不是 `AppUserPrincipal`。要么用 `@AuthenticationPrincipal Jwt jwt` 然后 `userRepository.findByUsername(jwt.getSubject())`,要么注册 `JwtAuthenticationConverter` 让它转成 `AppUserPrincipal`。**取后者更简洁,但需要在 `SecurityConfig` 里注入 converter。**

> 决策:`AuthController.me` 用 `@AuthenticationPrincipal Jwt jwt`,直接从 claim 读 `username` 和 `emailAddress`(spec §5 已经把这俩冗余存在 access 里),**不去查 DB**。
> 这条让 `/me` 没 DB IO,延伸了 spec §6.9 中"`me(AppUserPrincipal p)`"的实现细节 — 行为一致,无需更新 spec。

### 4.4 新建 `cn.arorms.infra.email.exception.GlobalExceptionHandler`

按 spec §9 + §6.11 全部映射。返回 `ResponseEntity<ErrorBody>`,401 一定带 `WWW-Authenticate` 头。

**阶段 4 验证**:
- 应用启动
- DB 里手动插入一条用户 `INSERT INTO users(username, password, email_address, enabled, created_at) VALUES ('alice', '$2a$10$<bcrypt-of-alice-password>', 'alice@arorms.cn', true, NOW())`(用在线 BCrypt 工具或 Spring 的 `BCryptPasswordEncoder.encode(...)` 生成)
- `curl POST /api/auth/login` 拿到 access + Set-Cookie

---

## 阶段 5 — 手测验收

### 5.1 写 `src/test/http/auth-flow.http`

完整复制 spec 附录 A。

### 5.2 启动应用,在 IDEA HTTP Client 中按顺序跑 14 个 case,**全部对照预期**:

| Case | 期望 |
|---|---|
| 1 登录正确 | 200 + accessToken + Set-Cookie REFRESH_TOKEN |
| 2 登录错密码 | 401 `unauthorized` + `WWW-Authenticate` 头 |
| 3 登录用户不存在 | 401(响应文案与 case 2 完全一致) |
| 4 登录缺字段 | 400 `bad_request` |
| 5 /me 带正确 token | 200 `{username, emailAddress}` |
| 6 /me 不带 token | 401 |
| 7 /me garbage token | 401 + `WWW-Authenticate` |
| 8 /api/mail/send 带 token | 200(MailSender 走本机 Postfix,失败容忍 — 验证的是鉴权层) |
| 9 /api/mail/send 无 token | 401 |
| 10 refresh 用 cookie | 200 + 新 access + 新 Set-Cookie |
| 11 refresh 拿 access 当 refresh | 401(`tokenType` 校验) |
| 12 refresh 缺 cookie | 401 `Refresh token missing` |
| 13 refresh 篡改 | 401 |
| 14 CORS 预检 | 200 + `Access-Control-Allow-Origin: http://localhost:5173` |

### 5.3 任何 case 不符即回阶段 1~4 排查;不放过响应文案/状态码差异。

---

## 阶段 6 — 安全 checklist 逐条勾

按 spec §11 11 类清单全部 ✅。重点:
- `MAIL_JWT_SECRET` 在本地用 `export MAIL_JWT_SECRET="$(openssl rand -base64 32)"` 测一遍
- 任何启动期/请求期的日志里 grep `eyJ` 确认 token 没被打印(Spring Security DEBUG 关掉)
- 把 secret 改短再启动,确认 fail-fast 生效后改回

---

## 阶段 7 — 收尾

- 所有未提交改动整理到 1~3 个 commit:
  1. `feat(security): add JwtProperties, ErrorBody, exceptions`
  2. `feat(security): add SecurityConfig, JwtService, principals, entry point`
  3. `feat(security): add AuthController + AuthService + global exception handler + http client tests`
- 在 commit message 里链回 spec 路径
- 不开 PR,不 push(用户后续指令决定)

---

## 风险与回退

| 风险 | 缓解 |
|---|---|
| Nimbus / OAuth2 Resource Server API 在 Spring Security 7 与 6 略有差异 | 先在阶段 3 做最小可启动验证,出错查官方 7.x 文档 |
| Postfix 25 端口连不上导致阶段 5 case 8 跑不通 | 临时把 `MailService.send` mock 一下,或专注鉴权层 — 邮件层不是本计划范围 |
| 本地 `Secure=true` cookie 在 `http://localhost` 不发 | 走 `application-local.properties` 临时改 `false`,**不要**改主配置 |
| `JpaRepository` 泛型反掉(其它 repo)导致编译错 | 阶段 2 只修 `UserRepository`,其它 repo 暂不调用,不会被编译路径触达 |

回退策略:每个阶段都是单向追加新文件 + 对原文件的小量 surgical 改动,任何阶段失败可 `git checkout -- <file>` 回到上阶段。

---

## 估时(粗)

| 阶段 | 时长 |
|---|---|
| 0 | 5 min |
| 1 | 20 min |
| 2 | 30 min |
| 3 | 60 min(SecurityConfig 最容易踩坑) |
| 4 | 45 min |
| 5 | 20 min |
| 6 | 10 min |
| 7 | 10 min |
| **合计** | **~3.5 h** |