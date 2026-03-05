# xinya-nurse · 医护端

骨髓移植隔离病房 DTx 系统 — 医护工作台（Vue 3 Web 应用）

## 功能页面

| 页面 | 路由 | 说明 |
|---|---|---|
| 数据驾驶舱 | `/dashboard` | 患者分布、心理状态、症状趋势、预警列表 |
| 患者管理 | `/patients` | 列表搜索、新建患者 |
| 患者详情 | `/patients/:id` | 基本信息、心理数据、对话记录、宣教进度、设备状态 |
| 预警中心 | `/alerts` | 危机预警处理 |
| 宣教内容 | `/education` | 按阶段浏览宣教视频/图文 |

## 运行

**前提：** 后端服务已在 `localhost:8080` 启动

```bash
npm install
npm run dev        # 开发模式，访问 http://localhost:3001
npm run build      # 生产构建
```

## 环境要求

- Node.js >= 18
- 后端地址默认代理至 `http://localhost:8080`，如需修改请编辑 `vite.config.ts` 中的 `proxy.target`

## 兼容性

平板（≥768px）和电脑（≥992px）双端自适应，侧边栏在平板下自动切换为抽屉模式。
