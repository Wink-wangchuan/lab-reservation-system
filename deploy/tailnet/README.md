# 部署说明 · tailnet 服务器 2407-70

本目录是「智慧实验室预约管理系统」在 tailnet 服务器上的部署件。
部署约束详见 `C:\Users\Nebula\.agents\SERVER.md`（无 sudo、只绑 tailnet 地址、用户级安装）。

## 访问入口

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| 前端（生产静态站） | http://100.96.0.100:8092 | Vue3 构建产物 + SPA 回退 |
| 后端接口 | http://100.96.0.100:8091/api | Spring Boot，同时是前端跨域目标 |
| Redis | 127.0.0.1:6379 | 仅回环，不对外 |
| MySQL | 100.96.0.100:3306 | 队友提供，库 `lab_reservation` |

前端跨域：`request.js` 的 baseURL 取构建期 `VITE_API_BASE`（见 `frontend/.env.production`），
后端 `SecurityConfig` 已放开 CORS。

## v1.1 新增能力（本次已部署）

| 模块 | 前端入口 | 后端接口前缀 | 权限码 |
| --- | --- | --- | --- |
| 预约签到核销闭环 | `/checkin/mine`、`/checkin/verify` | `/api/checkin` | `checkin:mine` / `checkin:verify` / `checkin:list` |
| 站内消息通知中心 | `/notice` | `/api/notice` | `notice:mine` / `notice:list` / `notice:send` |
| 实验室实时占用看板 | `/board` | `/api/stats/realtime-board`、`/api/stats/board-summary` | `lab:board:view` |
| 个人中心 | `/profile` | `/api/profile` | 登录即可 |

新增两张表：`lab_checkin`（签到核销单）、`sys_notice`（站内消息）。
两者实体均继承 `BaseEntity`，**必须含 `deleted / create_time / update_time` 三列**，否则逻辑删除会 500。

### 数据库增量升级（已初始化过 v1.0.0 的库）

```powershell
# 服务器无 mysql 客户端，用 JDBC 执行（连接参数取自服务器 .env，密码不出服务器）
java -cp /tmp/labops-classes:/tmp/mysql-connector.jar SqlRunner /tmp/upgrade_v1.1.sql
```

脚本 `sql/upgrade_v1.1.sql` 幂等可重复执行：建表 + `INSERT IGNORE` 权限点与角色授权。
全新建库仍用 `sql/mysql.sql`（已同步包含新表与新权限）。

> 升级后必须清一次登录缓存，否则老账号仍持有旧的权限集合，菜单里的新条目不会出现：
> ```bash
> ~/opt/redis/bin/redis-cli -h 127.0.0.1 -p 6379 FLUSHALL
> ```
> （Redis 无密码，`-a` 会报 AUTH 失败但仍可执行；本环境只有本应用在用该实例。）

## v1.2 新增能力（本次已部署）

认证侧改造：登录页移除演示账号提示，新增双登录方式与自助开户链路。

| 能力 | 前端入口 | 后端接口 | 说明 |
| --- | --- | --- | --- |
| 密码登录 | `/login` | `POST /api/auth/login` | 用户名 + 密码（原有能力） |
| 验证码登录 | `/login` | `POST /api/auth/captcha`、`POST /api/auth/login-by-code` | 账号可为**用户名或手机号** |
| 自助注册 | `/register` | `POST /api/auth/register` | 默认赋予「学生」角色，账号为**待审核** |
| 忘记密码 | `/forgot` | `POST /api/auth/reset-password` | 手机验证码校验后重置 |
| 注册审核 | `/system/user` | `PUT /api/user/{id}/approve` | 列表内「通过审核」按钮 |

### 验证码机制（重要）

- 6 位数字随机码存 Redis：`captcha:{login|register|reset}:{标识}`，TTL 5 分钟；另有 60 秒节流键
  `captcha:cooldown:{场景}:{标识}`，60 秒内重复获取返回 `40106`
- 场景隔离：登录 / 注册 / 找回密码的验证码互不通用
- 标识约定：登录与找回密码存**解析后的 userId**（因此"用用户名取码、用手机号登录"同样成立），注册存**手机号**
- 校验为「先比对、再删除，并以删除结果作为通过闸门」，一次性消费，防止并发复用
- **验证码直接随响应返回给前端展示**（`CaptchaVO.code`）——这是**演示环境**行为：系统未接入短信通道，
  为便于演示才回传。生产环境必须改为短信下发，绝不返回验证码本身

> `AuthServiceImpl` / `CaptchaServiceImpl` 中已就此写了醒目注释；前端通知也会提示"演示环境未接入短信通道"。

### 账号状态扩展为三态

`sys_user.status`：`0` 禁用 / `1` 启用 / `2` **待审核**（自助注册初始值）。

- 两种登录方式都必须在**签发令牌之前**校验状态，否则待审核账号会"令牌签发成功但每个请求都被过滤器拒绝"，
  前端会反复弹"登录已失效"
- 管理端「启用/禁用」「审核通过」「重置密码」后都会**清除 `auth:user:{id}` 缓存**，使变更即时生效
- 前端用户管理页：待审核行显示「待审核」标签（不再用开关表达），操作列出现「通过审核」按钮；
  编辑弹窗内待审核状态只读，避免被误改成启用/禁用

### 数据库增量升级

```powershell
# 执行前先确认无重复手机号（唯一索引会因此失败）
#   SELECT phone, COUNT(*) c FROM sys_user WHERE phone IS NOT NULL GROUP BY phone HAVING c > 1;
set -a; . ~/lab-reservation/.env; set +a
~/opt/jre25/bin/java -cp /tmp/labops-classes:/tmp/mysql-connector.jar SqlRunner /tmp/upgrade_v1.2.sql
```

`sql/upgrade_v1.2.sql` 幂等：扩展 `status` 列注释 + 用 `information_schema` + 动态 SQL 条件添加
`uk_phone`（MySQL 8 无 `CREATE INDEX IF NOT EXISTS`）。全新建库仍用 `sql/mysql.sql`（已同步）。

### 主题应用时机（易踩坑）

`src/utils/theme.js` 的副作用（把已保存/系统主题写到 `<html class="dark">`）必须在**入口 `main.js`** 引入。
若只在 `layout/index.vue` 里引入，则登录 / 注册 / 找回密码这些不套用后台布局的整页在**直接打开或刷新**时
不会应用深色模式（SPA 内跳转因为模块已加载所以看不出问题，极易漏测）。

## v1.3 修复（学生端看不到数据驾驶舱）

**现象**：以学生角色登录后左侧菜单没有「数据驾驶舱」，且默认落地页 `/dashboard` 会调用
`/api/stats/overview` 返回 `40300 无权限访问该资源`，页面无数据。

**根因**：菜单项 `/dashboard` 按 `statistics:view` 权限过滤，而种子数据里该权限（`1601`）只授予了
角色 1/2/3，**唯独漏了学生（role 4）**；更关键的是 `router` 的 `redirect: '/dashboard'` 与登录后跳转
都是写死的，学生会被送到自己无权访问的页面。

**修复**：
1. 为学生角色补授 `statistics:view`（`sql/mysql.sql` 同步，线上库执行 `sql/upgrade_v1.3.sql`），
   驾驶舱面向**所有登录用户**可见——学生看校园实验室运行概览是合理的
2. 执行后需清一次登录缓存（`FLUSHALL`）让权限立即生效；**清缓存不会踢下线**，
   `JwtAuthenticationFilter` 缓存未命中时会回源数据库重建
3. 驾驶舱内的「待办工作台」按权限过滤条目：学生只看到「待处理报修」与「今日预约」，
   且「今日预约」的落地页按可访问性回退到「我的签到」——避免点出 403 空页

> 教训：**给角色授权后必须同步前端菜单可见性所依赖的权限**；另外任何"按权限过滤菜单"的页面，
> 其**默认落地路由也必须与权限对齐**，否则会出现"菜单里没有、但一登录就站在这个页面上"的错位。

### 驾驶舱内容按角色裁剪（纯前端）

驾驶舱对全体登录用户开放，但其中管理侧数据对学生无意义且会暴露系统内部信息，故在前端按权限隐藏：

- `dashboard/index.vue` 用 `isOperator`（`reservation:approve` 或 `system:user:list` 任一命中）判定运维视角
- **学生端**：指标卡 4 张（实验室总数 / 设备总数 / 预约总数 / 今日预约）、图表 3 个（近 7 日趋势 / 热门实验室 / 实验室类型）；
  隐藏「用户总数」「待审核预约」「待处理报修」「故障设备」四张卡、「预约状态分布」饼图，以及整个「待办工作台」面板
- **管理/教师端**：维持原样（8 张卡 + 4 个图表 + 待办面板）
- 实现方式为**前端过滤**，后端 `/stats/overview` 仍返回全量字段（管理端需要），无需拆接口
- 布局适配：`mid-grid` 在无待办面板时加 `.no-todo` 让图表独占整行；`chart-stack` 改用
  `repeat(auto-fit, minmax(320px, 1fr))`，只剩一张图时自动铺满

> 排错提示：浏览器自动化工具常开启 "Preserve log"，**换角色后读控制台会把上一个角色的历史报错一起读出来**，
> 容易误判成新问题。复核权限相关报错时应**新开标签页**再读控制台。

## v1.4 修复（学生端打开报修闭环报「无权限访问该资源」）

**现象**：学生打开 `/device/repair` 直接提示 `40300 无权限访问该资源`。

**根因**：菜单项按 `device:repair:submit` **或** `device:repair:list` 任一命中即可见，学生有 `submit` 所以菜单能看到；
但页面内部整套复用了管理端接口：

| 页面动作 | 原调用接口 | 所需权限 | 学生是否具备 |
| --- | --- | --- | --- |
| 进入页面即加载列表 | `GET /repair/page` | `device:repair:list` | 否 → 一打开就 40300 |
| 提交报修的设备下拉 | `GET /device/page` | `system:device:list` | 否 → 点开弹窗再 40300 |
| 开始维修 / 完成维修 | `/repair/{id}/start|complete` | `device:repair:handle` | 否 → 潜在第三次 40300 |

**修复**（未给学生开放设备台账，坚持最小权限）：

1. 新增 `GET /api/repair/device-options`：只回传 `id / deviceCode / deviceName / labId / labName / faultStatus`，
   不含价格、购置日期、品牌型号等资产字段；权限为 `hasAnyAuthority('device:repair:submit','device:repair:handle','device:repair:list')`
   （学生/实验室管理员/超管可访问，教师本就无报修职责故拒绝）
2. `repair.vue` 按权限自适应：
   - 有 `device:repair:list` → 仍看**全部工单**（标题「报修工单」）、有维修动作
   - 只有 `device:repair:submit` → 改查 `/repair/mine`，标题变「我的报修」，隐藏维修动作，仅保留「取消」
   - 「提交报修」按钮按 `device:repair:submit` 显隐（顺带修掉实验室管理员点了会 403 的隐患）
3. 设备下拉改用上述精简接口，普通用户无需台账权限即可提交报修

> 通用教训：**菜单可见性只要"任一权限命中"就会露出入口，但页面内部的每个接口都要各自满足权限**。
> 新增/改造带权限的页面时，要逐个核对 onMounted 与各按钮实际调用的接口所需权限，别只看菜单那一个权限码。

## v1.5 全站权限—页面一致性审计

**用户规则**：管理员权限归管理员页面，用户权限归用户页面 —— **凡是当前角色没有权限的元素，就不该出现在页面上**
（不能"先渲染、点了才报 40300"）。

**做法**：这次不再逐个被动修，而是做了一次系统化审计 ——
对 STUDENT / TEACHER / LAB_ADMIN / SUPER_ADMIN 四个角色，逐个实测「该角色能看到的每个页面的 onMounted 接口与各操作按钮接口」，
与后端 `@PreAuthorize` 所需权限比对，输出「角色 × 页面 × 元素 × 接口 × 实测返回码」矩阵。

**审计发现并修复的 6 处**：

| 角色 | 页面 | 元素/动作 | 接口 | 所需权限 | 修法 |
| --- | --- | --- | --- | --- | --- |
| 教师 | `/reservation/list` | 导出 Excel | `/reservation/export` | `reservation:export` | ① `v-if` 隐藏 |
| 实验室管理员 | `/lab/info` | 行内删除 | `DELETE /lab/{id}` | `system:lab:delete` | ① `v-if` 隐藏 |
| 实验室管理员 | `/lab/info` | 负责人下拉 | `/user/page` | `system:user:list` | ② 改调 `/user/options` |
| 实验室管理员 | `/device/repair` | 取消报修 | `/repair/{id}/cancel` | `device:repair:submit` | ① 守卫改 `canSubmit` |
| 教师/实验室管理员 | `/notice` | 接收人下拉 | `/user/page` | `system:user:list` | ② 改调 `/user/options` |
| 教师/实验室管理员 | `/profile` | 发起预约 / 我的签到 | 目标页接口 | `reservation:submit` / `checkin:mine` | ① `v-if` 隐藏 |

**新增精简接口**（避免为一个下拉框去要求「用户管理」权限）：

- `GET /api/user/options` → `id / username / realName`，仅启用用户，
  权限 `hasAnyAuthority('system:user:list','system:lab:add','system:lab:edit','notice:send')`
  （注意：**教师实际并没有 `notice:send`**，所以教师被拒是符合预期的；教师看到的 `/notice` 页也不会有「发送消息」按钮）

**核对结论**：修复后教师、实验室管理员两个角色的页面均无「显示了但用不了」的元素，控制台无报错、无 40300；
后端仍保持最小权限（被隐藏的接口对该角色继续返回 40300，只是 UI 不再暴露）。

> 判定技巧：本项目业务错误统一是 **HTTP 200 + body.code≠200**，所以用 curl/Invoke-RestMethod 校验权限时
> **必须读 body.code**，不能只看 HTTP 状态码 —— 否则会把"被拒绝"误读成"通过"。

## v1.6 AI 助手（流式对话 + 系统数据上下文）

**能力**：左侧菜单「AI 助手」整页聊天。助手会按提问自动带上**当前用户有权看到**的系统数据
（实验室实时占用、我的预约、我的签到、故障设备、我的报修），因此能回答「今天哪些实验室空闲」
「我的预约什么状态」等问题，同时也能答通用问题。输出为**流式打字机**效果，会话历史缓存在 Redis。

### 令牌处理（重要）

- 模型：`MiniMax-M2.5`（默认，可用 `AI_MODEL` 覆盖）
- 协议：OpenAI 兼容，base url 默认 `https://zhenze-guangzhou8.cmecloud.cn/api/coding/v1`
  （chat 路径 = `{base-url}/chat/completions`）
- **令牌只写在服务器 `~/lab-reservation/.env` 的 `AI_TOKEN=`**（该文件已 600 权限，
  systemd 单元用 `EnvironmentFile=` 注入）。**仓库源码与前端构建产物中均不含该令牌**
  （已用 grep 校验为 0 命中）。前端只调本系统 `/api/ai/*`，浏览器拿不到令牌。
- 未配置 `AI_TOKEN` 时接口返回 `50500 AI 助手未配置`，属正常降级，不会 500。

### 接口

| 端点 | 说明 |
| --- | --- |
| `POST /api/ai/chat` | `text/event-stream`，事件名 `delta`（正文增量）/ `done` / `error` |
| `GET /api/ai/history` | 回显最近会话（Redis List，24h） |
| `DELETE /api/ai/history` | 清空会话 |

均 `@PreAuthorize("isAuthenticated()")`。限流 20 次/分钟；同一用户同时只允许一条流（Redis 占用标记）。

### 关键实现要点（踩过的坑）

1. **异步派发必须放行，否则流式请求必定报错**（本次实测发现的真实缺陷）：
   `SseEmitter` 结束时会触发一次 **ASYNC 二次派发**，该派发**同样会经过 Spring Security 的过滤器链**，
   但此时 `SecurityContext` 已随原请求线程结束而清空，`AuthorizationFilter` 判为匿名访问 → 抛
   `AuthorizationDeniedException` → 容器想在已提交的响应上渲染错误页 → 连接不带终止块就被关闭，
   浏览器侧表现为 `net::ERR_INCOMPLETE_CHUNKED_ENCODING`（**即使是正常完成的对话也会出现**）。
   修法：`http.authorizeHttpRequests(a -> a.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll() ...)`
   —— 首次派发已完成认证与 `@PreAuthorize` 校验，放行二次派发不会绕过鉴权。
2. **`userId` 必须在请求线程内取出**再传入工作线程：`SecurityContext` 不跨线程。
3. **`spring.mvc.async.request-timeout: 120s`**：Tomcat 异步超时默认 30s，会把长回答硬掐断；
   `SseEmitter` 自身超时（100s）要小于它。
4. **`response.setBufferSize(1)`**：Tomcat 响应缓冲默认 8KB，逐段增量会被攒批后一次性蹦出，
   打字机效果就没了；也不要开 `server.compression`。
5. **不设 `HttpRequest` 级 timeout**：JDK 该超时覆盖整个交换（含 body），会中途截断长流；
   只用 `connectTimeout` + 业务层看门狗。
6. **只转发 `delta.content`**：该网关会先流式输出 `delta.reasoning`（模型思考过程），
   必须跳过，否则用户会看到模型的内部推理。
7. 转发走**专用线程池**（`aiExecutor`），不用 `@Async`：长流会打满默认的 applicationTaskExecutor。
8. 会话历史用 **Redis List**（`ai:chat:{userId}`，push + trim + expire），
   而非单键 JSON 读改写，避免同用户并发丢消息。

### 前端

- `views/ai/index.vue` 聊天页；`utils/sse.js` 用 `fetch` + `ReadableStream` 手写 SSE 解析
  （**EventSource 无法自定义 Authorization 头**）。要点：chunk 边界会切断帧，需缓冲区拼接并保留不完整尾段；
  用 `TextDecoder({ stream: true })` 处理被切断的多字节字符；非流式响应（50500/403）要按统一 Result JSON 分支提示。
- 支持「停止生成」（AbortController）与「清空会话」。

### Markdown 渲染（回答美观度）

模型回答是 Markdown（表格、加粗、列表、代码块），必须渲染成富文本，否则 `|`、`**`、`#` 会原样显示。
为此新增两个前端依赖：`marked`（解析）+ `dompurify`（净化）。

> **安全要点（务必保留净化这一步）**：marked 18 已经**移除 `html` 选项**，
> 实测 `{ html: false }` 无效 —— 原始 HTML 会**原样透传**（`<script>`、`<img onerror>` 都能穿过），
> 因此 `v-html` 前必须用 DOMPurify 净化，否则模型输出（可能被提示注入影响）里的脚本会在浏览器执行。
> 净化后再把外链补上 `target="_blank" rel="noopener noreferrer"`。

排版样式写在 `views/ai/index.vue` 里（scoped + `:deep()`，因为 v-html 内容不在组件作用域内）：
标题/段落/列表/引用/分隔线、行内 `code` 与 `pre` 代码块（等宽 + 底色 + 横向滚动）、
表格（圆角描边 + 表头底色 + 斑马纹，颜色全部走语义变量以适配深色模式）；
窄屏（≤640px）下表格改为 `display:block; overflow-x:auto` 横向滚动，避免被挤压变形。
用户自己的消息气泡保持纯文本 `pre-wrap`（不需要渲染 Markdown）。

## UI 能力

- **深色模式**：顶栏月亮/太阳图标切换，取值为 `light/dark` 存于 `localStorage.labwise_theme`，
  首屏跟随系统 `prefers-color-scheme`；配色集中在 `frontend/src/styles/index.scss` 的 `html.dark` 变量块，
  图表（ECharts）通过 `utils/theme.js` 暴露的 `isDark` 重建配色。
- **卡片表面色一律走语义变量**（`--card-bg` / `--card-bg-hover` / `--card-bg-soft` / `--surface-btn` / `--hairline`），
  禁止在组件里写死 `rgba(255,255,255,0.3x)` —— 这类硬编码在深色下会泛白，且散落各处难以排查。
  Element Plus 的 `effect="light"` 标签、骨架屏、`el-message`/`el-notification` 等浅色默认值也已在 `html.dark` 内覆盖。
- **响应式断点**：`1180px`（卡片降列）/ `1024px`（侧栏折叠为图标）/ `768px`（侧栏隐藏、改用抽屉导航）
  / `640px`（弹窗 92% 宽、工具条纵向铺满、分页精简）/ `600px`（弹窗表单单列化 `.form-responsive`）。
- 首次加载用 `el-skeleton` 骨架屏，空数据用 `el-empty`，列表页统一 `.table-scroll` 横向滚动容器。

### 重新部署前端时必须整体替换 dist

`serve_dist.py` 已按「入口 HTML 不缓存 / `assets/` 内容哈希强缓存」下发缓存头。
但只要服务器上残留上一次的旧哈希文件，浏览器就可能继续命中旧资源，
出现「改了样式却看不到」的假象。因此上传时**先删再解包**（不要直接覆盖叠加）：

```bash
ssh tailnet-server 'rm -rf ~/lab-reservation/dist && tar -xzf /tmp/lab-dist.tgz -C ~/lab-reservation && systemctl --user restart lab-frontend'
```



## 服务器侧目录

```
~/lab-reservation/
├── app.jar          后端可执行 jar
├── dist/            前端构建产物
├── deploy/          serve_dist.py + 单元文件副本
├── redis.conf       用户级 Redis 配置
├── .env             DB/Redis/JWT 等敏感配置（chmod 600）
├── logs/            app.log / frontend.log / redis.log
└── data/redis/      Redis 持久化数据
~/opt/jre25/         Temurin JRE 25（jar 字节码为 Java 24，JRE21 无法运行）
~/opt/redis/         源码编译的 Redis 8.2.1
```

三个 systemd 用户服务：`lab-redis` / `lab-backend` / `lab-frontend`，已 `enable`（Linger=yes，开机自启）。

## 本地构建 → 上传 → 重启

后端（本机 `JAVA_HOME` 必须指向 JDK 25，否则 release 24 编译失败）：

```powershell
$env:JAVA_HOME="D:\JAVA\jdk-25.0.2"
mvn -q -DskipTests package
```

前端（产物 baseURL 由 `.env.production` 注入）：

```powershell
cd frontend; npm run build
```

上传并重启：

```powershell
$tmp=$env:TEMP
tar -czf "$tmp\lab-dist.tgz" -C frontend dist
scp -q "$tmp\lab-dist.tgz" tailnet-server:/tmp/
scp -q "target\lab-reservation-system.jar" tailnet-server:/tmp/lab-app.jar
ssh tailnet-server 'tar -xzf /tmp/lab-dist.tgz -C ~/lab-reservation && mv /tmp/lab-app.jar ~/lab-reservation/app.jar && rm -f /tmp/lab-dist.tgz && systemctl --user restart lab-backend lab-frontend && sleep 8 && systemctl --user is-active lab-backend lab-frontend'
```

## 常用运维

```bash
ssh tailnet-server 'systemctl --user status lab-backend --no-pager'
ssh tailnet-server 'tail -n 50 ~/lab-reservation/logs/app.log'
ssh tailnet-server 'systemctl --user restart lab-backend'
```

## 数据库初始化

`sql/mysql.sql` 建库建表并写入种子数据（账号 admin/labadmin/teacher/student，密码 123456）。

> 该脚本本次部署时修复了三处缺陷：①结尾一个孤立 `;` 导致语法错误；②种子密码哈希并非
> `123456` 的 BCrypt 值（已替换为校验通过的哈希）；③`sys_permission` 表缺少
> `deleted / create_time / update_time` 三列，而该表实体继承 `BaseEntity`（含逻辑删除字段），
> 会直接导致权限相关接口报 500（`Unknown column 'deleted'`）。若既往曾用旧脚本初始化，需重新导入。

导入方式（服务器无 mysql 客户端，用 JDBC 执行）：

```powershell
java -cp <mysql-connector-j.jar> ImportSql.java 100.96.0.100 root <密码> sql\mysql.sql
```
