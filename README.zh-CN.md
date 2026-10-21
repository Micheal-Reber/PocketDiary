<div align="center">

# 📖 PocketDiary

**简洁好用的 Android 日记 App · 真液态玻璃 UI · 数据纯本地、零联网**

![Platform](https://img.shields.io/badge/platform-Android_8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white)
![Room](https://img.shields.io/badge/Room-v16-3DDC84)
![Version](https://img.shields.io/badge/version-1.16.0-blue)
![License](https://img.shields.io/badge/license-MIT-green)

[English](README.md) | **简体中文**

**[⬇️ 下载安装](#-下载安装)** · **[✨ 功能](#-功能)** · **[🛠️ 技术栈](#-技术栈)** · **[🙏 鸣谢](#-鸣谢)**

</div>

---

## 一览

五个底部 Tab，全部功能都在这一页说清楚：

| Tab | 里面有什么 |
|-----|-----------|
| **日记** | 一天一篇 · 心情/天气/定位 · Markdown · 图文混排 · 全文搜索 · 自定义磨砂背景 · 密码锁 |
| **日历** | 习惯打卡月历 · 彩色圆点 · 补打卡 · 周/月/年统计图表 |
| **倒数日** | 经典全屏 / 照片卡双风格 · 正倒数三态判定 · 分享图 · **桌面小组件** |
| **待办** | 黑底折叠列表 · 精确闹钟 / 锁屏响铃提醒 · 每天重复 · 稍后再响 |
| **学习** | 番茄钟计时 · 专注任务与记录 · 白噪音 · 到点强提醒 · 专注时锁定其他 App · 专注统计 |

三条承诺：**数据纯本地、零联网依赖**；**换机一键迁移**；**界面是真液态玻璃**，不是贴图。

---

## ✨ 功能

### 📖 日记
- **一天一篇**，按日期自动归档（`date` 唯一索引，改日期 = 搬移语义），月份大数字分割列表
- 记录**心情、天气、位置**：一键定位并反向解析成可读地址，天气手动点选
- **Markdown 书写与预览**：commonmark 解析（含表格 / 删除线 / 任务列表三个 GFM 扩展）+ 自研子集渲染，格式条只发动作、不碰正文
- **图文混排**最多 7 张插图，两阶段生命周期管理，图片全屏查看
- **全文搜索**（250ms 防抖）、**左滑删除**（确认弹窗兜底）、卡片正文两行预览
- **自定义列表背景**：导入照片后卡片自动做 **backdrop 磨砂模糊**（GraphicsLayer + BlurEffect，API 31+）
- **日记密码锁**：4–6 位 PIN，三种验证时机（会话内 / 每次进日记 / 每天一次），改密码需验旧，SHA-256 加盐哈希，连错 5 次冷却 30 秒

### 📅 日历打卡
- 月历视图 + 习惯打卡系统，日期格显示当天已打卡习惯的**彩色圆点**（最多 3 个）
- 点击日期逐项勾选，**可补打卡**，未来日期锁定；习惯名称与 emoji 完全自定义
- **统计页**三视图：**周频率**（最近十周滚动窗口）/ **月视图** / **年视图**
- 折线图每点带**数值标签**、当前周期高亮，底部勾选习惯控制曲线显隐

### ⏳ 倒数日
- **经典全屏 / 照片卡片**双风格；蓝橙徽章、置顶、每年/每月重复
- **正倒数三态判定**（Today / Countdown / Countup，跨月跨闰年安全 +1 日）
- 分享图**离屏渲染**（1080×1440），每事件独立背景图；照片卡支持上下拖动取景
- 列表 / 网格双视图，过程式纹理背景（无位图素材）

### 🖥️ 桌面小组件
- 倒数日小组件，**纯 RemoteViews 实现**（不用 Glance / WorkManager），在 MIUI 桌面实测可用
- 单层扁平视图树，固定字号，零位图背景（纯黑 28dp 圆角）——避开 MIUI 的嵌套截断与位图尺寸坑
- 显示哪一条可手选，未选则自动取置顶或离今天最近的一条；点击进选择页而非详情页
- 半小时自更新，解锁 / 改时间 / 改时区 / 零点都会触发刷新

### ✅ 待办
- 黑底列表 + **已完成折叠**，勾选即下沉/回升；Sheet 直管编辑（无独立编辑页）
- **精确闹钟提醒**：通知模式 / **闹钟响铃模式**（`setAlarmClock` + 全屏 Intent，锁屏也能响），每天重复、稍后 5 分钟再响
- **开机、改时区、改时间自动重排**并补发错过的普通通知；MIUI 自启动检测与深链引导
- 通知 / 响铃点透深链直达待办条目（`open_todo_id`）

### 🍅 学习模式（番茄钟）
- **专注任务与专注记录**：任务累计时长与次数，会话与任务写回同事务，不怕中途被杀
- 段长、短休、长休、长休间隔全部可配；**今日目标支持自定义分钟数**（10–720）
- **白噪音**：白 / 粉 / 棕程序化合成（零素材、零缝隙、统一 RMS），也可**导入自定义音频**（复制进应用目录，不再依赖外部文件路径）
- **到点强提醒**：只在专注段结束时响铃 + 震动 + 亮屏（60 秒自动停），休息段结束只发通知；铃声、音量、震动开关 / 时长 / 间隔 / 强度、亮屏全部可配，响铃期间通知上可一键停止
- **专注时锁定其他应用**：无障碍只读前台包名把人拉回专注页，白名单可自由切换（输入法恒放行），支持系统 / 已安装分栏检索，显示**累计拉回次数**
- **常驻通知 + Android 16 实时活动**：状态栏倒计时药丸，进度走过的部分加粗、分段配色随主题
- **专注统计**：今日目标半圆表盘、近 7 天与 26 周热力图、周/月/年趋势与环比、事项分布（点行直接开该任务的番茄钟）
- **后台保活引导**：检测电池优化白名单并一键加入，已在白名单时引导查 MIUI / HyperOS 自启，避免系统中途杀进程断计时
- 计时真值用 `SystemClock.elapsedRealtime`，跨重启与改时间都准

### ⚙️ 设置
- 设置 Tab 是**目录页**，点进 **外观 / 待办 / 隐私 / 数据迁移 / 关于** 五个二级页，目录行右侧直接显示当前值摘要
- 暗色 / 亮色模式**独立于系统**，开屏纯色随软件内模式；**壁纸取色**（Material You）
- **底栏模式**：悬浮玻璃胶囊（默认）或经典停靠原生栏，一键切换
- **语言**：中文 / English / 跟随系统
- **密码锁**与**系统日历同步**开关
- **数据迁移**：导出 / 导入 ZIP，格式 v4 把偏好拆成 外观 / 番茄钟 / 锁定 / 日历 四组，**自定义白噪音音频随包携带**；导入只覆盖备份里有的分组（旧版本的包仍可正常导入，不会把你现在的番茄钟设置抹成默认值）；全量覆盖 + 事务 + `*.bak` 回滚 + 版本闸门 + zip-slip / CRC 防护
- 关于页版本号自动读 `BuildConfig.VERSION_NAME`

### 🎨 设计语言
- **真液态玻璃**：悬浮胶囊底栏与加号按钮实时取样背后内容做 backdrop 折射与模糊（[`io.github.kyant0:backdrop`](https://github.com/Kyant0/AndroidLiquidGlass) 1.0.6）
- 卡片层次靠 `surfaceContainerLow/High` 色阶而非阴影；所有圆角与间距走设计令牌，代码里没有魔法数字

---

## 📥 下载安装

前往 [**Releases 页面**](https://github.com/Micheal-Reber/PocketDiary/releases) 下载最新的 `app-release.apk`，传输到手机直接安装（需允许安装未知来源应用）。

> 要求 **Android 8.0（API 26）及以上**；backdrop 模糊等玻璃效果在 **Android 12（API 31）+** 上完整呈现。

## 🛠️ 技术栈

| 分类 | 方案 |
|------|------|
| 语言 | Kotlin 2.3.21 |
| UI | Jetpack Compose BOM 2026.01.01（Compose 1.10.2 / Material 3 1.4）+ 液态玻璃 [`backdrop`](https://github.com/Kyant0/AndroidLiquidGlass) 1.0.6 |
| 数据库 | Room 2.8.5 · **v16 七表**（日记 / 习惯 / 打卡 / 倒数日 / 待办 / 专注任务 / 专注会话），v9→16 手写迁移链 |
| 偏好 | DataStore Preferences ×5（主题 / 语言 / 密码锁 / 番茄钟 / 日历） |
| 导航 | Navigation Compose（底部五 Tab + 子路由） |
| 架构 | ViewModel + Repository + Flow 单向数据流 |
| 定位 | 系统 LocationManager（无 Play Services 依赖） |
| 备份 | kotlinx-serialization JSON + 流式 ZIP（STORED CRC + 版本闸门 v1..v4） |
| 提醒 | AlarmManager（`setExactAndAllowWhileIdle` / `setAlarmClock`）+ 开机 / 时区 / 时间变更重排 |
| Markdown | org.commonmark 0.24.0 + 三个 GFM 扩展（只扩 AST）+ 自研子集渲染 |
| 小组件 | 纯 RemoteViews（无 Glance / WorkManager） |
| 服务 | 前台服务（专注计时）+ 无障碍服务（专注锁定，只读前台包名） |
| 处理器 | **KSP**（Room，非 kapt） |
| 开屏 | 系统开屏纯色化（无 logo，随软件内亮暗设置秒进界面） |

## 🚀 构建

1. 安装 **JDK 17** 与 Android SDK（compileSdk 36）
2. 配置环境变量 `JAVA_HOME`、`ANDROID_HOME`
3. 签名（可选）：根目录放 `pocketdiary.jks` + gitignored `keystore.properties`（`storePassword`/`keyAlias`/`keyPassword`）
4. 执行：

```bash
./gradlew test               # JVM 单元测试（发版前必跑）
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 签名发布包（有 keystore.properties 时）
```

> **debug 与 release 签名不互通**，覆盖安装需先卸载旧包（会清数据）。

## 📂 项目结构

```
app/src/main/java/com/example/diary/
├── MainActivity.kt         # 入口：亮暗模式接管（独立于系统）+ open_todo_id 深链
├── DiaryApplication.kt
├── receiver/               # 待办闹钟触发、开机/改时间重排
├── util/                   # DateUtils 共享日期与提醒格式化
├── data/
│   ├── backup/             # 备份格式 v4 + 导出/导入服务（版本闸门、zip-slip 防护）
│   ├── countdown/          # 正倒数判定纯函数 + 分享图渲染 + 程序化纹理
│   ├── focus/              # 番茄钟计时纯逻辑 + 前台服务 + 白噪音合成 + 专注锁定 + 统计计算
│   ├── image/              # 背景图存储（相对路径）/ 每事件背景
│   ├── local/              # Room v16：七实体 + DAO + MIGRATION_* 链
│   ├── location/           # 定位封装（无 GMS）
│   ├── photo/              # 图文混排两阶段生命周期
│   ├── preferences/        # DataStore ×5：主题 / 语言 / 密码锁 / 番茄钟 / 日历
│   ├── repository/         # 数据仓库层（SaveResult 日期冲突处理）
│   └── todo/               # 闹钟调度 + 通知 + 全屏响铃
└── ui/
    ├── components/         # 多屏共用件：滑删 / 确认 / 搜索 / 日期选择 / 预设 chip
    ├── countdown/          # 倒数日列表 / 编辑 / 详情 + 小组件选择页
    ├── diary/              # 日记列表：月份分割、滑动删除、背景磨砂、全文搜索
    ├── editor/             # 编辑器：无边框书写、Markdown 预览与格式条、图文混排
    ├── focus/              # 专注页 + 任务组件 + 专注统计二级页
    ├── habits/             # 打卡日历 + 统计图表（自研 LineChart）+ ViewModel
    ├── lock/               # 密码锁键盘屏
    ├── navigation/         # 底部 Tab + 路由 + 液态玻璃底栏（双模）+ 玻璃基建与手感实现
    ├── settings/           # 目录页 + 外观 / 待办 / 隐私 / 数据迁移 / 关于 五个二级页
    ├── theme/              # Material 3 主题 + 圆角 / 间距令牌
    ├── todo/               # 待办列表 + 编辑/提醒 Sheet + 响铃页
    └── widget/             # 倒数日桌面小组件（RemoteViews）
```

## 🧪 测试

JVM 单测 **19 个测试类 / 195 例**，`./gradlew test` 一次跑完：

| 领域 | 测试类 |
|------|--------|
| 日期与提醒 | `DateMathTest` · `TodoReminderSchedulerTest` · `CountdownReminderTest` |
| 备份与迁移 | `BackupSerializationTest` · `BackupVersionGateTest` · `Migration15To16Test`（Robolectric） |
| 番茄钟 | `FocusTimerTest` · `FocusStatsTest` · `WhiteNoiseTest` · `FocusGuardTest` · `FocusAlarmRingerTest` · `FocusNotificationPlannerTest` · `BackgroundStartTest` |
| 待办 | `TodoRepositoryTest`（Robolectric） |
| 小组件 | `CountdownWidgetModelTest` |
| Markdown | `MarkdownEditingTest` · `MarkdownPlainTextTest` · `MarkdownExtParseTest` |
| 渲染 | `BlurCacheTest` |

## 🙏 鸣谢

- **[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)** —— PocketDiary 的液态玻璃界面构建于它的 `backdrop` 库（`io.github.kyant0:backdrop`）之上。折射 / 色散着色器、边缘高光、内阴影，以及示例目录里的 `LiquidBottomTabs`，是我们悬浮底栏、悬浮按钮和玻璃卡片的实现参考。感谢这个库与示例工程。
- **[perchA5uka/Niriko](https://github.com/perchA5uka/Niriko)** —— 它的 `docs/dock-kyant-migration-plan.md` 与 `ui/bottombar`（MIT）记录了移植悬浮胶囊底栏的坑：命中格与指示器是两套坐标、手势层的归属、指示器必须显式指定宽度。我们的实现参考了这些结论。

## 📄 License

[MIT](LICENSE) © Micheal-Reber
