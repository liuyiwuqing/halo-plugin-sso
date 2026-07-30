# 统一身份认证

为多个 Halo 站点提供“身份中心 + 接入站”的单点登录能力。一个站点作为身份中心统一承接登录与授权，其它 Halo 站点作为接入站跳转到身份中心完成认证，并在回调后建立本站登录态、绑定本地用户、同步用户资料和映射本地角色。

## 插件介绍

`halo-plugin-sso` 适合一组 Halo 站点共用同一套账号体系的场景，例如主站、文档站、社区站、资源站分别部署，但希望用户只使用一个中心账号登录。

插件采用 Authorization Code + PKCE 的登录闭环：

1. 接入站发起登录。
2. 用户被跳转到身份中心授权端点。
3. 身份中心确认用户登录状态和授权条件。
4. 身份中心向接入站回调授权码。
5. 接入站使用授权码换取 Token，拉取用户身份信息。
6. 接入站绑定或创建本地 Halo 用户，映射角色并建立本地登录态。

当前能力包括：

- 身份中心模式：管理接入站、签发授权码、提供 Token 和 UserInfo、下发中心标准角色。
- 接入站模式：跳转身份中心登录、处理回调、绑定或创建本地用户、同步用户资料、映射本地角色。
- 登录入口控制：接入站可选择自动跳转 SSO 登录，也可保留 Halo 默认登录页并手动选择“统一身份认证”。
- 动态认证提供者信息：接入站登录页展示的 `displayName`、`description`、`logo`、`website` 会从身份中心站点基础信息同步。
- 角色映射：身份中心下发中心角色，接入站按映射规则转换为本站本地角色。
- 审计日志：记录接入站登录成功、失败、失败原因，并支持筛选、聚合、手动清理和自动清理。
- 用户绑定：记录中心账号与接入站本地用户的绑定关系。

## 适用环境

- Halo `>= 2.25.0`
- Java 21+
- 一个可被接入站访问的身份中心站点地址，例如 `https://auth.example.com`
- 接入站需要有稳定外部访问地址，例如 `https://blog.example.com`

生产环境建议使用 HTTPS。开发调试时可在插件设置中临时开启“开发环境允许 HTTP localhost”，但别把这个当生产方案，真上生产这么干就有点硬莽了。

### 反向代理安全边界

Halo 2.25.x 默认使用原生转发头处理。插件使用服务端暴露的客户端地址限制单一请求来源创建的未完成登录状态；OAuth 回调地址和同源判断则依赖代理提供的外部协议与主机信息。生产部署必须满足：

- Halo 服务端口只允许可信反向代理访问，不要把源站端口同时暴露到公网。
- 最外层代理必须丢弃并重写客户端传入的 `Forwarded`、`X-Forwarded-For`、`X-Forwarded-Host` 和 `X-Forwarded-Proto`。
- 多级代理只能在明确的可信代理链内传递来源地址；不要让任意客户端自行声明来源 IP。

插件不会自行解析原始 `X-Forwarded-For` 作为限额键，但会使用转发的协议和主机信息识别站点外部地址。可信代理边界应由 Halo 和部署层统一建立，否则攻击者既能伪造来源绕过限额，也可能污染站点外部地址判断。

## 快速开始

完整接入至少需要两个 Halo 站点：

- 站点 A：身份中心。
- 站点 B：接入站。

下面用：

- 身份中心：`https://auth.example.com`
- 接入站：`https://blog.example.com`

### 1. 安装插件

在身份中心站点和每个接入站点都安装并启用本插件。

构建插件：

```bash
./gradlew build
```

构建完成后，插件 JAR 位于：

```text
build/libs/
```

将 JAR 上传到 Halo 后台插件管理并启用。

### 2. 配置身份中心

进入身份中心站点后台：

```text
控制台 -> 插件 -> 统一身份认证 -> 设置
```

在“基础设置”中配置：

| 配置项 | 建议值 | 说明 |
| --- | --- | --- |
| 运行模式 | 身份中心模式 | 当前站点负责统一登录和授权 |
| 中心标准角色 | `subscriber`、`author`、`editor` 等 | 只有选中的中心角色会下发给接入站 |
| 要求邮箱已验证 | 开启 | 未验证邮箱的中心用户不允许跨站登录 |

然后进入插件控制台页面：

```text
控制台 -> 系统工具 -> 统一身份认证
```

在“接入站”区域创建一个接入站：

| 字段 | 示例 | 说明 |
| --- | --- | --- |
| 接入站名称 | 我的博客 | 控制台展示用 |
| 主站地址 | `https://blog.example.com` | 用于展示和生成默认回调的主要外部访问地址 |
| Redirect URI | `https://blog.example.com/apis/public.sso.muyin.site/v1alpha1/client/callback` | 精确回调白名单；同一站点有多个域名时逐行登记 |
| 启用状态 | 开启 | 关闭后该接入站不能继续登录 |

同一个 Halo 接入站可以让多个域名共用一组 `Client ID` 和 `Client Secret`。例如
`https://blog.muyin.site` 与 `https://lywq.muyin.site` 指向同一站点时，在同一个接入站中登记：

```text
https://blog.muyin.site/apis/public.sso.muyin.site/v1alpha1/client/callback
https://lywq.muyin.site/apis/public.sso.muyin.site/v1alpha1/client/callback
```

每个地址仍按协议、域名、端口和路径精确匹配；不要使用通配符。

创建成功后会得到：

- `Client ID`
- `Client Secret`

`Client Secret` 只展示一次，复制保存好。这个东西丢了就重新生成或重建接入站，别指望页面再掏出来给你看。

### 3. 配置接入站

进入接入站后台：

```text
控制台 -> 插件 -> 统一身份认证 -> 设置
```

在“基础设置”中配置：

| 配置项 | 示例 | 说明 |
| --- | --- | --- |
| 运行模式 | 接入站模式 | 当前站点负责跳转登录和建立本地登录态 |
| 身份中心地址 | `https://auth.example.com` | 身份中心站点外部地址 |
| Client ID | 从身份中心复制 | 对应身份中心创建的接入站 |
| Client Secret | 从身份中心复制 | 服务端换取 Token 使用 |
| 默认本地角色 | `guest` 或其它低权限角色 | 未命中角色映射时使用 |
| 登录时同步用户资料 | 按需开启 | 同步邮箱、用户名、展示名称和头像 |
| 自动跳转 SSO 登录 | 按需开启 | 开启后访问 `/login` 自动跳转身份中心 |

接入站保存配置后，插件会定期从身份中心同步认证提供者信息。同步来源是身份中心 Halo 系统基础设置：

| AuthProvider 字段 | 身份中心来源 |
| --- | --- |
| `displayName` | 站点标题 |
| `description` | 站点副标题 |
| `logo` | 站点 Logo，未配置时使用 favicon，再未配置时使用插件默认图标 |
| `website` | 站点外部访问地址 |

如果身份中心临时不可用，接入站会保留上一次同步成功的本地认证提供者信息，不会把登录入口改成空壳。

### 4. 配置角色映射

进入接入站插件控制台：

```text
控制台 -> 系统工具 -> 统一身份认证 -> 角色映射
```

创建映射关系：

| 中心角色 | 本地角色 | 说明 |
| --- | --- | --- |
| `subscriber` | `guest` | 普通用户 |
| `author` | `contributor` | 投稿或作者 |
| `editor` | `editor` | 编辑 |

规则说明：

- 身份中心只下发“中心标准角色”中允许的角色。
- 接入站只按启用状态的映射规则转换角色。
- 未命中映射时使用“默认本地角色”。
- 已有本地用户登录时，插件会追加缺失角色，不会清空用户已有本地角色。

## 登录教程

### 自动 SSO 登录

适合希望接入站完全交给身份中心登录的场景。

1. 在接入站插件设置中开启“自动跳转 SSO 登录”。
2. 用户访问接入站 `/login`。
3. 插件自动跳转到身份中心授权页。
4. 用户在身份中心登录。
5. 登录成功后回到接入站，并建立接入站本地登录态。

如果需要临时访问接入站本地登录页，可以访问：

```text
/login?sso_local=1
```

### 手动选择 SSO 登录

适合接入站需要同时保留本地登录和 SSO 登录的场景。

1. 在接入站插件设置中关闭“自动跳转 SSO 登录”。
2. 到 Halo 的认证提供者配置中启用 `muyin-sso`。
3. 用户访问接入站 `/login`。
4. 用户在登录页选择“统一身份认证”。
5. 插件跳转身份中心完成登录。

手动入口由插件声明的 `AuthProvider` 提供：

```text
metadata.name: muyin-sso
authenticationUrl: /apis/public.sso.muyin.site/v1alpha1/client/login
```

### 指定登录后返回地址

接入站登录入口支持 `return_url` 参数：

```text
/apis/public.sso.muyin.site/v1alpha1/client/login?return_url=/archives/demo
```

登录完成后会回到指定地址。插件会校验返回地址，只允许本站相对路径，避免被拿去做开放重定向。这个地方不校验就等于给钓鱼链接递刀，不能省。

## 运行时临时状态与稳定性保护

插件会在内存中短暂保存 OAuth 授权码和接入站发起登录时的 `state` 会话，这两类数据都只用于一次登录闭环，不作为长期状态保存。

- 授权码有效期为 5 分钟，`/oauth/token` 成功消费后立即从内存删除；过期授权码会在签发新授权码或消费授权码时清理。
- 未完成登录会话有效期为 10 分钟，接入站回调消费 `state` 后立即从内存删除；过期会话按不超过 1 分钟的间隔清理。
- 授权码和未完成登录会话的全局最大容量均为 `10000`；未完成登录会话还限制每个请求来源最多保留 `64` 个。达到上限时拒绝继续创建，避免异常流量导致内存持续增长。
- Token 端点最多同时执行 `4` 个 PBKDF2 客户端密钥校验；超限请求不排队并返回 HTTP `429`，避免匿名请求耗尽计算资源。
- 接入站调用身份中心 Metadata、角色、Token 和 UserInfo 接口的总请求超时均为 10 秒。
- 登录回调及控制台角色代理遇到连接失败时返回 HTTP `502`，超时时返回 HTTP `504`；后台元数据同步失败时保留上一次成功的本地认证提供者信息。

审核验证可重点查看：

```bash
./gradlew test --tests site.muyin.sso.oauth.AuthorizationCodeManagerTest --tests site.muyin.sso.clientlogin.ClientLoginSessionManagerTest
./gradlew build
```

## 审计日志与清理

插件会记录接入站登录过程中的关键结果：

- 登录成功。
- 登录失败。
- Client 校验失败。
- Token 换取失败。
- UserInfo 拉取失败。
- 用户创建或绑定失败。
- 角色映射结果。

在插件控制台的“审计日志”中可以：

- 按结果筛选：`success` / `failure`
- 按 Client ID 筛选。
- 按关键词搜索 subject、email 或 message。
- 查看最近失败原因聚合。
- 按保留天数执行干跑预览或真实清理。
- 查看最近一次清理状态和清理历史。

插件设置中的“自动清理过期审计日志”默认开启，后台任务会按保留天数定期清理；如有合规留存要求，可关闭自动清理或调整保留天数。

## 公共接口

接入站和身份中心之间主要使用以下接口。

身份中心 OAuth API：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/apis/public.sso.muyin.site/v1alpha1/oauth/authorize` | OAuth 授权端点 |
| `POST` | `/apis/public.sso.muyin.site/v1alpha1/oauth/token` | 使用授权码换取 Token |
| `GET` | `/apis/public.sso.muyin.site/v1alpha1/oauth/userinfo` | 获取中心用户信息和中心角色 |
| `GET` | `/apis/public.sso.muyin.site/v1alpha1/oauth/notice` | 授权拒绝提示页 |

公共辅助 API：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/apis/public.sso.muyin.site/v1alpha1/metadata` | 获取身份中心认证提供者元信息 |
| `GET` | `/apis/public.sso.muyin.site/v1alpha1/roles/list` | 获取身份中心可下发角色列表 |
| `GET` | `/apis/public.sso.muyin.site/v1alpha1/client/login` | 接入站发起 SSO 登录 |
| `GET` | `/apis/public.sso.muyin.site/v1alpha1/client/callback` | 接入站处理 SSO 回调 |

Console API 主要由插件后台页面调用，覆盖接入站管理、角色映射、用户绑定、审计日志和设置读取。开发调试时可查看：

```text
api-docs/openapi/v3_0/ssoApis.json
```

## 常见问题

### 接入站登录后回调失败

优先检查身份中心中登记的 Redirect URI 是否和接入站实际地址完全一致。协议、域名、端口、路径都要一致。

标准回调路径为：

```text
https://接入站域名/apis/public.sso.muyin.site/v1alpha1/client/callback
```

如果同一接入站有多个访问域名，需要把每个实际域名对应的完整回调地址都登记到同一个接入站；
仅登记主域名不会自动放行其他域名。

### 访问接入站 `/login` 没有跳转身份中心

检查接入站插件设置：

- 运行模式必须是“接入站模式”。
- 身份中心地址、Client ID、Client Secret 必须填写。
- “自动跳转 SSO 登录”必须开启。

如果你关闭了自动跳转，需要在 Halo 认证提供者中启用 `muyin-sso`，然后在默认登录页手动选择“统一身份认证”。

### 手动 SSO 登录入口没有展示

检查 Halo 认证提供者配置中是否启用了 `muyin-sso`。插件声明 `AuthProvider` 不等于 Halo 一定展示它，展示与否由 Halo 的认证提供者启用状态控制。

### 接入站登录页展示的名称和 Logo 不对

接入站会从身份中心 `/metadata` 同步认证提供者信息。请检查：

- 身份中心插件是否为“身份中心模式”。
- 接入站能否访问身份中心地址。
- 身份中心 Halo 系统基础设置中是否配置了标题、副标题、Logo、外部访问地址。
- 身份中心站点外部地址是否正确，尤其是反向代理后的 `https` 地址。

### 用户登录成功但角色不符合预期

检查三处：

1. 身份中心“中心标准角色”是否包含该用户角色。
2. 接入站“角色映射”是否配置并启用。
3. 接入站“默认本地角色”是否符合预期。

### 身份中心退出后接入站没有退出

这是当前设计，不是 bug。插件不做统一退出登录。身份中心退出只影响身份中心本地会话，不会强制接入站本地会话失效。

## 开发

本地开发依赖：

- Java 21+
- Node.js 18+
- pnpm

启动 Halo 开发服务：

```bash
./gradlew haloServer
```

前端开发：

```bash
cd ui
pnpm install
pnpm dev
```

运行测试：

```bash
./gradlew test
```

构建插件：

```bash
./gradlew build
```

生成的插件 JAR 在：

```text
build/libs/
```

## 许可证

[GPL-3.0](./LICENSE) © Lywq
