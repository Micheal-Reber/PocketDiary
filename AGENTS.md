# AGENTS.md — PocketDiary 开发指引

**Generated:** 2026-08-25 · **Commit:** 5206d57 · **Branch:** main

本文件供 AI 编码代理（及新成员）快速了解本项目的构建方式、架构约定与历史坑点。

## 项目概览

**PocketDiary** — 极简 Android 日记 App。纯本地存储、零联网依赖（无账号/云同步）。

- **语言/UI**: Kotlin 1.9.24 + Jetpack Compose (BOM 2024.09.03, M3 1.3) + Material 3
- **数据库**: Room（破坏性迁移，开发期惯例）
- **偏好**: DataStore Preferences
- **SDK**: minSdk 26 / target & compile 35 / JDK 17
- **约束**: Kotlin 1.9.24 工具链 —— **不要**引入要求 Kotlin 2.x / Compose 1.8+ / M3 1.4 的依赖（如 Material 3 Expressive 组件）

## 构建与安装

所有 gradle/adb 命令需先设置环境变量（PowerShell）：

```powershell
$env:JAVA_HOME = "E:\dev\jdk-17"
$env:ANDROID_HOME = "E:\dev\android-sdk"
$env:GRADLE_USER_HOME = "E:\dev\.gradle"
.\gradlew.bat assembleRelease   # 签名发布包（本地测试也用这个，与手机上已装签名一致）
.\gradlew.bat assembleDebug     # 调试包（与 release 签名不互通，覆盖安装需先卸载）
```

- adb: `E:\dev\android-sdk\platform-tools\adb.exe`
- 测试机: 小米 23116PN5BC（USB 连接不稳定，掉线后等几秒重试即可）
- 发版流程：改 `versionName/versionCode` → 构建 → 用户手动上传 GitHub Release（**未经用户确认不得发布**）

## 架构

```
app/src/main/java/com/example/diary/
├── MainActivity.kt         # 亮暗模式独立于系统（同步读 DataStore → setTheme 变体）
├── data/
│   ├── image/              # BackgroundImageStore：图片导入+解码+缓存
│   ├── local/              # Room: DiaryEntry / Habit / HabitRecord（version 4）
│   ├── preferences/        # DataStore: 暗色模式 / 日记背景 / 壁纸取色
│   └── repository/         # 薄仓库层（SaveResult 密封类处理日期冲突）
└── ui/
    ├── diary/              # 日记列表：月份分割、滑动删除、自定义背景、全文搜索
    ├── editor/             # 编辑器：无边框书写、Markdown 预览(MarkdownText.kt)
    ├── habits/             # 打卡日历 + 统计图表（LineChart 自研）
    ├── navigation/         # 底部三 Tab：日记/日历/设置 + 编辑器/统计路由
    ├── settings/           # 设置页
    └── theme/              # Material 3 主题
```

## WHERE TO LOOK

| 任务 | 位置 | 备注 |
|------|------|------|
| 日记编辑/保存/改期 | `ui/editor/DiaryEditorScreen.kt` + `DiaryRepository.saveEntry` | 改日期=搬移语义 |
| Markdown 渲染/预览 | `ui/editor/MarkdownText.kt` | commonmark 解析 + 自研子集渲染 |
| 全文搜索 | `DiaryDao.searchEntries` + `ui/diary/DiaryListScreen.kt` | LIKE 实时 Flow |
| 统计图表 | `ui/habits/LineChart.kt` + `StatisticsSection.kt` | 数值标签预计算（remember） |
| 打卡日历 | `ui/habits/CalendarComponents.kt` + `HabitsViewModel.kt` | 多习惯彩点、月份分割 |
| 亮暗/开屏 | `Theme.kt` + `themes.xml` + `MainActivity.kt` | 独立于系统 |
| 背景图缓存 | `data/image/BackgroundImageStore.kt` | 覆盖同名文件后必须 `clearCache()` |

## CODE MAP

（中心度未测量——本环境无 Kotlin LSP/codegraph，以下为本会话全量通读结论）

| 符号 | 类型 | 位置 | 角色 |
|------|------|------|------|
| `DiaryRepository.saveEntry` | suspend | data/repository | 日记唯一写入口（插入/更新/搬移/冲突判定） |
| `BackgroundImageStore.decode` | suspend | data/image | 内存缓存解码（path+mtime+maxDim 键） |
| `markdownToPlainText` | fun | ui/editor/MarkdownText.kt | 列表预览语法剥离 |
| `HabitsViewModel.loadAllStats` | private | ui/habits/HabitsViewModel.kt | 四数据集全量刷新入口 |
| `habitColor` / `HabitColorPalette` | fun/val | ui/habits/LineChart.kt | 习惯配色（全局引用） |
| `AppShapes` / `Spacing` | val | ui/theme | 圆角/间距令牌（禁止字面量） |

## 关键约定（务必遵守）

### 数据库
- **日期一律存 `yyyy-MM-dd` 字符串**（`LocalDate.toString()`），解析用 `LocalDate.parse`
- 日记一天一篇：`diary_entries.date` 有 UNIQUE 索引；保存走 `DiaryRepository.saveEntry`
  - id==0 → 按日期查重后插入；id!=0 → 按 id 整条 UPDATE（改日期=搬移，冲突返回 `SaveResult.DateConflict`）
- **schema 变更 → version +1**（v1.2 对应 Room version 4）；开发期用 `fallbackToDestructiveMigration()`（会清数据，需告知用户）
- DAO 查询只写必要字段；统计查询按需加载（切年只查月统计、切月只查日统计）

### 主题 / UI
- **所有圆角走 `MaterialTheme.shapes`**（AppShapes: 8/12/16/28/32），**禁止** `RoundedCornerShape(字面量)`
- 间距用 `ui/theme/Spacing.kt` 令牌（xs=4/s=8/m=12/l=16/xl=20/xxl=24）
- 卡片层次靠 `surfaceContainerLow/High` 色阶，**不靠阴影**（elevation 0）
- 亮暗模式**独立于系统**：以 App 内设置（DataStore）为准；开屏为无 logo 纯色（随软件内模式）

### Markdown
- 解析用 `org.commonmark:commonmark`，渲染用自研子集（`ui/editor/MarkdownText.kt`）——**不要**引入 mikepenz/richtext 等渲染库（Kotlin 2.x 兼容问题）
- 列表卡片预览必须走 `markdownToPlainText()` 剥离语法

## 已知坑点（踩过的）

1. **PowerShell 编码**：严禁用 `Get-Content`/`Set-Content`/`-Replace` 管道读写 UTF-8 源码文件（GBK 会毁掉中文/emoji）——一律用 Edit/Write 工具；批量替换需 `[System.IO.File]::ReadAllText/WriteAllText` + 显式 UTF8
2. **adb 掉线**：设备经常中途掉线，`Start-Sleep` 后重试即可，不要排查
3. **shell 中断**：命令被 kill 直接原样重试
4. **Compose API 位置**：`drawLayer` 在 `androidx.compose.ui.graphics.layer` 包；`DatePicker` 系列需 `@OptIn(ExperimentalMaterial3Api::class)`；`BoxWithConstraints` 在 `androidx.compose.foundation.layout`
5. **签名**：debug 与 release 签名不互通，切换安装需先 `adb uninstall com.example.diary`（会清数据，需告知）
6. **kapt**：Room 处理器对 DAO 中引用已删除类型敏感，删实体字段后全局 grep 残留引用
7. **stash**：`stash@{0}` 保存有已回滚的「倒数日 Days Matter 升级+图片编辑器」完整 WIP——恢复需 `git stash pop`（可能与新代码冲突）；确认不要可 `git stash drop`

## 工作流约定

- **先方案后编码**：大改动先输出详细方案（含数据模型/UI/边界情况），用户确认（「开工」）后再动手
- **测试驱动提交**：构建→安装到手机→用户实测确认→才 `git add/commit/push`
- **密钥安全**：`pocketdiary.jks`、`keystore.properties` 已 gitignore，**永远不得入库**；提交前 `git check-ignore` 复核
- 提交信息：中文、一行概括（分号分隔多点）；推送目标 `origin main`
