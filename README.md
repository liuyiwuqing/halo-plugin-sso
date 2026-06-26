# 统一身份认证

为多个 Halo 站点提供中心身份站、单点登录、用户绑定和角色映射能力。

## 简介

该插件采用“中心身份站 + 多个接入站”的架构：

- 身份中心模式：统一注册登录，管理接入站，签发授权码和用户身份信息。
- 接入站模式：跳转中心站登录，处理回调，自动绑定或创建本地用户，并执行角色映射。

第一版优先实现 Authorization Code + PKCE 登录闭环，不做密码同步、共享 Cookie、实时全站踢下线和完整标准 OIDC Provider 承诺。

## 当前接口

中心站 Console API：

- `GET /apis/console.sso.muyin.site/v1alpha1/clients/list`
- `POST /apis/console.sso.muyin.site/v1alpha1/clients/create`：创建后返回系统生成的 `clientId` 和 `clientSecret`。
- `POST /apis/console.sso.muyin.site/v1alpha1/clients/update`
- `DELETE /apis/console.sso.muyin.site/v1alpha1/clients/{clientId}`
- `GET /apis/console.sso.muyin.site/v1alpha1/role-mappings/list`
- `POST /apis/console.sso.muyin.site/v1alpha1/role-mappings/create`
- `POST /apis/console.sso.muyin.site/v1alpha1/role-mappings/update`
- `GET /apis/console.sso.muyin.site/v1alpha1/user-bindings/list`
- `GET /apis/console.sso.muyin.site/v1alpha1/audit-logs/list`：支持 `outcome`、`clientId`、`keyword`、`page`、`size` 查询参数。
- `GET /apis/console.sso.muyin.site/v1alpha1/audit-logs/recent-failures`：按失败原因聚合最近登录失败记录。
- `POST /apis/console.sso.muyin.site/v1alpha1/audit-logs/cleanup`：按保留天数预览或清理过期审计日志，支持 `dryRun`。
- `GET /apis/console.sso.muyin.site/v1alpha1/audit-logs/cleanup-status`：获取最近一次审计日志清理状态。
- `GET /apis/console.sso.muyin.site/v1alpha1/audit-logs/cleanup-records`：获取最近的审计日志清理历史记录，支持 `limit` 查询参数。

中心站 OAuth API：

- `GET /apis/public.sso.muyin.site/v1alpha1/oauth/authorize`
- `POST /apis/public.sso.muyin.site/v1alpha1/oauth/token`
- `GET /apis/public.sso.muyin.site/v1alpha1/oauth/userinfo`

公共 API：

- `GET /apis/public.sso.muyin.site/v1alpha1/client/login`：生成 state / PKCE 并跳转到中心站授权页。
- `GET /apis/public.sso.muyin.site/v1alpha1/client/callback`：校验 state、换取 Token、拉取 userinfo、绑定/创建本地用户、映射角色、建立 Halo 登录态，并跳回 `return_url`。
- `GET /apis/public.sso.muyin.site/v1alpha1/roles/list`：获取中心身份站角色列表，返回角色名、展示名、模块和隐藏标记。

当前接入站登录流程已经覆盖：

- 接入站模式下可配置是否自动拦截站点普通 `/login` 并跳转到 SSO 登录入口；开启时主题登录按钮无需逐个改模板，如需本地登录可访问 `/login?sso_local=1`。
- 插件声明 `AuthProvider` 手动登录入口；关闭自动跳转并在 Halo 认证提供者中启用 `muyin-sso` 后，接入站可在默认登录页手动选择“统一身份认证”发起 SSO 登录。
- 身份中心收到未登录用户的授权请求时，会跳转到中心站 `/login?redirect_uri=...`，中心站登录完成后回到授权端点继续签发授权码。
- 中心站角色随 `userinfo.roles` 下发，并按“中心标准角色”设置过滤；该配置仅在身份中心模式展示，角色选项来自当前中心站的真实 Halo 角色列表，未配置时默认只下发 `subscriber`、`author`、`editor`。
- 接入站按 `SsoRoleMapping` 映射中心角色，未命中时使用接入站默认角色。
- Console 页面支持创建、编辑、启停接入站，并对常见接入配置问题给出提示。
- Console 页面支持管理角色映射，角色映射以中心角色为稳定键。
- Console 页面支持查看用户绑定和接入站登录审计日志。
- 审计日志支持按结果、Client ID、关键词筛选和分页查看，并展示最近失败原因聚合。
- 审计日志支持按保留天数执行干跑预览和手动清理，避免日志长期堆积影响 Console 使用。
- 插件设置提供审计日志自动清理开关，默认关闭；开启后后台任务每 6 小时按保留天数清理一次。
- Console 审计面板会展示最近一次清理来源、结果、完成时间、删除数量或失败原因，并展示最近几次持久化清理历史。
- 首次登录自动创建本地 Halo 用户并记录 `SsoUserBinding`。
- 接入站回调会记录登录成功/失败审计，审计写入失败不会阻断登录。
- 已有本地用户登录时同步资料并追加缺失角色，不会清空用户已有本地角色。

## 开发环境

- Java 21+
- Node.js 18+
- pnpm

## 开发

```bash
# 启用插件
./gradlew haloServer
# 开发前端
cd ui
pnpm install
pnpm dev
```

## 构建

```bash
./gradlew build
```

构建完成后，可以在 `build/libs` 目录找到插件 jar 文件。

## 许可证

[GPL-3.0](./LICENSE) © Lywq 
