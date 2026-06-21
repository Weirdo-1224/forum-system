> 执行规则：所有任务初始保持未勾选。只有完成任务描述中的产物与验收，并实际运行所在阶段的验证命令且结果成功后，才可将对应任务标记为完成。

## 分支约定

- 每个 Phase 从 `main` 切出独立分支，命名格式为 `phase/<N>-<short-name>`，例如 `phase/2-infrastructure`、`phase/3-auth`。
- 在分支上完成该 Phase 的全部任务并勾选后，运行并通过该 Phase 列出的验证命令。
- 验证通过后，通过 Merge Request/Pull Request 合并回 `main`；合并前必须确认无未解决的冲突且 `main` 上的回归测试仍通过。
- 已合并的 Phase 分支可保留作为历史记录，也可在合并后删除。
- `main` 分支始终保持上一个已完成 Phase 的稳定状态，不允许直接推送未完成或验证失败的 Phase 代码。

## 1. Phase 1：Spring Boot 项目骨架

- [x] 1.1 对齐 Java 17、Spring Boot 3.5.x 和 Maven Wrapper 构建基线，移除 Spring Data JPA 并声明 MyBatis-Plus、PostgreSQL/Flyway、Security/JWT、Validation、OpenAPI、Actuator、Testcontainers 与测试依赖。（范围：`pom.xml`、`mvnw`、`mvnw.cmd`；验收：Maven 依赖解析成功且依赖树中不含 `spring-boot-starter-data-jpa`）
- [x] 1.2 建立 `common`、`auth`、`user`、`post`、`comment`、`reaction`、`moderation` 一级包及模块内 `controller/service/repository/entity/dto` 结构。（范围：`src/main/java/org/example/forum_system/**`；验收：包结构可编译且不存在根包级业务 Controller、Service、Repository、Entity）
- [x] 1.3 建立本地、测试和生产配置入口以及环境变量占位，不写入真实数据库密码或 JWT 密钥。（范围：`src/main/resources/application*.yaml`、`.gitignore`；验收：配置绑定测试通过且仓库不包含真实秘密）
- [x] 1.4 添加模块依赖架构测试，禁止 Controller 访问 Repository、跨模块访问内部 Repository 以及 Entity 作为 API DTO。（范围：`src/test/java/**/architecture/**`；验收：合法结构通过，测试规则能够识别示例违规依赖）
- [x] 1.5 添加 Spring Boot 上下文启动测试，验证主应用和基础配置可加载。（范围：`src/test/java/**/ForumSystemApplicationTests.java`；验收：上下文测试在无业务实现时通过）
- [x] 1.6 运行 Phase 1 验证命令并记录构建与测试结果。（范围：项目构建与 `target/` 测试报告；验收：以下命令全部退出码为 0）

**Phase 1 验证命令：**

```powershell
.\mvnw.cmd -q clean test
.\mvnw.cmd -q dependency:tree
```

## 2. Phase 2：PostgreSQL、Flyway 和公共基础设施

- [ ] 2.1 定义 Docker Compose PostgreSQL 服务、数据卷、健康检查和环境变量覆盖方式。（范围：`compose.yaml`、`.env.example`；验收：Compose 配置解析成功且 PostgreSQL 健康检查可用）
- [ ] 2.2 配置 PostgreSQL 数据源、Flyway 和 MyBatis-Plus 的 UUID、UTC、分页与软删除基础行为，禁止 ORM 自动建表。（范围：`src/main/resources/application*.yaml`、`common/config/**`；验收：应用能连接 PostgreSQL 且启动时由 Flyway 接管迁移）
- [ ] 2.3 创建 Flyway 基线迁移并约定后续版本命名、UTC 时间和 UUID 扩展策略。（范围：`src/main/resources/db/migration/V1__baseline.sql`；验收：空 PostgreSQL 可成功执行迁移，重复启动不产生 schema 漂移）
- [ ] 2.4 建立统一 ProblemDetail、稳定错误码、字段校验错误、traceId 和日志脱敏基础。（范围：`common/error/**`、`common/web/**`；验收：参数错误和未处理异常返回约定结构且不暴露堆栈或秘密）
- [ ] 2.5 建立统一分页请求、最大 100 条限制、稳定排序和排序字段白名单组件。（范围：`common/pagination/**`；验收：默认分页、越界分页和非法排序单元测试通过）
- [ ] 2.6 建立共享 PostgreSQL Testcontainers 测试支持和动态数据源配置。（范围：`src/test/java/**/support/**`、`src/test/resources/**`；验收：测试可启动独立 PostgreSQL 并执行 Flyway）
- [ ] 2.7 添加 Flyway 与数据库基础集成测试，覆盖空库迁移、重复迁移和 PostgreSQL 方言约束。（范围：`src/test/java/**/migration/**`；验收：不依赖 H2 的迁移测试通过）
- [ ] 2.8 添加公共异常、分页和配置单元测试。（范围：`src/test/java/**/common/**`；验收：400/500 ProblemDetail、分页上限、排序白名单和秘密脱敏测试通过）
- [ ] 2.9 运行 Phase 2 验证命令并记录数据库、迁移与公共测试结果。（范围：Compose 环境与 `target/` 测试报告；验收：以下命令全部退出码为 0）

**Phase 2 验证命令：**

```powershell
docker compose config
docker compose up -d postgres
docker compose ps
.\mvnw.cmd -q test
```

## 3. Phase 3：用户注册、登录和 Token

- [ ] 3.1 创建 users 与 refresh_tokens 的 Flyway Migration，包含 UUID、账号状态、角色、软删除、Token family/session、外键、检查约束、部分唯一约束和索引。（范围：`src/main/resources/db/migration/V2__create_identity_tables.sql`；验收：Migration 在空库和已执行 V1 的数据库上均成功）
- [ ] 3.2 建立 user 与 auth 持久化 Entity，映射用户资料、账号状态和 Refresh Token 生命周期字段。（范围：`user/entity/**`、`auth/entity/**`；验收：字段类型、UTC 时间、软删除和枚举映射测试通过）
- [ ] 3.3 实现 users 与 refresh_tokens 的 MyBatis-Plus Repository/Mapper 查询和条件更新。（范围：`user/repository/**`、`auth/repository/**`、相关 Mapper XML；验收：自定义 SQL 使用显式字段且不包含用户输入拼接）
- [ ] 3.4 添加身份 Repository 的 PostgreSQL 集成测试，覆盖用户名/邮箱部分唯一约束、禁用不释放标识、软删除后复用、Token 哈希唯一性和有效会话查询。（范围：`src/test/java/**/user/repository/**`、`src/test/java/**/auth/repository/**`；验收：真实 PostgreSQL 约束和查询测试通过）
- [ ] 3.5 实现 BCrypt 密码处理、JWT 签发校验和 Refresh Token 随机生成与哈希能力。（范围：`auth/service/**`、`common/security/**`；验收：密码不可逆、JWT 过期/签名/签发者/受众校验和 Token 哈希测试通过）
- [ ] 3.6 建立默认拒绝的 Spring Security Filter Chain、Bearer JWT 认证和 401/403 ProblemDetail 处理。（范围：`common/security/**`、`common/config/**`；验收：公开路由放行、受保护路由无 Token 返回 401、角色不足返回 403）
- [ ] 3.7 添加安全基础测试，覆盖缺失、过期、篡改 JWT、USER/ADMIN 角色边界和日志脱敏。（范围：`src/test/java/**/security/**`；验收：所有认证与授权失败场景测试通过）
- [ ] 3.8 实现用户注册用例，覆盖参数校验、规范化标识、重复冲突、不做邮箱验证和软删除标识复用。（范围：`auth/service/**`、`user/service/**`、`auth/dto/**`；验收：注册正常、非法、重复和复用单元测试通过）
- [ ] 3.9 实现登录用例，覆盖统一凭据失败、禁用账号拒绝以及 Access/Refresh Token 签发。（范围：`auth/service/**`、`auth/dto/**`；验收：成功登录和账号枚举防护测试通过）
- [ ] 3.10 实现 Refresh Token 原子轮换、并发单次成功、重放检测、Token family 撤销和幂等退出。（范围：`auth/service/**`、`auth/repository/**`；验收：事务回滚、并发刷新、重放和重复退出测试通过）
- [ ] 3.11 实现注册、登录、刷新和退出 HTTP API；这是首批写入型认证 API，必须复用已完成的安全基础和统一异常。（范围：`auth/controller/**`、`auth/dto/**`；验收：API 状态码、ProblemDetail 和敏感字段过滤测试通过）
- [ ] 3.12 实现个人资料查看与修改、公开用户主页和用户公开文章查询契约。（范围：`user/service/**`、`user/controller/**`、`user/dto/**`；验收：本人修改、越权拒绝、隐私字段隐藏、禁用用户主页和分页契约测试通过）
- [ ] 3.13 添加 auth/user 端到端与 PostgreSQL 并发集成测试，覆盖注册登录生命周期、刷新轮换、退出、禁用后拒绝刷新和 Access Token 自然过期策略。（范围：`src/test/java/**/auth/**`、`src/test/java/**/user/**`；验收：规范中的 auth/user 正常与异常场景通过）
- [ ] 3.14 运行 Phase 3 验证命令并记录身份、安全与并发测试结果。（范围：auth/user/common-security 测试报告；验收：以下命令全部退出码为 0）

**Phase 3 验证命令：**

```powershell
.\mvnw.cmd -q test -Dtest="*Auth*,*User*,*Security*,*Token*"
.\mvnw.cmd -q test
```

## 4. Phase 4：文章、分类和标签

- [ ] 4.1 创建 categories、tags、posts 和 post_tags 的 Flyway Migration，包含文章状态、审核状态、稳定 slug、乐观锁、软删除、约束和公开查询索引。（范围：`src/main/resources/db/migration/V3__create_post_tables.sql`；验收：Migration 在 V2 数据库上成功且约束/索引存在）
- [ ] 4.2 建立 post 模块的文章、分类、标签和文章标签关系 Entity。（范围：`post/entity/**`；验收：状态、UUID、UTC、版本和软删除映射测试通过）
- [ ] 4.3 实现文章、分类、标签及关联关系的 MyBatis-Plus Repository/Mapper 与批量查询。（范围：`post/repository/**`、相关 Mapper XML；验收：SQL 使用显式字段并支持稳定分页、筛选和批量装配）
- [ ] 4.4 添加 post Repository 的 PostgreSQL 集成测试，覆盖 slug 唯一、标签规范化唯一、文章标签联合唯一、部分索引查询和分页排序。（范围：`src/test/java/**/post/repository/**`；验收：真实 PostgreSQL 约束、执行计划关键索引和无明显 N+1 测试通过）
- [ ] 4.5 实现 Markdown 渲染与后端白名单安全清洗，限制危险协议、脚本、嵌入内容和正文大小。（范围：`post/service/**`、`post/dto/**`；验收：合法 Markdown 保留格式，XSS 载荷无法进入公开 HTML）
- [ ] 4.6 实现草稿创建、本人查看和作者所有权校验。（范围：`post/service/**`；验收：创建成功、参数非法、访客及他人访问草稿返回规范结果）
- [ ] 4.7 实现文章编辑与乐观锁，覆盖已发布文章直接更新、版本冲突和非法状态。（范围：`post/service/**`、`post/repository/**`；验收：并发编辑只有一个成功，其他请求返回 409）
- [ ] 4.8 实现文章发布、首次 slug 生成、slug 永久稳定和幂等重复发布。（范围：`post/service/**`；验收：slug 冲突可解、标题编辑不改 slug、重复发布不改首次发布时间）
- [ ] 4.9 实现作者幂等软删除文章及删除后的公开、评论和点赞入口阻断。（范围：`post/service/**`；验收：重复删除成功且他人删除被拒绝）
- [ ] 4.10 实现管理员分类创建、修改和停用以及停用分类的历史读取规则。（范围：`post/service/**`、`post/controller/**`、`post/dto/**`；验收：管理员正常流程、普通用户 403、重复名称和不存在分类测试通过）
- [ ] 4.11 实现标签自动规范化、复用和并发创建一致性。（范围：`post/service/**`、`post/repository/**`；验收：大小写/空白归一化且并发创建只产生一个标签身份）
- [ ] 4.12 实现文章草稿、编辑、发布、删除、详情和本人文章 HTTP API。（范围：`post/controller/**`、`post/dto/**`；验收：受保护 API 使用既有安全基础，所有权和状态错误映射正确）
- [ ] 4.13 实现公开文章、用户文章、分类和标签查询 API，支持默认 20、最大 100、筛选和稳定排序。（范围：`post/controller/**`、`post/service/**`、`post/dto/**`；验收：草稿/隐藏/删除不泄露，分页总数和排序稳定）
- [ ] 4.14 添加 post 模块单元、Web 和 PostgreSQL 集成测试，覆盖状态、权限、Markdown、slug、标签并发、乐观锁和查询性能边界。（范围：`src/test/java/**/post/**`；验收：post specs 的正常与异常场景全部可追踪并通过）
- [ ] 4.15 运行 Phase 4 验证命令并记录文章、分类、标签与并发测试结果。（范围：post 及累计回归测试报告；验收：以下命令全部退出码为 0）

**Phase 4 验证命令：**

```powershell
.\mvnw.cmd -q test -Dtest="*Post*,*Category*,*Tag*,*Markdown*"
.\mvnw.cmd -q test
```

## 5. Phase 5：评论和回复

- [ ] 5.1 创建 comments 的 Flyway Migration，包含两层扁平关系、同文章引用、审核状态、orphan、版本、软删除约束和分页索引。（范围：`src/main/resources/db/migration/V4__create_comment_table.sql`；验收：Migration 在 V3 数据库上成功且外键/检查约束/索引存在）
- [ ] 5.2 建立评论 Entity，映射一级评论、根评论、具体回复目标、审核状态、orphan 和软删除字段。（范围：`comment/entity/**`；验收：一级与二级关系映射测试通过）
- [ ] 5.3 实现评论 MyBatis-Plus Repository/Mapper 与两层批量分页查询。（范围：`comment/repository/**`、相关 Mapper XML；验收：显式字段查询能稳定分页一级评论并批量加载二级回复）
- [ ] 5.4 添加 comment Repository 的 PostgreSQL 集成测试，覆盖同文章父子约束、自引用拒绝、公开索引和两层查询无明显 N+1。（范围：`src/test/java/**/comment/repository/**`；验收：真实 PostgreSQL 约束和分页查询测试通过）
- [ ] 5.5 实现一级评论创建和文章可评论状态校验。（范围：`comment/service/**`、post 模块公开查询接口；验收：公开文章成功，访客、非法内容及不可见文章失败）
- [ ] 5.6 实现回复一级或二级评论且公开展示最多两层的规则。（范围：`comment/service/**`；验收：二级回复目标保留，跨文章、隐藏和不存在父评论被拒绝）
- [ ] 5.7 实现作者编辑评论的所有权、参数和状态冲突校验。（范围：`comment/service/**`；验收：本人可编辑，越权、已删、隐藏和非法内容按规范失败）
- [ ] 5.8 实现评论幂等软删除、父删子留和 orphan 原子标记。（范围：`comment/service/**`、`comment/repository/**`；验收：子回复保留，重复删除无副作用）
- [ ] 5.9 实现删除父评论与新增回复的并发协调。（范围：`comment/service/**`、`comment/repository/**`；验收：并发后不存在未标记且指向不可用父评论的普通回复）
- [ ] 5.10 实现评论创建、回复、编辑、删除和公开两层分页 HTTP API。（范围：`comment/controller/**`、`comment/dto/**`；验收：认证、所有权、分页、排序、占位父评论和隐藏式 404 响应正确）
- [ ] 5.11 添加 comment 模块单元、Web 和 PostgreSQL 并发测试，覆盖两层限制、权限、状态、orphan、分页和删除并发。（范围：`src/test/java/**/comment/**`；验收：comment specs 的正常与异常场景全部可追踪并通过）
- [ ] 5.12 运行 Phase 5 验证命令并记录评论、回复与并发测试结果。（范围：comment 及累计回归测试报告；验收：以下命令全部退出码为 0）

**Phase 5 验证命令：**

```powershell
.\mvnw.cmd -q test -Dtest="*Comment*"
.\mvnw.cmd -q test
```

## 6. Phase 6：点赞和取消点赞

- [ ] 6.1 创建 post_likes 的 Flyway Migration，包含用户文章联合唯一约束、外键和文章统计索引。（范围：`src/main/resources/db/migration/V5__create_post_like_table.sql`；验收：Migration 在 V4 数据库上成功且并发唯一约束存在）
- [ ] 6.2 建立文章点赞 Entity。（范围：`reaction/entity/**`；验收：用户、文章和 UTC 创建时间映射测试通过）
- [ ] 6.3 实现点赞 MyBatis-Plus Repository/Mapper 的幂等写入、删除、用户状态查询和文章聚合计数。（范围：`reaction/repository/**`、相关 Mapper XML；验收：显式字段 SQL 支持重复请求和批量状态查询）
- [ ] 6.4 添加 reaction Repository 的 PostgreSQL 集成测试，覆盖联合唯一、重复点赞、重复取消、聚合计数和文章批量状态查询。（范围：`src/test/java/**/reaction/repository/**`；验收：真实 PostgreSQL 幂等和计数测试通过）
- [ ] 6.5 实现仅针对公开文章的点赞与取消用例。（范围：`reaction/service/**`、post 模块公开状态接口；验收：访客、非文章对象、不存在及不可见文章按规范失败）
- [ ] 6.6 实现点赞、取消并发下的最终状态和计数一致性。（范围：`reaction/service/**`、`reaction/repository/**`；验收：并发后最多一条有效关系且计数不负、不重）
- [ ] 6.7 实现 `PUT/DELETE` 文章点赞 HTTP API，并在文章响应中装配点赞总数和当前用户状态。（范围：`reaction/controller/**`、`reaction/dto/**`、post 查询 DTO；验收：端点幂等、认证和公开响应行为正确）
- [ ] 6.8 添加 reaction 模块单元、Web 和 PostgreSQL 并发测试，覆盖重复请求、权限、文章状态、点赞取消竞争和计数一致性。（范围：`src/test/java/**/reaction/**`；验收：reaction specs 的正常与异常场景全部可追踪并通过）
- [ ] 6.9 运行 Phase 6 验证命令并记录点赞幂等与并发测试结果。（范围：reaction 及累计回归测试报告；验收：以下命令全部退出码为 0）

**Phase 6 验证命令：**

```powershell
.\mvnw.cmd -q test -Dtest="*Reaction*,*Like*"
.\mvnw.cmd -q test
```

## 7. Phase 7：管理员审核

- [ ] 7.1 创建 moderation_actions 的 Flyway Migration，包含互斥目标引用、审核动作、非空原因、不可变审计约束和查询索引。（范围：`src/main/resources/db/migration/V6__create_moderation_action_table.sql`；验收：Migration 在 V5 数据库上成功且一条记录只能关联一种目标）
- [ ] 7.2 建立审核操作 Entity。（范围：`moderation/entity/**`；验收：文章、评论、用户三类目标与 UTC 审核时间映射测试通过）
- [ ] 7.3 实现审核记录 MyBatis-Plus Repository/Mapper 的追加、筛选、稳定分页和排序查询。（范围：`moderation/repository/**`、相关 Mapper XML；验收：显式字段查询支持目标、动作、管理员和时间筛选）
- [ ] 7.4 添加 moderation Repository 的 PostgreSQL 集成测试，覆盖互斥目标、审核原因、只追加约束和分页索引。（范围：`src/test/java/**/moderation/repository/**`；验收：真实 PostgreSQL 约束和稳定分页测试通过）
- [ ] 7.5 实现文章隐藏与恢复事务，确保状态更新和审核记录原子完成且不能恢复作者已删除文章。（范围：`moderation/service/**`、post 模块公开审核接口；验收：正常、重复、非法原因、不存在和状态冲突测试通过）
- [ ] 7.6 实现评论隐藏与恢复事务，确保不恢复作者已删除正文且不自动改变文章状态。（范围：`moderation/service/**`、comment 模块公开审核接口；验收：正常、重复、非法原因、不存在和状态冲突测试通过）
- [ ] 7.7 实现用户禁用与恢复事务，禁用时撤销全部 Refresh Token 且保留历史公开内容。（范围：`moderation/service/**`、user/auth 模块公开管理接口；验收：登录刷新被阻止、既有 Access Token 自然过期、历史主页文章评论仍可见）
- [ ] 7.8 实现作者查看自己隐藏内容、隐藏状态和审核原因的查询边界。（范围：`moderation/service/**`、post/comment 查询 DTO；验收：作者可见完整状态，其他普通用户得到隐藏式 404）
- [ ] 7.9 实现管理员文章、评论、用户状态和审核记录 HTTP API。（范围：`moderation/controller/**`、`moderation/dto/**`；验收：ADMIN 可用、USER 返回 403、重复 PUT 幂等、非法参数使用统一 ProblemDetail）
- [ ] 7.10 添加 moderation 模块单元、Web 和 PostgreSQL 事务测试，覆盖权限矩阵、状态独立性、审计不可变和失败回滚。（范围：`src/test/java/**/moderation/**`；验收：moderation specs 的正常与异常场景全部可追踪并通过）
- [ ] 7.11 运行 Phase 7 验证命令并记录审核、权限和事务测试结果。（范围：moderation/auth/user/post/comment 累计测试报告；验收：以下命令全部退出码为 0）

**Phase 7 验证命令：**

```powershell
.\mvnw.cmd -q test -Dtest="*Moderation*,*Admin*"
.\mvnw.cmd -q test
```

## 8. Phase 8：OpenAPI、Actuator、文档和完整回归测试

- [ ] 8.1 配置 OpenAPI 元数据、Bearer JWT 安全方案、公开/受保护端点标注和 ProblemDetail 示例。（范围：`common/config/**`、各模块 controller/dto 的 OpenAPI 描述；验收：OpenAPI JSON 包含全部 `/api/v1` 端点、认证要求和错误响应）
- [ ] 8.2 配置 Actuator 健康与信息端点的最小暴露和敏感详情保护。（范围：`pom.xml`、`src/main/resources/application*.yaml`、`common/config/**`；验收：公开健康检查可用且未授权请求看不到数据库凭据或内部详情）
- [ ] 8.3 完善 Docker Compose 应用 profile、数据库启动依赖和容器健康检查。（范围：`compose.yaml`、`Dockerfile`、`.dockerignore`；验收：PostgreSQL 健康后应用启动并完成 Flyway 迁移）
- [ ] 8.4 编写本地启动、环境变量、Migration、测试、OpenAPI、Actuator 和故障排查文档。（范围：`README.md`、必要的 `docs/**`；验收：新环境可仅按文档完成启动与测试）
- [ ] 8.5 建立 proposal/specs 到自动化测试的追踪清单，确认六个领域每条需求至少由一个测试覆盖。（范围：`docs/testing.md` 或测试元数据、`src/test/java/**`；验收：不存在未映射的 auth/user/post/comment/reaction/moderation 需求）
- [ ] 8.6 添加完整权限矩阵回归测试，覆盖访客、注册用户、管理员、所有权和隐藏式 404。（范围：`src/test/java/**/regression/**`；验收：所有公开、用户和管理端 API 的 401/403/404 边界通过）
- [ ] 8.7 添加完整状态与并发回归测试，覆盖 Token 轮换、文章乐观锁、标签唯一、评论 orphan、点赞幂等和审核回滚。（范围：`src/test/java/**/regression/**`；验收：真实 PostgreSQL 下重复运行结果稳定）
- [ ] 8.8 添加分页、排序、索引和 N+1 回归测试，覆盖文章、用户主页、评论和审核记录列表。（范围：`src/test/java/**/regression/**`；验收：默认 20、最大 100、非法排序拒绝、稳定顺序和查询数量边界通过）
- [ ] 8.9 执行从空数据库启动的端到端验收，覆盖注册登录、发文、评论、点赞、审核、禁用用户和恢复用户主链路。（范围：Docker Compose 环境、`src/test/java/**/e2e/**`；验收：从空卷启动后主链路和异常链路均通过）
- [ ] 8.10 运行完整 OpenSpec、构建、测试和 Compose 验证命令并保存最终报告；所有命令成功前不得勾选任何未验证任务。（范围：OpenSpec Change、`target/`、Compose 服务和最终测试报告；验收：以下命令全部退出码为 0）

**Phase 8 验证命令：**

```powershell
openspec-cn validate build-blog-forum-mvp --strict
docker compose config
docker compose up -d --build
docker compose ps
.\mvnw.cmd -q clean verify
```
