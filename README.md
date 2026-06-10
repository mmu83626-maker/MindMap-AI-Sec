# NetScope AI / MindMap AI-Sec

NetScope AI 是一个用于网络安全、网络测量和 Web 应用分析的 AI 工作台。项目包含 Vue 3 前端和 Spring Boot 3 后端，可用于演示 DNS/TCP/TLS/HTTP 测量、安全响应头检查、Web 请求监听、站点对比、飞书机器人入口和 LLM 调度。

## 功能概览

- 网络测量：DNS、TCP、TLS、TTFB、总耗时和响应体大小。
- 安全体检：HTTPS、证书有效期、HSTS、CSP、X-Frame-Options 等响应头。
- Web 监听：捕获请求方法、路径、请求头、来源和风险提示。
- 站点对比：对两个 URL 的安全配置和性能指标做并排比较。
- AI 调度：通过 OpenAI 兼容接口调用 OpenAI、Kimi、豆包或自定义模型。
- 飞书集成：支持飞书事件回调和消息入口。
- 报告导出：支持 Word、Excel、CSV 等报告产物。

## 技术栈

- 前端：Vue 3、Vite、TypeScript、Element Plus、ECharts
- 后端：Spring Boot 3、Java 17、Spring Security、JPA、H2/MySQL、Redis
- 构建：npm、Maven

## 一键部署到 Windows 电脑

目标电脑需要先安装：

- JDK 17 或更高版本
- Node.js 18 或更高版本
- PowerShell 5+ 或 PowerShell 7+

最简单方式是在项目根目录双击：

```text
deploy-local.bat
```

这会自动检查/构建项目，并在本机启动完整网站：

```text
http://localhost:8090
```

如果是短期演示，需要给别人公网访问地址，双击：

```text
deploy-public-demo.bat
```

它会启动完整本地项目，并通过 Cloudflare Quick Tunnel 输出一个临时 HTTPS 地址，例如：

```text
https://example.trycloudflare.com
```

演示期间电脑和终端窗口需要保持运行。Quick Tunnel 地址通常每次启动都会变化。

首次克隆后，在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-and-run.ps1
```

脚本会自动完成：

1. 从 `.env.example` 创建 `.env`。
2. 检查 Java、Node.js、npm。
3. 下载本地 Maven。
4. 安装前端依赖并构建前端。
5. 把前端静态文件打进 Spring Boot。
6. 构建后端 jar 并启动服务。

默认访问地址：

```text
http://localhost:8090
```

如果只想构建不启动：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-and-run.ps1 -NoStart
```

如果已经构建过，只想快速启动：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-and-run.ps1 -SkipBuild
```

## 打包给别人使用

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-release.ps1
```

生成文件：

```text
dist\release\MindMap-AI-Sec-windows.zip
```

把这个 zip 发给别人后，对方解压并执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-release.ps1
```

发布包只要求目标电脑安装 JDK 17+，不需要安装 Node.js 或 Maven。

## 环境配置

`.env` 不会提交到 Git。需要配置 API Key、飞书机器人、数据库等参数时，复制或编辑 `.env.example` 中的字段。

常用配置：

```env
APP_PORT=8090
LOG_PATH=./logs

DB_URL=jdbc:h2:mem:mindmap;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
DB_USERNAME=sa
DB_PASSWORD=
DB_DRIVER=org.h2.Driver
DB_DIALECT=org.hibernate.dialect.H2Dialect

LLM_DEFAULT_PROVIDER=openai
OPENAI_API_KEY=
OPENAI_BASE_URL=https://api.openai.com/v1/chat/completions
OPENAI_MODEL=gpt-4o-mini

FEISHU_APP_ID=
FEISHU_APP_SECRET=
FEISHU_VERIFICATION_TOKEN=
FEISHU_ENCRYPT_KEY=

JWT_SECRET=change-me-in-production
```

默认使用 H2 内存数据库，适合本地演示。生产或长期保存数据时请改为 MySQL，并同步配置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`DB_DRIVER` 和 `DB_DIALECT`。

## 开发模式

后端：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-backend.ps1
```

前端：

```powershell
cd frontend
npm install
npm run dev
```

开发模式默认地址：

- 前端：`http://localhost:3000`
- 后端：`http://localhost:8090`

## 主要 API

```http
GET  /api/agent/skills
POST /api/agent/run

GET  /api/llm/providers
GET  /api/llm/settings
PUT  /api/llm/settings
POST /api/llm/test

POST /api/network/measure
GET  /api/network/reports
POST /api/network/reports/{id}/export
POST /api/network/reports/compare/export
POST /api/network/listener/capture
GET  /api/network/listener/events
POST /api/network/listener/export

POST /api/feishu/events
GET  /api/feishu/health
```

## 项目结构

```text
.
├── backend/                 Spring Boot 后端
├── frontend/                Vue 3 前端
├── docs/                    架构和功能文档
├── scripts/                 一键部署、打包和开发脚本
├── tools/                   本地工具缓存，默认不提交
├── .env.example             环境变量模板
└── README.md
```
