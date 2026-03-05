# 心芽DTx - Android 患者端

骨髓移植隔离病房数字疗法系统 - Android 患者端应用

## 项目架构

采用 Clean Architecture + MVVM 模式

```
app/src/main/java/com/xinya/dtx/
├── core/                   # 核心基础模块
│   ├── database/           # Room 本地数据库
│   ├── network/            # Retrofit 网络层
│   ├── sync/               # 离线同步引擎
│   └── kiosk/              # Kiosk锁定模块
├── feature/                # 功能模块
│   ├── agent/              # 双智能体对话
│   ├── hopetree/           # 希望之树
│   ├── pro/                # PRO数据采集
│   ├── education/          # 护理宣教
│   └── meditation/         # 冥想疗愈
├── clinical/               # 临床路径状态机
├── di/                     # 依赖注入 (Hilt)
└── ui/                     # UI主题和导航
```

## 技术栈

- **语言**: Kotlin 2.0
- **UI**: Jetpack Compose
- **架构**: MVVM + Clean Architecture
- **依赖注入**: Hilt
- **数据库**: Room (SQLite)
- **网络**: Retrofit + OkHttp
- **异步**: Kotlin Coroutines + Flow

## 核心功能

1. **双智能体对话** - 小芽(心理陪护) + 小护士(护理宣教)
2. **希望之树** - 游戏化免疫重建可视化
3. **PRO数据采集** - 每日打卡和症状记录
4. **护理学堂** - 宣教视频学习
5. **冥想空间** - 沉浸式疗愈体验
6. **离线优先** - 弱网环境数据可靠性保障
7. **Kiosk模式** - 医疗级设备锁定

## 开发环境

- Android Studio Hedgehog | 2023.1.1+
- JDK 17
- Gradle 8.2+

## 构建运行

```bash
# 同步依赖
./gradlew sync

# 调试运行
./gradlew installDebug

# 生成发布包
./gradlew assembleRelease
```

## API 对接

修改 `di/AppModule.kt` 中的 `API_BASE_URL` 配置后端地址：

```kotlin
const val API_BASE_URL = "http://your-server:8080/"
```
