# 雨课堂真实数据接入

当前项目不会读取你日常浏览器里的 Cookie。雨课堂接入使用 Playwright 打开的独立浏览器用户目录：

```powershell
cd frontend
npm run rain:install-browser
npm run rain:login
```

如果雨课堂必须在系统浏览器里使用，可以直接调用本机 Chrome 或 Edge：

```powershell
cd frontend
npm run rain:login:chrome
# 或
npm run rain:login:edge
```

流程：

1. 脚本打开独立浏览器窗口。
2. 你手动登录雨课堂网页版。
3. 进入课程、作业、待办、学习任务等页面，让页面加载作业接口。
4. 终端按 Enter，脚本保存会话和捕获结果。

保存位置：

```text
backend/data/rain-classroom-session.json
backend/data/rain-classroom-assignments.json
backend/data/rain-classroom-discovery.json
backend/data/rain-classroom-browser/
```

后端默认读取：

```text
backend/data/rain-classroom-assignments.json
```

如果捕获到作业，前端会显示“真实雨课堂”；如果没有捕获到，会明确显示“演示数据”。

可选环境变量：

```env
RAIN_CLASSROOM_URL=https://www.yuketang.cn/
RAIN_CLASSROOM_CACHE_PATH=自定义缓存文件路径
```
