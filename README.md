# MindMap AI-Sec 🧠🔐

## 项目概述

**MindMap AI-Sec** 是一个面向科研人员的**安全增强型 Web 应用**，在原有 AI 学术助手基础上，集成了：

- 🤖 **RAG 智能学术咨询** - 基于私有知识库的精准问答
- 🔐 **网络安全防护** - 加密存储、MFA 认证、审计日志
- 📊 **网络质量测量** - 延迟/带宽测速、智能节点优选
- 🌐 **Web 应用** - Vue3 响应式 + 多端适配
- 🐳 **容器化部署** - Docker Compose 一键启动

## 📋 功能特性

| 编号 | 功能名称 | 简要描述 |
|------|--------|----------|
| F-01 | RAG 智能学术咨询 | LangChain4j + 流式响应 + 原文溯源 |
| F-02 | 多模态资料解析 | OCR 公式识别 + 语音转写 |
| F-03 | 动态知识图谱 | 3D 可视化关联图谱 |
| F-04 | 自动化写作辅助 | 大纲生成 + 引用格式校对 |
| F-05 | 网络安全防护 | JWT + AES-256 + MFA + 审计日志 |
| F-06 | 网络质量测量 | 延迟测速 + 带宽检测 + 趋势图表 |
| F-07 | 用户认证与权限 | OAuth 2.0 + MFA + RBAC |
| F-08 | 笑话生成器 | 外部 API 集成 + 6 种分类 |

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────┐
│                   前端（Vue3）                       │
│  Element Plus + Pinia + WebSocket + Vite           │
└────────────────────┬────────────────────────────────┘
                     │ HTTPS + JWT
┌────────────────────▼────────────────────────────────┐
│                 API 网关（Nginx）                    │
│              SSL/TLS 1.3 加密                       │
└────────────────────┬────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────┐
│            后端（Spring Boot 3）                    │
│  ├─ Spring Security（JWT + MFA + RBAC）            │
│  ├─ LangChain4j（RAG 知识库）                      │
│  ├─ WebSocket（实时流式推送）                      │
│  ├─ Network Service（测速 + 监控）                 │
│  └─ Audit Service（操作审计）                      │
└────────────────────┬────────────────────────────────┘
                     │
   ┌─────────────────┼──────────────────┐
   │                 │                  │
┌──▼──┐        ┌─────▼──┐         ┌───▼────┐
│MySQL│        │ Redis  │         │OpenAI  │
└─────┘        └────────┘         └────────┘
```

## 🚀 快速启动

### 前置要求
- Docker & Docker Compose
- Node.js 16+
- Java 17+
- Git

### 启动步骤

```bash
# 1. 克隆仓库
git clone https://github.com/mmu83626-maker/MindMap-AI-Sec.git
cd MindMap-AI-Sec

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 填入你的 API Key

# 3. 启动所有服务
docker-compose up -d

# 4. 查看日志
docker-compose logs -f backend

# 5. 访问应用
# 前端: http://localhost:3000
# 后端 API: http://localhost:8080
# MySQL: localhost:3306
# Redis: localhost:6379
```

## 📚 核心模块

### 1. RAG 智能咨询（F-01）
- ✅ LangChain4j 集成
- ✅ OpenAI API 调用
- ✅ WebSocket 流式响应（打字机效果）
- ✅ 原文溯源和引用
- ✅ 向量数据库存储

**API 端点：**
```bash
GET  /api/rag/ask?query=xxx          # 提问
GET  /api/rag/sources?query=xxx      # 获取来源
POST /api/documents/upload           # 上传文献
GET  /api/documents/list             # 文献列表
```

### 2. 网络安全（F-05）
- ✅ JWT Token 认证（24h 过期）
- ✅ MFA 多因素认证（Google Authenticator）
- ✅ AES-256 数据加密
- ✅ RBAC 角色权限控制
- ✅ XSS/SQL 注入防护
- ✅ 操作审计日志
- ✅ HTTPS/TLS 1.3

**API 端点：**
```bash
POST   /api/auth/register            # 注册
POST   /api/auth/login               # 登录
POST   /api/auth/mfa/setup           # 设置 MFA
POST   /api/auth/mfa/verify          # 验证 MFA
GET    /api/audit-logs               # 审计日志
```

### 3. 网络测量（F-06）
- ✅ 延迟测量（ICMP Ping）
- ✅ 带宽测速（下载/上传）
- ✅ API 响应监控
- ✅ CDN 节点优选
- ✅ 趋势图表展示
- ✅ 网络诊断工具

**API 端点：**
```bash
POST   /api/network/speedtest        # 运行测速
GET    /api/network/metrics          # 获取指标
GET    /api/network/trends?days=7    # 获取趋势
GET    /api/network/health           # 健康检查
```

### 4. Web 应用（Vue3）
- ✅ 响应式设计（PC/平板/手机）
- ✅ Element Plus UI 组件库
- ✅ Pinia 状态管理
- ✅ Vue Router 路由
- ✅ TypeScript 类型安全
- ✅ 暗黑模式支持

**页面：**
```
├── Dashboard      - 仪表板
├── Chat          - RAG 对话
├── Documents     - 文献管理
├── Network       - 网络测量
├── AuditLog      - 审计日志
├── Settings      - 设置
└── Login         - 登录/注册
```

### 5. 笑话生成器（F-08）
- ✅ JokeAPI 集成
- ✅ 6 种笑话分类
- ✅ 缓存优化
- ✅ 一键复制/分享
- ✅ 笑话历史记录

## 📂 项目结构

```
MindMap-AI-Sec/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/mindmap/
│   │   ├── config/                  # 配置类
│   │   ├── controller/              # REST 控制器
│   │   ├── service/                 # 业务服务
│   │   ├── entity/                  # 数据模型
│   │   ├── repository/              # 数据访问
│   │   ├── security/                # 安全模块
│   │   ├── websocket/               # WebSocket
│   │   └── exception/               # 异常处理
│   ├── src/main/resources/
│   │   ├── application.yml          # 配置文件
│   │   ├── application-dev.yml
│   │   ├── application-prod.yml
│   │   └── logback-spring.xml       # 日志配置
│   ├── pom.xml                      # Maven 依赖
│   └── Dockerfile
│
├── frontend/                         # Vue3 前端
│   ├── src/
│   │   ├── components/              # Vue 组件
│   │   ├── views/                   # 页面
│   │   ├── store/                   # Pinia 状态
│   │   ├── api/                     # API 客户端
│   │   ├── router/                  # 路由配置
│   │   ├── utils/                   # 工具函数
│   │   ├── assets/                  # 静态资源
│   │   ├── App.vue
│   │   └── main.ts
│   ├── public/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── Dockerfile
│
├── docker-compose.yml               # 容器编排
├── nginx.conf                       # Nginx 配置
├── .env.example                     # 环境变量模板
├── .gitignore
├── LICENSE                          # MIT 许可证
└── docs/                            # 文档
    ├── API_DOCUMENTATION.md
    ├── DEPLOYMENT_GUIDE.md
    ├── SECURITY_HANDBOOK.md
    ├── ARCHITECTURE.md
    └── JOKE_API_GUIDE.md
```

## 🔐 安全特性详解

### 身份认证
```
注册 → 密码加密(BCrypt) → 用户存储
  ↓
登录 → 验证密码 → 颁发 JWT Token
  ↓
MFA 设置 → Google Authenticator → 扫码认证
  ↓
API 调用 → JWT 验证 → RBAC 权限检查
```

### 数据加密
- **传输层**：TLS 1.3 HTTPS
- **存储层**：AES-256 CBC 模式
- **敏感字段**：密码(BCrypt)、Token(JWT)、MFA Secret(加密)

### 审计日志
记录所有关键操作：
- 用户登录/注册
- 文献上传/下载
- AI 咨询问题
- 设置修改
- 权限变更

## 📊 网络测量指标

| 指标 | 说明 | 目标值 |
|------|------|--------|
| 延迟 | 往返时间(RTT) | < 50ms |
| 抖动 | 延迟变化 | < 10ms |
| 丢包率 | 数据包丢失 | < 1% |
| 下载速度 | 带宽容量 | > 10 Mbps |
| 上传速度 | 带宽容量 | > 5 Mbps |
| API 响应时间 | 平均响应 | < 200ms |

## 🧪 测试

```bash
# 后端单元测试
cd backend
mvn test

# 前端单元测试
cd frontend
npm run test

# 集成测试
mvn verify
```

## 📖 文档

- [API 文档](./docs/API_DOCUMENTATION.md) - 完整的 REST API 规范
- [部署指南](./docs/DEPLOYMENT_GUIDE.md) - 云端/私有化部署
- [安全手册](./docs/SECURITY_HANDBOOK.md) - 安全配置和最佳实践
- [架构设计](./docs/ARCHITECTURE.md) - 系统架构详解
- [笑话 API 指南](./docs/JOKE_API_GUIDE.md) - 笑话功能文档

## 🌟 主要特性

✅ **安全第一** - 企业级加密和认证  
✅ **高可用** - 容器化部署，支持水平扩展  
✅ **实时响应** - WebSocket 流式推送  
✅ **全端覆盖** - PC/平板/手机响应式  
✅ **易于部署** - Docker Compose 一键启动  
✅ **完整文档** - API/部署/安全手册  
✅ **开源友好** - MIT 许可证  

## 📝 环境变量配置

```bash
# .env 文件示例
DB_PASSWORD=your_db_password
OPENAI_API_KEY=sk-xxxxx
JWT_SECRET=your-super-secret-key-2026
KEYSTORE_PASSWORD=your-keystore-password
REDIS_PASSWORD=your-redis-password
```

## 🚀 部署

### 本地开发
```bash
docker-compose -f docker-compose.yml up -d
```

### 生产环境
```bash
docker-compose -f docker-compose.prod.yml up -d
```

### 私有化部署
详见 [部署指南](./docs/DEPLOYMENT_GUIDE.md)

## 📞 支持和反馈

- 📖 文档：[Wiki](https://github.com/mmu83626-maker/MindMap-AI-Sec/wiki)
- 🐛 Bug 报告：[Issues](https://github.com/mmu83626-maker/MindMap-AI-Sec/issues)
- 💬 讨论：[Discussions](https://github.com/mmu83626-maker/MindMap-AI-Sec/discussions)

## 📄 许可证

MIT License - 详见 [LICENSE](./LICENSE) 文件

## 👨‍💻 贡献

欢迎 Fork 和 Pull Request！

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

**Made with ❤️ by MindMap Team**
