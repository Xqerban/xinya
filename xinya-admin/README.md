# 心芽DTx - 医护管理端

骨髓移植隔离病房数字疗法系统 - Vue 3 医护管理平台

## 项目架构

```
src/
├── api/                    # API请求封装
├── views/                  # 页面视图
│   ├── dashboard/          # 数据驾驶舱
│   ├── patient/            # 患者管理
│   ├── education/          # 宣教内容管理
│   └── knowledge/          # 知识库管理
├── components/             # 公共组件
├── stores/                 # Pinia状态管理
├── router/                 # 路由配置
├── types/                  # TypeScript类型
├── layouts/                # 布局组件
└── styles/                 # 全局样式
```

## 技术栈

- **框架**: Vue 3.4 + TypeScript
- **UI库**: Element Plus
- **图表**: ECharts + vue-echarts
- **状态管理**: Pinia
- **构建工具**: Vite 5
- **HTTP**: Axios

## 核心页面

1. **数据驾驶舱** - 患者状态总览、心理能量分布、症状趋势、预警信息
2. **患者管理** - 患者档案列表、详情查看、阶段流转
3. **宣教内容管理** - 视频上传、分类管理、内容编辑
4. **知识库管理** - 护理知识条目维护（供RAG检索）

## 开发运行

```bash
# 安装依赖
npm install

# 开发模式
npm run dev

# 构建生产版本
npm run build

# 预览生产版本
npm run preview
```

## 环境配置

开发模式下API请求会代理到 `http://localhost:8080`。

如需修改后端地址，编辑 `vite.config.ts`：

```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://your-backend:8080',
      changeOrigin: true
    }
  }
}
```

## 默认账号

Demo版本无需密码，点击登录即可进入系统。
