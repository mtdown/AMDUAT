# AMDUAT

AMDUAT 第一阶段是一个 DeepSeek Harness 配件扫描台。它采用 Java 后端 + Vue 前端的前后端分离架构，只读扫描本机 `F:\deepseek-harness`，在网页中展示已经挂载的 Cordis 插件行和仓库中存在的 DSH package。

## 项目结构

- `backend/`：Spring Boot API 服务，负责读取和归一化 DSH 配件清单。
- `frontend/`：Vue 3 + Vite 单页网页，负责展示扫描概览、筛选表格和配件详情。

## 扫描口径

后端默认扫描 `F:\deepseek-harness`，也可以通过环境变量覆盖：

```powershell
$env:DSH_REPO_ROOT="F:\deepseek-harness"
```

扫描范围：

- `apps\cli\config\agent-presets\*\preset.yml`
- `apps\cli\config\agent-presets\*\agent.cordis.yml`
- `packages\**\package.json`
- `apps\**\package.json`
- `packages\bundle\web-app\cordis.patch.yml`
- `examples\**\*.cordis.yml`

扫描会排除 `.git`、`node_modules`、`dist`、`lib`、`target` 等依赖和构建目录。Cordis 中的 `!!js` 表达式只作为文本展示，不会执行。

## 启动

后端：

```powershell
cd F:\AMDUAT\backend
.\mvnw.cmd spring-boot:run
```

前端：

```powershell
cd F:\AMDUAT\frontend
pnpm install
pnpm dev
```

打开 `http://localhost:5173` 查看配件扫描台。前端开发服务器会把 `/api` 和 `/health` 代理到 `http://localhost:8080`。

## API

- `GET /health`：健康检查。
- `GET /api/scan/summary`：返回 DSH 根目录、是否找到、preset 数、Cordis 行数、package 数、解析失败数和扫描时间。
- `GET /api/accessories`：返回统一配件清单。
- `GET /api/presets`：返回 agent preset 列表。

`/api/accessories` 支持这些筛选参数：`q`、`kind`、`category`、`source`、`enabled`、`presetId`。

## 验证

后端测试：

```powershell
cd F:\AMDUAT\backend
.\mvnw.cmd test
```

前端测试和构建：

```powershell
cd F:\AMDUAT\frontend
pnpm test
pnpm build
```
