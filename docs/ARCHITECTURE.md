# NetScope AI 架构设计

## 1. 分层架构

```text
客户端层
├── Web Vue App：网络测量、安全体检、监听与报告工作台
└── Feishu Bot：移动端自然语言指令入口

接口层
├── REST API：Agent 调度、LLM、网络测量、监听、报告导出
└── Event Callback：飞书消息事件回调

智能体层
├── AgentOrchestrator：理解用户意图并规划 Skill 调用
├── LlmGateway：统一 DeepSeek / OpenAI 兼容模型
├── SkillRouter：选择网络测量、Web 监听、报告生成、飞书回复等工具
└── Runtime Settings：网页端与飞书端共用同一套模型配置

业务层
├── NetworkMeasureService：DNS/TCP/TLS/HTTP 测量与安全检查
├── NetworkReportService：Word/Excel/CSV 报告生成
├── NetworkListenerService：Web 请求监听与风险提示
└── FeishuService：消息接收、AI 调度与回复

数据层
├── H2/MySQL：用户、报告、工具运行记录、审计
├── Local Files：导出的报告文件
└── Runtime Memory：监听事件、最近测量结果、Skill 状态
```

## 2. 网络 Agent 工作流

1. 用户从网页端或飞书发送自然语言请求。
2. AgentOrchestrator 调用 DeepSeek 分析意图，判断是否需要网络测量、网站对比、Web 监听、文件导出或普通问答。
3. SkillRouter 执行对应工具：
   - NetworkMeasureService 检测 HTTPS/TLS、证书、安全响应头和耗时指标。
   - NetworkListenerService 捕获请求日志并生成风险提示。
   - NetworkReportService 生成 Word、Excel 或 CSV 分析报告。
   - FeishuService 将结果回复到机器人消息来源。
4. 后端返回结构化结果，前端展示流程图、体检卡、对比面板、监听日志和可下载报告。

## 3. 网络安全体检

重点检查项：

- HTTPS 是否启用。
- TLS 版本与证书有效期。
- HSTS、CSP、X-Frame-Options、X-Content-Type-Options、Referrer-Policy 等安全响应头。
- HTTP 状态、跳转、Server 暴露和潜在配置风险。

评分用于演示和辅助分析，不替代专业渗透测试结论。

## 4. 网络测量可视化

测量流程拆成：

- DNS：域名解析耗时。
- TCP：连接建立耗时。
- TLS：握手耗时和协议版本。
- HTTP/TTFB：首字节与总体响应耗时。

前端将这些阶段做成可暂停、回放的流程可视化，便于讲解网络访问链路与性能瓶颈。

## 5. Web 应用监听

监听模块用于展示 Web 请求观察能力：

- 捕获请求方法、路径、Header、Body 摘要和来源。
- 标记缺失安全头、可疑 User-Agent、明文请求等风险提示。
- 支持将监听记录导出为 Excel、CSV 或 Word 分析材料。

## 6. 飞书移动端控制

飞书侧需要：

- 创建企业自建应用或机器人。
- 配置事件订阅 URL：`/api/feishu/events`。
- 配置消息发送权限。
- 将消息交给 AgentOrchestrator，使飞书端和网页端共享同一套 DeepSeek 调度逻辑。

典型指令：

- “检测 https://example.com 的 TLS 和安全响应头”
- “对比 example.com 和 cloudflare.com”
- “开启 Web 监听并生成 Excel 报告”
- “总结刚才的监听日志风险”

## 7. 安全边界

- 飞书 Token、模型 Key、平台凭证必须只放在后端或安全环境变量中。
- 飞书事件回调应校验 verification token / encrypt key。
- 网络检测应限制超时、重定向次数和目标范围，避免被滥用为扫描器。
- 所有工具调用建议写入审计日志。
