<div align="center">

# 📖 PocketDiary

**简洁好用的 Android 日记 App · Material 3 原生风格 · 数据纯本地**

![Platform](https://img.shields.io/badge/platform-Android_8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-green)

</div>

---

## ✨ 功能一览

### 📖 日记
- **一天一篇**，按日期自动归档，月份大数字分割，翻找一目了然
- 记录**心情、天气、位置**——一键定位并反向解析地址，天气手动点选
- 卡片固定尺寸、正文两行预览，长文不撑爆列表
- **左滑删除**，误删有确认弹窗兜底
- 支持**自定义列表背景**：在设置里导入一张照片，整个日记页焕然一新
- **Markdown 预览**与**图文混排**（最多 7 张插图）

### 📅 日历打卡
- 月历视图 + 习惯打卡系统
- 每个日期格子显示当天已打卡习惯的**彩色圆点**（最多 3 个，颜色与习惯一一对应）
- 点击任意日期弹窗逐项勾选；**可补打卡**，未来日期锁定
- 习惯名称与 emoji 图标完全自定义

### 📊 统计
- 全屏统计页，三种视图自由切换：**周频率**（最近十周滚动窗口）/ **月视图** / **年视图**
- 折线图每个数据点带**数值标签**，Y 轴 ∞ 刻度设计
- 当前周期（本周 / 本月）**红色高亮**
- 底部勾选习惯即可控制曲线显隐，同时汇总该周期打卡天数

### ⏳ 倒数日
- 经典全屏 / 照片卡片双风格，蓝橙徽章、置顶、重复（年/月）
- 分享图离屏渲染；每事件独立背景图

### ✅ 待办
- 黑底列表 + 已完成折叠；左滑删除
- **精确闹钟提醒**（每天重复 / 过期不排 / 开机与改时区自动重排）

### ⚙️ 设置
- 暗色 / 亮色模式切换（独立于系统）
- 日记背景自定义与恢复默认
- **数据迁移**：导出 / 导入 ZIP（全量覆盖 + 事务回滚 + 版本校验）
- 关于页版本号自动跟随构建版本（`BuildConfig`）

## 📥 下载安装

前往 [**Releases 页面**](https://github.com/Micheal-Reber/PocketDiary/releases) 下载最新的 `app-release.apk`，传输到手机直接安装（需允许安装未知来源应用）。

> 要求 Android 8.0（API 26）及以上。

## 🛠️ 技术栈

| 分类 | 方案 |
|------|------|
| 语言 | Kotlin 1.9.24 |
| UI | Jetpack Compose + Material 3（动态取色） |
| 数据库 | Room **v11**（DiaryEntry / Habit / HabitRecord / CountdownEvent / TodoItem 五表；v9/v10 手写迁移） |
| 偏好 | DataStore Preferences |
| 导航 | Navigation Compose（底部五 Tab + 编辑器 + 统计页） |
| 架构 | ViewModel + Repository + Flow 单向数据流 |
| 定位 | 系统 LocationManager（无 Play Services 依赖） |
| 备份 | kotlinx-serialization JSON + 流式 ZIP（STORED CRC） |
| 提醒 | AlarmManager 精确闹钟 + 开机/改时区重排 |
| 开屏 | 系统开屏纯色化（无 logo，随软件内亮暗设置秒进界面） |
| 处理器 | **KSP**（Room，非 kapt） |

## 🚀 构建

1. 安装 JDK 17 与 Android SDK（platform 35）
2. 配置环境变量 `JAVA_HOME`、`ANDROID_HOME`
3. 签名（可选）：根目录放 `pocketdiary.jks` + gitignored `keystore.properties`（`storePassword`/`keyAlias`/`keyPassword`）
4. 执行：

```bash
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 签名发布包（有 keystore.properties 时）
./gradlew test               # JVM 单元测试
```

> **debug 与 release 签名不互通**，覆盖安装需先卸载旧包。

## 📂 项目结构

```
app/src/main/java/com/example/diary/
├── MainActivity.kt            # 入口：亮暗模式接管（独立于系统）+ 开屏 + open_todo_id 深链
├── DiaryApplication.kt
├── receiver/                  # TodoAlarmReceiver / BootCompletedReceiver（闹钟 + 开机/改时区重排）
├── util/                      # DateUtils 共享日期格式化
├── data/
│   ├── backup/                # BackupData / ExportService / ImportService / BackupRepository
│   ├── countdown/             # DateMath + ShareCardRenderer + TextureLibrary
│   ├── image/                 # BackgroundImageStore（相对路径）/ EventImageStore
│   ├── local/                 # Room 数据库、DAO、实体（version 11）
│   ├── location/              # 定位封装（无 GMS）
│   ├── photo/                 # DiaryPhotoStore 两阶段生命周期
│   ├── preferences/           # DataStore 主题/背景/预览偏好
│   ├── repository/            # 数据仓库层
│   └── todo/                  # TodoReminderScheduler + TodoNotificationHelper
└── ui/
    ├── components/            # SharedUi：SwipeDelete/Confirm/Search/UtcDatePicker/PresetChip
    ├── countdown/             # 倒数日三屏 + 共享件
    ├── diary/                 # 日记列表：卡片、月份分割、滑动删除、背景、搜索防抖
    ├── editor/                # 日记编辑器：心情/天气/定位/预览
    ├── habits/                # 打卡日历 + 统计图表 + ViewModel
    ├── navigation/            # 底部导航 + 路由
    ├── settings/              # 设置页（含数据迁移 + 关于版本）
    ├── theme/                 # Material 3 主题
    └── todo/                  # 待办列表 + 编辑/提醒 Sheet
```

## 🧪 测试

JVM 单测覆盖倒数日日期计算、提醒调度、备份序列化/版本闸门、待办仓库、模糊缓存等级：

```bash
./gradlew test
```

## 📄 License

[MIT](LICENSE) © Micheal-Reber
