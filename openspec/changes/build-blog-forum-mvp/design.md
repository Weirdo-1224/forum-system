## 上下文

当前仓库是 Java 17、Spring Boot 3.5.x 的单 Maven 工程，仅具备基础启动骨架，尚未实现论坛业务。MVP 需要一次性建立认证、用户、文章、评论、点赞和审核六个领域，并满足权限隔离、软删除、Token 轮换、内容状态、并发写入和可审计性要求。

技术基线固定为 Maven 与 Maven Wrapper、PostgreSQL、MyBatis-Plus、Flyway、Spring Security、JWT Access Token、Refresh Token、JUnit 5、PostgreSQL Testcontainers、Docker Compose、OpenAPI 和 Actuator。系统以单应用、单数据库方式部署，不引入 Redis、消息队列、Elasticsearch 或微服务基础设施。

主要使用者是访客、注册用户和管理员。设计优先保证 MVP 的可验证行为与清晰模块边界，不追求完整领域驱动设计，也不拆分 Maven 多模块。

## 目标 / 非目标

**目标：**

- 在单一 Spring Boot 应用中形成边界明确的模块化单体。
- 落实 proposal 和六个领域 specs 中的全部业务行为。
- 提供稳定的 `/api/v1` REST API、统一错误语义和分页约定。
- 使用 PostgreSQL 约束、事务和并发控制保证核心一致性。
- 建立可轮换、可撤销、可检测重放的 Refresh Token 生命周期。
- 保证文章、评论、点赞和审核状态之间不会互相覆盖。
- 通过 JUnit 5、Testcontainers 和架构测试支持分阶段验收。
- 支持使用 Docker Compose 建立一致的本地 PostgreSQL 环境。

**非目标：**

- 不开发前端、图片上传、私信、关注、通知、全文搜索或推荐。
- 不引入微服务、Maven 多模块、Redis、MQ、Elasticsearch 或分布式事务。
- 不采用聚合根、领域事件总线、端口适配器全套分层等重型 DDD 结构。
- 不支持邮箱验证、找回密码、第三方登录、多因素认证或用户自助注销。
- 不提供 Access Token 实时黑名单；禁用用户已有 Access Token 自然过期。
- 不维护文章历史版本，不提供定时发布、评论点赞或删除内容恢复。

## 决策

### 总体架构

系统采用单 Maven 工程、单 Spring Boot 进程、单 PostgreSQL 数据库的模块化单体：

```text
HTTP / JSON
    │
    ▼
Spring Security Filter Chain
    │
    ▼
业务模块 Controller → Service → Repository/MyBatis Mapper → PostgreSQL
                              │
                              └─ Flyway 管理结构

横切能力：统一异常、校验、分页、OpenAPI、Actuator、日志与追踪标识
```

所有模块随同一应用部署并可共享本地事务，但代码依赖仍按业务边界控制。模块间只调用对方公开的 Service/Facade；不得注入或调用其他模块的 Repository、Mapper 或内部 Entity。

### 包结构

基础包使用 `org.example.forum_system`，一级业务包固定如下：

```text
org.example.forum_system
├─ common
│  ├─ config
│  ├─ error
│  ├─ pagination
│  ├─ security
│  └─ web
├─ auth
│  ├─ controller
│  ├─ service
│  ├─ repository
│  ├─ entity
│  └─ dto
├─ user
│  ├─ controller
│  ├─ service
│  ├─ repository
│  ├─ entity
│  └─ dto
├─ post
│  ├─ controller
│  ├─ service
│  ├─ repository
│  ├─ entity
│  └─ dto
├─ comment
│  ├─ controller
│  ├─ service
│  ├─ repository
│  ├─ entity
│  └─ dto
├─ reaction
│  ├─ controller
│  ├─ service
│  ├─ repository
│  ├─ entity
│  └─ dto
└─ moderation
   ├─ controller
   ├─ service
   ├─ repository
   ├─ entity
   └─ dto
```

- `controller` 只负责 HTTP 适配、认证主体读取、参数校验、DTO 转换和状态码选择，不包含核心规则或事务编排。
- `service` 负责业务规则、所有权检查、事务边界和跨模块公开接口。
- `repository` 存放 MyBatis-Plus Mapper 及必要的自定义查询组件；它是持久化包，不额外叠加一套重型 DDD Repository 抽象。
- `entity` 是持久化模型，只在模块内部使用，不直接作为 API 请求或响应。
- `dto` 定义模块对外 HTTP 契约和必要的模块公开数据视图。
- `common` 只承载无业务归属的稳定横切能力，禁止放置全局业务 Service、Repository 或 Entity。

### 模块依赖

```text
common ← 所有模块

user ← auth
user ← post（仅查询作者公开信息）
post ← comment（校验文章可评论性）
post ← reaction（校验文章可点赞性）
user/post/comment ← moderation（通过公开审核入口改变状态）
auth ← moderation（通过 auth 公开入口撤销 Refresh 会话）
```

依赖约束：

- `auth` 通过 `user` 的公开账号查询能力校验凭据和账号状态，不访问 user Repository。
- `post` 可通过 `user` 的批量公开资料查询能力组装作者展示信息；用户主页的文章列表由 post 模块提供查询 API，避免 user 与 post 双向依赖。
- `comment` 和 `reaction` 通过 post 的公开状态查询判断文章是否存在、已发布且可见。
- `moderation` 通过 user、post、comment 和 auth 的公开管理入口完成状态变更与 Token 撤销；审核记录由 moderation 自己维护。
- 跨模块调用传递 UUID、不可变值或公开 DTO，不传递内部 Entity。
- 使用架构测试禁止跨模块 repository 访问、禁止 Controller 访问 Repository、禁止 Entity 暴露到 controller DTO。

### API 列表

所有 API 使用 `/api/v1` 前缀、JSON、Jakarta Bean Validation 和统一 ProblemDetail 响应。分页默认 20、最大 100，排序字段使用端点白名单。

#### Auth

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| POST | `/api/v1/auth/register` | 访客 | 注册账号，不自动完成邮箱验证 |
| POST | `/api/v1/auth/login` | 访客 | 登录并签发 Access/Refresh Token |
| POST | `/api/v1/auth/refresh` | 访客持 Refresh Token | 轮换 Refresh Token |
| POST | `/api/v1/auth/logout` | 当前会话 | 幂等撤销当前 Refresh 会话 |

#### User

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| GET | `/api/v1/users/me` | 注册用户 | 查看自己的资料 |
| PATCH | `/api/v1/users/me` | 注册用户 | 修改自己的基础资料 |
| GET | `/api/v1/users/{username}` | 公开 | 查看公开用户主页 |
| GET | `/api/v1/users/{username}/posts` | 公开 | 分页查看用户公开文章 |

#### Post、分类和标签

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| GET | `/api/v1/posts` | 公开 | 分页、排序、分类和标签筛选 |
| GET | `/api/v1/posts/{slug}` | 公开/作者 | 公开详情；作者可查看自己的非公开文章 |
| POST | `/api/v1/posts` | 注册用户 | 创建草稿 |
| GET | `/api/v1/users/me/posts` | 注册用户 | 查看自己的草稿及其他状态文章 |
| PATCH | `/api/v1/posts/{id}` | 作者 | 编辑文章并进行版本校验 |
| POST | `/api/v1/posts/{id}/publish` | 作者 | 幂等发布草稿 |
| DELETE | `/api/v1/posts/{id}` | 作者 | 幂等软删除文章 |
| GET | `/api/v1/categories` | 公开 | 查询有效分类 |
| POST | `/api/v1/admin/categories` | 管理员 | 创建分类 |
| PATCH | `/api/v1/admin/categories/{id}` | 管理员 | 修改或停用分类 |
| GET | `/api/v1/tags` | 公开 | 查询或匹配标签 |

标签在文章创建或编辑请求中提交，由系统自动规范化、复用或创建，不额外暴露普通用户创建标签端点。

#### Comment

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| GET | `/api/v1/posts/{slug}/comments` | 公开 | 分页查询两层评论 |
| POST | `/api/v1/posts/{slug}/comments` | 注册用户 | 发表一级评论 |
| POST | `/api/v1/comments/{id}/replies` | 注册用户 | 回复一级或二级评论，展示仍为两层 |
| PATCH | `/api/v1/comments/{id}` | 作者 | 编辑自己的可编辑评论 |
| DELETE | `/api/v1/comments/{id}` | 作者 | 幂等软删除评论 |

#### Reaction

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| PUT | `/api/v1/posts/{slug}/like` | 注册用户 | 幂等点赞文章 |
| DELETE | `/api/v1/posts/{slug}/like` | 注册用户 | 幂等取消点赞 |

文章详情返回有效点赞总数；已认证请求额外返回当前用户点赞状态。

#### Moderation

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| PUT | `/api/v1/admin/posts/{id}/visibility` | 管理员 | 将文章设为 HIDDEN 或 VISIBLE并记录原因 |
| PUT | `/api/v1/admin/comments/{id}/visibility` | 管理员 | 将评论设为 HIDDEN 或 VISIBLE并记录原因 |
| PUT | `/api/v1/admin/users/{id}/status` | 管理员 | 将用户设为 DISABLED 或 ACTIVE并记录原因 |
| GET | `/api/v1/admin/moderation-actions` | 管理员 | 分页、筛选和排序审核记录 |

状态写入端点使用 `PUT` 表达目标状态，因此重复提交天然保持幂等。恢复已被作者删除的内容返回状态冲突。

### 数据模型

所有主业务标识使用 UUID，时间使用 UTC 的带时区时间，所有 Entity 都包含必要的创建和更新时间。核心模型如下：

#### users

- 身份：`id`。
- 登录标识：`username`、规范化用户名、`email`、规范化邮箱、`password_hash`。
- 权限与状态：`role`（USER/ADMIN）、`status`（ACTIVE/DISABLED）。
- 公开资料：`display_name`、`bio`、`avatar_url`。
- 生命周期：`created_at`、`updated_at`、`deleted_at`。
- 禁用不是删除；禁用记录仍占用用户名和邮箱，软删除后标识才可复用。

#### refresh_tokens

- `id`、`user_id`、唯一 `token_hash`。
- `session_id` 标识一次登录会话，`family_id` 标识连续轮换链。
- `expires_at`、`revoked_at`、`replaced_by_token_id`、`created_at`。
- 数据库只保存 Refresh Token 的不可逆哈希，不保存明文。

#### categories

- `id`、`name`、规范化名称、`status`（ACTIVE/INACTIVE）、创建与更新时间。
- 停用分类不删除历史文章关系，但新发布或编辑不得再选择它。

#### tags 与 post_tags

- 标签保存 `id`、展示名称、规范化名称和创建时间。
- 文章标签关系保存 `post_id`、`tag_id`。
- 标签规范化后复用；文章删除不自动删除标签。

#### posts

- `id`、`author_id`、`category_id`。
- 内容：`title`、唯一 `slug`、`markdown_source`、经过安全清洗的 `rendered_html`。
- 发布状态：`publication_status`（DRAFT/PUBLISHED）。
- 审核状态：`moderation_status`（VISIBLE/HIDDEN）。
- 并发与生命周期：`version`、`published_at`、`created_at`、`updated_at`、`deleted_at`。
- slug 在首次发布时生成，标题编辑不改变 slug；软删除文章仍保留 slug，因此不会被复用。

#### comments

- `id`、`post_id`、`author_id`。
- 关系：`parent_id` 指向一级评论，`root_id` 标识评论组，`reply_to_comment_id` 保存具体被回复评论。
- 内容与状态：`content`、`moderation_status`（VISIBLE/HIDDEN）、`orphaned_at`。
- 生命周期：`version`、`created_at`、`updated_at`、`deleted_at`。
- 一级评论的 `parent_id/root_id` 为空；二级回复的 `parent_id/root_id` 均指向一级评论。回复二级评论只改变 `reply_to_comment_id`，不产生第三层。

#### post_likes

- `user_id`、`post_id`、`created_at`。
- `(user_id, post_id)` 同时作为业务唯一键；MVP 不维护冗余点赞计数，按有效关系聚合，避免计数列与关系状态不一致。

#### moderation_actions

- `id`、`moderator_id`、`target_type`、目标文章/评论/用户引用、`action`、`reason`、`created_at`。
- 一条记录只允许关联一种目标；记录只追加，不通过业务 API 修改或删除。

### 数据库约束

- 所有主键、外键和非空字段由 PostgreSQL 约束保证，禁止只依赖应用校验。
- 未软删除用户的规范化用户名唯一，未软删除用户的规范化邮箱唯一；禁用用户仍属于未删除用户。
- `role`、用户状态、发布状态、审核状态、分类状态、审核动作和目标类型使用枚举式检查约束。
- Refresh Token 哈希全局唯一，替换关系不得指回自身。
- 文章 slug 全局唯一且软删除后仍保留；标题、正文、作者和版本不能为空。
- 标签规范化名称唯一；文章与标签关系联合唯一。
- 点赞的用户与文章联合唯一，防止重复和并发点赞。
- 评论的父评论、根评论和被回复评论必须与当前评论属于同一文章；评论不得引用自身。
- 评论最多两层主要由 Service 在事务内校验，数据库外键保证引用目标存在且属于同一文章。
- 审核记录必须且只能关联文章、评论或用户之一；审核原因不能为空。
- 核心外键默认限制物理删除。业务数据使用软删除，避免级联物理删除历史内容与审核证据。

### 数据库索引

- users：未删除记录上的规范化用户名、规范化邮箱部分唯一索引；账号状态索引用于管理查询。
- refresh_tokens：Token 哈希唯一索引；`(user_id, revoked_at, expires_at)`、`family_id` 和 `session_id` 索引用于有效会话与重放处理。
- posts：slug 唯一索引；公开文章使用覆盖发布、审核、删除状态并按 `published_at DESC, id` 排序的部分组合索引；作者文章使用 `(author_id, publication_status, updated_at DESC, id)`；分类列表使用 `(category_id, published_at DESC, id)`。
- tags：规范化名称唯一索引；post_tags 同时提供 `(post_id, tag_id)` 唯一索引和 `(tag_id, post_id)` 反向筛选索引。
- comments：公开评论使用 `(post_id, parent_id, created_at, id)`；二级回复使用 `(root_id, created_at, id)`；作者管理使用 `(author_id, updated_at DESC, id)`。
- post_likes：`(user_id, post_id)` 唯一索引及 `(post_id, created_at)` 统计索引。
- moderation_actions：按目标与时间、管理员与时间建立组合索引，支持审核记录筛选和稳定分页。

列表统一在排序字段后追加 UUID 作为稳定次序。公开文章、用户文章、评论和审核记录避免逐行查询作者、标签或点赞状态，使用批量查询或受控联表查询规避 N+1。

### 文章和评论状态

文章使用三个互不覆盖的维度：

```text
发布状态：DRAFT ──发布──> PUBLISHED
审核状态：VISIBLE <──────> HIDDEN
删除状态：deleted_at 为空 ──作者删除──> 非空（终态）
```

公开条件为：已发布、未隐藏且未删除。管理员恢复只把 HIDDEN 改为 VISIBLE，不能清除 `deleted_at`。发布后编辑直接更新内容并保留 PUBLISHED；隐藏文章的作者仍可读取内容、状态和审核原因。

评论状态同样分离：

```text
审核状态：VISIBLE <──────> HIDDEN
删除状态：deleted_at 为空 ──作者删除──> 非空
父子状态：正常回复 ──父评论删除──> orphan 回复
```

删除有子回复的一级评论时保留父记录作为删除占位，并在同一事务内设置子回复 `orphaned_at`。新增回复必须锁定并重新确认一级评论状态；若删除并发发生，新回复要么失败，要么与其他回复一致成为 orphan。

### JWT 流程

1. 注册只创建 ACTIVE 用户，不执行邮箱验证；客户端随后登录。
2. 登录校验规范化标识、BCrypt 密码和 ACTIVE 状态。
3. 登录成功签发 30 分钟 JWT Access Token 和 7 天随机高熵 Refresh Token。
4. Access Token 至少包含 `sub`（用户 UUID）、角色、签发时间、过期时间、唯一 `jti`、签发者和受众，不包含邮箱、密码或其他敏感资料。
5. Spring Security 以无状态方式校验 JWT 签名、签发者、受众和过期时间，并建立认证主体。
6. API 默认拒绝；公开路由显式放行，普通写操作要求 USER 或 ADMIN，管理路由要求 ADMIN。
7. JWT 密钥从环境变量读取，启动时校验强度；禁止写入仓库、响应或日志。
8. 用户被禁用时撤销其 Refresh Token，但已有 Access Token 不查黑名单，在最长 30 分钟内自然到期，这是明确接受的权衡。

### Refresh Token 轮换

Refresh Token 使用随机不透明值，而不是自包含 JWT，以便服务端严格控制撤销和轮换：

1. 客户端提交 Refresh Token，服务端计算哈希并定位记录。
2. 在事务中锁定或条件更新该记录，校验未过期、未撤销、用户仍为 ACTIVE。
3. 将旧 Token 标记撤销，生成新的随机 Refresh Token，保存其哈希并建立替换关系。
4. 同一事务签发新的 Access Token；事务成功后才向客户端返回新 Token 明文。
5. 并发刷新同一 Token 时，只有一个请求能完成状态切换，其余请求得到 Token 已失效结果。
6. 已轮换 Token 再次出现视为重放，撤销其 `family_id` 下仍有效的 Refresh Token。
7. 退出登录幂等撤销当前 Refresh Token；管理员禁用用户时批量撤销该用户所有有效 Refresh Token。

### 权限控制

| 能力 | 访客 | 注册用户 | 管理员 |
|---|---:|---:|---:|
| 注册、登录、刷新 | 允许 | 允许 | 允许 |
| 浏览公开文章、评论、用户主页 | 允许 | 允许 | 允许 |
| 创建、编辑、发布、删除文章 | 禁止 | 仅自己的文章 | 仅自己的文章 |
| 评论、回复、编辑、删除评论 | 禁止 | 创建；仅编辑删除自己的评论 | 同普通用户 |
| 点赞、取消点赞文章 | 禁止 | 允许 | 允许 |
| 管理分类 | 禁止 | 禁止 | 允许 |
| 隐藏、恢复文章或评论 | 禁止 | 禁止 | 允许 |
| 禁用、恢复用户 | 禁止 | 禁止 | 允许 |
| 查看完整审核记录 | 禁止 | 禁止 | 允许 |

权限同时在路由层和 Service 层校验。所有权敏感更新必须把资源身份、当前用户和允许状态作为同一业务判断，不能只依赖客户端传入作者 ID。对草稿、隐藏内容和他人私有资源，普通请求统一返回不存在，降低资源枚举风险。

### 事务边界

- 注册：用户创建与唯一标识校验为一个事务；数据库唯一冲突转换为稳定业务错误。
- Refresh Token 轮换：旧 Token 撤销、新 Token 创建和替换关系为一个事务。
- 退出：当前 Refresh Token 撤销为短事务并保持幂等。
- 禁用用户：用户状态变更、全部 Refresh Token 撤销和审核记录写入为一个事务。
- 文章创建/编辑：文章、分类校验、标签规范化创建和文章标签关系更新为一个事务。
- 发布文章：状态校验、slug 分配、发布时间与版本更新为一个事务。
- 评论创建：文章可评论状态、父评论关系和评论写入为一个事务。
- 删除一级评论：父评论软删除和全部子回复 orphan 标记为一个事务，并与新增回复进行并发协调。
- 点赞/取消：文章可点赞状态与点赞关系写入为一个短事务。
- 审核：目标状态更新和 moderation_actions 追加为一个事务。

跨模块事务只允许由上层公开 Service 编排，参与模块不得捕获并吞掉导致一致性失败的异常。只读列表使用只读事务或单次一致查询，不在长事务中执行分页响应组装。

### 点赞幂等

- 点赞入口使用 `PUT`，取消点赞使用 `DELETE`。
- 点赞依赖 `(user_id, post_id)` 唯一约束，以“插入成功或已存在”统一为成功结果；不得采用“先查再插”作为唯一保护。
- 取消点赞删除不存在关系时仍返回成功，不产生负数或额外副作用。
- MVP 不保存冗余点赞计数；公开计数从有效点赞关系聚合，确保并发后自然一致。
- 点赞与取消并发时，以数据库最终存在与否作为用户最终状态，响应组装必须读取事务完成后的状态。

### 文章乐观锁

- posts 使用递增 `version`。
- 编辑请求携带客户端最近读取的版本；更新同时匹配文章 ID、作者、允许状态和版本。
- 更新成功后版本递增。匹配行数为零时重新区分不存在、无权限、状态不允许和版本冲突，并返回稳定错误码。
- 发布、作者删除和管理员可见性变更同样参与版本校验，避免不同操作互相覆盖。
- 冲突返回 HTTP 409，服务端不得静默覆盖新版本；客户端需要重新读取后决定是否重试。

### 统一异常结构

所有错误使用 `application/problem+json` 的 ProblemDetail 风格：

```json
{
  "type": "https://forum.example/problems/resource-conflict",
  "title": "资源状态冲突",
  "status": 409,
  "detail": "文章已被其他请求更新",
  "instance": "/api/v1/posts/...",
  "code": "POST_VERSION_CONFLICT",
  "traceId": "...",
  "timestamp": "...",
  "errors": []
}
```

- 400：参数格式、校验、分页或排序错误。
- 401：缺少、非法或过期认证信息。
- 403：身份有效但角色或操作权限不足。
- 404：资源不存在，或为避免泄露而隐藏私有/审核状态。
- 409：唯一冲突、版本冲突或不允许的状态转换。
- 500：未预期错误，只返回通用说明和 traceId，不返回堆栈、SQL、Token 或内部路径。

字段校验错误放入 `errors`，包含稳定字段名和原因代码。异常日志按 traceId 关联并对密码、Authorization、Cookie、Refresh Token 和 JWT 做脱敏。

### OpenAPI 与 Actuator

- Springdoc OpenAPI 描述所有公开与管理端 API、认证方式、分页参数、状态码和 ProblemDetail 示例。
- OpenAPI 将 Bearer JWT 定义为统一安全方案；公开端点显式标记无需认证。
- 生产环境可关闭 Swagger UI 或限制为管理员/内部网络，避免无控制暴露管理接口说明。
- Actuator 默认只公开 `health` 和必要的 `info`；详细健康信息不向未授权用户暴露。
- 数据库健康检查参与容器健康状态，但不在响应中暴露连接串和凭据。

### 测试策略

- **单元测试**：JUnit 5 与 Mockito 测试 Service 业务规则，包括权限、状态机、所有权、Token 轮换、orphan、幂等和错误映射。
- **安全与 Web 测试**：验证公开路由、401、403、资源隐藏式 404、DTO 校验、ProblemDetail 和角色矩阵。
- **Repository 集成测试**：统一使用 PostgreSQL Testcontainers，不使用 H2；验证 MyBatis-Plus 映射、自定义 SQL、部分唯一索引、外键、检查约束、分页和排序。
- **Flyway 测试**：空数据库执行全部迁移；基于上一版本数据库执行升级；验证迁移重复启动不会产生漂移。
- **并发测试**：使用真实 PostgreSQL 验证同一 Refresh Token 并发轮换、文章乐观锁、标签并发创建、评论删除与回复并发、重复点赞和点赞取消并发。
- **模块架构测试**：验证 Controller 不访问 Repository、模块不访问其他模块 Repository、Entity 不作为 API DTO。
- **端到端测试**：按注册登录、发文、评论、点赞、审核和禁用用户的主链路逐阶段验收。
- **查询测试**：针对公开文章、用户主页、评论和审核列表检查稳定分页、筛选和排序；通过查询统计或日志断言防止明显 N+1。

### Docker 本地环境

Docker Compose 提供 PostgreSQL 服务，固定项目级数据库名和非生产默认账号，并配置：

- PostgreSQL 数据卷，支持本地重启后保留数据。
- `pg_isready` 健康检查，应用等待数据库健康后启动。
- 端口、数据库名、用户名和密码通过环境变量覆盖；生产凭据不得写入 Compose 默认文件。
- 应用使用独立本地配置读取 JDBC URL、数据库凭据和 JWT 密钥。
- 默认开发流程为 `docker compose up -d postgres` 后通过 Maven Wrapper 启动应用；可选 Compose profile 再构建应用容器。
- Flyway 在应用启动时执行迁移，MyBatis-Plus 不承担建表职责。
- Testcontainers 测试使用独立临时 PostgreSQL，不复用开发 Compose 数据库。

### 迁移与交付计划

1. 对齐 Maven 依赖：移除 JPA，加入与 Spring Boot 3.5.x 兼容的 MyBatis-Plus、PostgreSQL/Flyway、Security/JWT、OpenAPI、Actuator 和测试依赖。
2. 建立 Docker Compose PostgreSQL、本地配置、环境变量约定和基础 Flyway 迁移。
3. 建立 common 横切能力与模块包边界，再按 auth/user、post、comment/reaction、moderation 顺序交付。
4. 每阶段先应用向前兼容迁移，再部署应用，并运行该阶段单元、集成和端到端验收。
5. 数据库变更遵循“扩展、迁移、收缩”；失败优先回退应用并执行前向修复，不回滚删除已产生的审核记录。
6. 若某写能力出现一致性问题，可临时关闭对应写入口，保留公开读能力和审计证据。

### 备选方案和取舍

| 方案 | 决策 | 原因与取舍 |
|---|---|---|
| 微服务 | 放弃 | 当前团队和 MVP 规模不需要网络边界、服务治理和分布式事务成本 |
| Maven 多模块 | 放弃 | 单工程包边界配合架构测试已足够；减少构建和依赖管理复杂度 |
| 重型完整 DDD | 放弃 | 核心规则存在但领域复杂度不足以抵消聚合、端口和多层映射成本 |
| Spring Data JPA | 放弃 | 项目明确选择 MyBatis-Plus，需要可控 SQL 与 PostgreSQL 查询行为 |
| MyBatis Mapper 直接跨模块复用 | 放弃 | 会破坏模块边界并把数据模型耦合到调用方 |
| JWT Refresh Token | 放弃 | 不透明随机 Token 配合服务端哈希记录更适合严格轮换、撤销和重放检测 |
| Redis Token 黑名单 | 放弃 | MVP 接受 Access Token 最长 30 分钟自然失效，避免新增基础设施 |
| 冗余点赞计数列 | 暂不采用 | 关系聚合更简单可靠；流量证明需要后再增加原子计数与校准机制 |
| 评论递归树 | 放弃 | 产品最多两层；扁平关系查询、分页和 orphan 处理更可控 |
| 标题变化自动改 slug | 放弃 | 会破坏已分享链接；稳定 slug 优先于标题同步 |
| 数据库触发器承载业务状态 | 放弃 | 约束留在数据库，跨状态业务编排留在 Service，降低隐式行为 |
| MQ/领域事件驱动 | 放弃 | 当前操作可在单体本地事务内完成，不需要最终一致性复杂度 |

## 风险 / 权衡

- **禁用用户的 Access Token 不立即失效** → 接受最长 30 分钟写入窗口；立即撤销全部 Refresh Token，并保持较短 Access Token 生命周期。
- **发布后编辑无需重新审核** → 编辑直接生效，但保留更新时间与审核历史；管理员可再次隐藏。
- **Refresh Token 重放和并发刷新** → 使用单事务原子轮换、Token family 和重放后整族撤销。
- **Markdown XSS** → 保存源 Markdown，但公开输出只使用经过白名单清洗的渲染结果；危险协议和嵌入内容默认拒绝。
- **软删除标识复用造成身份混淆** → 历史内容始终关联不可变用户 UUID；禁用不释放标识，仅软删除释放。
- **评论 orphan 状态漂移** → 父删除与子标记同事务执行，新增回复锁定并复查父状态，提供一致性集成测试。
- **MyBatis 动态 SQL 注入** → 排序和筛选使用服务端白名单，禁止将用户输入直接拼接为 SQL 结构。
- **跨模块本地事务扩大耦合** → 只允许少量协调型 Service 跨模块调用，禁止跨模块 Repository；事务保持短小。
- **点赞实时聚合随规模增长变慢** → MVP 先依赖索引与聚合；出现真实性能瓶颈后再引入可校准的计数列，而非提前增加缓存。
- **审核记录目标使用多类型关联** → 通过目标类型检查和互斥外键约束保证一条记录只有一个目标，避免无约束多态引用。
- **分页使用偏移量在深页性能下降** → MVP 保留统一页码契约；公开文章流达到规模后可新增游标分页而不移除现有接口。

## 待确认事项

- Markdown 渲染与 HTML 白名单库的具体选型在实现任务中确认，但必须满足后端安全清洗要求。
- 用户名、简介、文章、评论、标签和审核原因的具体长度上限在实现前统一固化为 API 校验与数据库约束。
- OpenAPI UI 在生产环境是完全关闭还是仅管理员可见，由部署环境策略决定。
