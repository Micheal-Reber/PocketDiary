# PocketDiary

简洁好用的 Android 日记 App，Material 3 原生风格。

## 功能

- 📖 **日记** — 按日期记录，一天一篇，支持心情、天气、位置；列表卡片固定尺寸预览
- 📅 **日历 + 打卡** — 月历视图 + 习惯打卡系统
  - 点击日期弹窗，逐个勾选习惯完成情况
  - 日历格子显示当天已打卡习惯的彩色圆点（最多 3 个）
  - 支持自定义习惯名称和 emoji 图标
  - 可补打卡（不限过去），不能打未来日期
- 📊 **统计** — 全屏统计页，三种视图
  - 周频率（最近十周滚动窗口）/ 月视图 / 年视图折线图
  - 每个数据点带数值标签，当前周期红色高亮
  - 底部勾选习惯控制曲线显隐，并汇总该周期打卡天数
- ⚙️ **设置** — 暗色/亮色模式切换

## 技术栈

- **UI**: Jetpack Compose + Material 3（支持动态取色）
- **数据库**: Room（DiaryEntry、Habit、HabitRecord 三表）
- **设置**: DataStore Preferences
- **导航**: Navigation Compose（底部三 Tab）
- **架构**: ViewModel + Repository + Flow
- **最低 SDK**: Android 8.0 (API 26)

## 项目结构

```
app/src/main/java/com/example/diary/
├── MainActivity.kt
├── DiaryApplication.kt
├── data/
│   ├── local/           # Room 数据库、DAO、实体
│   ├── preferences/     # DataStore 主题偏好
│   └── repository/      # 数据仓库层
└── ui/
    ├── diary/           # 日记列表
    ├── editor/          # 日记编辑器
    ├── habits/          # 日历 + 打卡 + 折线统计图
    ├── navigation/      # 底部导航 + 路由
    ├── settings/        # 设置页
    └── theme/           # Material 3 主题
```

## 构建

1. 安装 JDK 17 + Android SDK (platform 35)
2. 设置环境变量 `ANDROID_HOME` 和 `JAVA_HOME`
3. 运行 `./gradlew assembleDebug` 或 `gradlew.bat installDebug`

## License

MIT
