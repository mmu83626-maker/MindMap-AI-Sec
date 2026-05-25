# NetScope AI Agent / Skill Design

NetScope AI 的 Agent 目标是把自然语言请求转成可执行的计算机网络工作流：

```text
Feishu / Web command
  -> Agent Orchestrator
  -> Skill planner
  -> Network / Listener / Report tool execution
  -> LLM analysis summary
  -> Web response or Feishu reply
```

## Current Endpoints

```http
GET  /api/agent/skills
POST /api/agent/run
POST /api/feishu/events
POST /api/llm/chat
POST /api/network/measure
POST /api/network/listener/capture
GET  /api/network/listener/events
```

## Built-In Skills

| Skill | Purpose |
| --- | --- |
| `network_security_measure` | Detect HTTPS/TLS, certificates, security headers, DNS/TCP/TLS/TTFB timing. |
| `web_app_listener` | Capture and review Web requests, headers, body previews and risk hints. |
| `file_generate` | Generate Word, Excel, CSV, PDF or Markdown reports. |
| `llm_chat` | Call the configured DeepSeek/OpenAI-compatible model for analysis and explanation. |
| `feishu_notify` | Send execution results and summaries back to Feishu. |

## Command Examples

From Feishu or the web chat:

```text
检测 https://example.com 的 HTTPS、TLS 证书和安全响应头
对比 https://example.com 和 https://cloudflare.com 的安全性与性能
开启 Web 监听，抓取请求后生成 Excel 表格
把刚才的网站体检结果生成 Word 报告
解释 DNS、TCP、TLS、TTFB 哪一步是瓶颈
```

## Local Backend

If Maven is not installed globally, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-backend.ps1
```

The script downloads a local Maven distribution into `tools/` and starts the backend on port `8090` by default.

For Cloudflare Tunnel:

```powershell
.\tools\cloudflared.exe tunnel --url http://localhost:8090 --http-host-header localhost
```

Then set the Feishu event callback URL to:

```text
https://your-tunnel.trycloudflare.com/api/feishu/events
```
