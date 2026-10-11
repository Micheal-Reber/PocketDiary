# AGENTS.md — PocketDiary 开发指引

**Updated:** 2026-10-11 · **Version:** 1.11.0 (code 18) · **Branch:** main

本文件供 AI 编码代理（及新成员）快速了解本项目的构建方式、架构约定与历史坑点。

## 项目概览

**PocketDiary** — 极简 Android 日记 App。纯本地存储、零联网依赖（无账号/云同步）。

- **语言/UI**: Kotlin 2.3.21 + Jetpack Compose (BOM 2026.01.01, Compose 1.10.2, M3 1.4) + Material 3
- **液态玻璃**: `io.github.kyant0:backdrop:1.0.6`（Kyant0/AndroidLiquidGlass，Apache-2.0；README 已鸣谢，**改玻璃效果必须鸣谢该库**）
- **数据库**: Room v14（v9→11 / v10→11 / v11→12→13→14 有手写迁移；v8 及更早仍破坏性回退会清数据）
- **偏好**: DataStore Preferences（`ThemePreferences`：暗色 / 壁纸取色 / 日记背景 / 编辑器预览 / **floatingBar 底栏模式**；`LanguagePreferences` 语言；`AppLockPreferences` 密码锁）
- **SDK**: minSdk 26 / target 35 / compile 36 / JDK 17
- **当前版本**: 1.11.0 (code 18)
- **工具链**: AGP 8.13.2 / Gradle 8.14.3 / KSP 2.3.12 / Room 2.8.5（schema 走 Room Gradle 插件 `room { schemaDirectory }`）

## 构建与安装

所有 gradle/adb 命令需先设置环境变量（PowerShell）：

```powershell
$env:JAVA_HOME = "E:\dev\jdk-17"
$env:ANDROID_HOME = "E:\dev\android-sdk"
$env:GRADLE_USER_HOME = "E:\dev\.gradle"
.\gradlew.bat test             # JVM 单元测试（发版前必跑）
.\gradlew.bat assembleRelease  # 签名发布包（本地测试也用这个，与手机上已装签名一致）
.\gradlew.bat assembleDebug    # 调试包（与 release 签名不互通，覆盖安装需先卸载）
```

- adb: `E:\dev\android-sdk\platform-tools\adb.exe`
- 测试机: 小米 23116PN5BC（USB 连接不稳定，掉线后等几秒重试即可）
- 日志/截图临时目录：`C:\Users\wangj\AppData\Local\Temp\opencode\`
- **Gradle 输出必须重定向到文件再看**（否则工具输出被截断看不到失败原因）：
  `cmd /c ".\gradlew.bat test assembleRelease > C:\Users\wangj\AppData\Local\Temp\opencode\build.log 2>&1"`，再判 `$LASTEXITCODE`；失败时 `Get-Content … -Tail 40`
- 装机：`adb.exe install -r app\build\outputs\apk\release\app-release.apk`；设备掉线就 `wait-for-device` + 循环重试
- 截图：`cmd /c "`"$adb`" exec-out screencap -p > file.png"`——**截到黑图/锁屏不代表应用异常，必须实际 read 图片确认**；唤醒用 `input keyevent KEYCODE_WAKEUP` + `wm dismiss-keyguard`
- **未经用户同意不要注入 `adb shell input tap/swipe` 操作手机界面**（只做只读截图/安装）；需要交互验证（如切英文、切停靠模式）请让用户手动切换
- 用户随时会切走应用（微信/锁屏），截图前先确认前台是本应用

### 发版流程

1. 改 `app/build.gradle.kts` 的 `versionName`/`versionCode`（**唯一来源**，同步 bump Room schema 变更时的 `versionCode`）
2. **设置页「关于」自动读 `BuildConfig.VERSION_NAME`**（勿手写死字符串）
3. `test` + `assembleRelease` 全绿 → 装机 → 用户实测
4. `git commit`（中文一行、分号分隔）→ `git push origin main`
5. **发布/替换安装包须用户确认**：`gh release upload v<版本> app\build\outputs\apk\release\app-release.apk --clobber --repo Micheal-Reber/PocketDiary`（`--clobber` 用于替换同一 Release 的旧包）；`gh release view <tag> --json assets` 核对 digest

- 单测清单：`DateMathTest` / `TodoReminderSchedulerTest` / `BackupSerializationTest` / `BackupVersionGateTest` / `TodoRepositoryTest`(Robolectric) / `BlurCacheTest`

## 架构

```
app/src/main/java/com/example/diary/
├── MainActivity.kt         # 亮暗模式独立于系统（同步读 DataStore → setTheme 变体）+ open_todo_id 深链
├── DiaryApplication.kt
├── receiver/               # TodoAlarmReceiver / BootCompletedReceiver（闹钟触发 + 开机/改时区重排）
├── util/                   # DateUtils：共享日期/提醒格式化（勿再各屏复制 formatter）
├── data/
│   ├── backup/             # 数据导出/导入（手机迁移）：BackupData/ExportService/ImportService/BackupRepository
│   ├── countdown/          # DateMath 正倒判定纯函数 + ShareCardRenderer 分享图 + TextureLibrary 纹理
│   ├── image/              # BackgroundImageStore（日记背景，相对路径）/ EventImageStore（倒数日每事件背景）
│   ├── local/              # Room: DiaryEntry / Habit / HabitRecord / CountdownEvent / TodoItem（version 14）
│   ├── location/           # LocationManager 封装（无 GMS）
│   ├── photo/              # DiaryPhotoStore：日记图文混排两阶段生命周期
│   ├── preferences/        # DataStore 三个文件：ThemePreferences（暗色/壁纸取色/编辑器预览/floatingBar）+ LanguagePreferences（语言）+ AppLockPreferences（密码锁）
│   ├── repository/         # 薄仓库层（SaveResult 密封类处理日期冲突）
│   └── todo/               # TodoReminderScheduler(AlarmManager/setAlarmClock) + TodoNotificationHelper + TodoAlarmNotifier(全屏响铃)
└── ui/
    ├── components/         # SharedUi：SwipeDelete/Confirm/Search/UtcDatePicker/PresetChip（多屏共用）
    ├── countdown/          # 倒数日：列表/编辑/详情 三屏 + 共享件（双卡片风格：CLASSIC / PHOTO_CARD）
    ├── diary/              # 日记列表：月份分割、滑动删除、自定义背景、全文搜索（250ms 防抖）
    ├── editor/             # 编辑器：无边框书写、Markdown 预览、📷插图（日期/chips 无描边）
    ├── habits/             # 打卡日历 + 统计图表（LineChart 自研）
    ├── navigation/
    │   ├── AppNavigation.kt        # 五 Tab + 子路由；**双模分支**（floatingBar 悬浮玻璃 / 原生 NavigationBar）+ 提供 LocalBottomBarInset/LocalFloatingBar
    │   ├── LiquidGlassBottomBar.kt # 液态玻璃胶囊底栏（三层玻璃 + 拖动跟手 + 速度挤压）
    │   ├── GlassSurface.kt         # 玻璃基建：GlassCapsule/GlassFab/glassContainerColor/FloatingBottomInset/DockedBottomBarHeight
    │   └── liquid/                 # 手感实现：DockTouchState / DampedDragAnimation / DragGestureInspector / InteractiveHighlight
    ├── settings/           # 设置页（数据迁移 ZIP + 关于读 BuildConfig + 底栏模式开关 + 语言切换）
    ├── todo/               # 待办：TodoListScreen(黑底+已完成折叠+黄FAB) + TodoEditSheet + ReminderTimeSheet + TodoRingActivity(响铃页)
    └── theme/              # Material 3 主题 + Spacing/AppShapes 令牌
```

## WHERE TO LOOK

| 任务 | 位置 | 备注 |
|------|------|------|
| 底部导航栏（悬浮/停靠双模） | `ui/navigation/AppNavigation.kt` + `LiquidGlassBottomBar.kt` + `GlassSurface.kt` | `ThemePreferences.floatingBar` 默认 true；停靠态=原生 `NavigationBar`+`NavigationBarItem` |
| 底栏拖动手感 | `ui/navigation/liquid/*` | `DockTouchState`（命中格与指示器坐标解耦）/ `DampedDragAnimation`（带速重定向）/ `DragGestureInspector`（consumeChanges）/ `InteractiveHighlight`（radialGradient 光晕） |
| 玻璃卡片/胶囊/悬浮按钮 | `GlassSurface.kt` 的 `GlassCapsule`/`GlassFab` | backdrop 1.0.6：`drawBackdrop(highlight/shadow/innerShadow)` + `blur` + `lens` |
| 内容底部避让 | `LocalBottomBarInset`（`GlassSurface.kt` 提供，`AppNavigation` 注入） | 全屏/经典内容用 `BottomBarContentInset` 垫底，否则最后一行被底栏遮挡 |
| 日记编辑/保存/改期 | `ui/editor/DiaryEditorScreen.kt` + `DiaryRepository.saveEntry` | 改日期=搬移语义 |
| Markdown 渲染/预览 | `ui/editor/MarkdownText.kt` | commonmark 解析 + 自研子集渲染 |
| 全文搜索 | `DiaryDao.searchEntries` + `ui/diary/DiaryListScreen.kt` | **250ms 防抖**后再查（勿改成每次按键直查） |
| 数据迁移导出/导入 | `data/backup/BackupRepository` + `SettingsScreen` | 版本闸门 `1..CURRENT_VERSION`；zip-slip/CRC/条目上限；导入=全量覆盖+事务 |
| 日记背景相对路径 | `BackgroundImageStore.normalizeStoredPath/RELATIVE_PATH` | 备份与导入必须归一，绝对路径换机悬空 |
| 统计图表 | `ui/habits/LineChart.kt` + `StatisticsSection.kt` | 数值标签预计算（remember） |
| 打卡日历 | `ui/habits/CalendarComponents.kt` + `HabitsViewModel.kt` | 多习惯彩点、月份分割 |
| 倒数日正倒判定 | `data/countdown/DateMath.kt` + 单测 | 三态 Today/Countdown/Countup；+1日；重复滚动；**改动必跑 JUnit** |
| 倒数日界面 | `ui/countdown/*Screen.kt` + `CountdownUi.kt` | 色板下标 0=自动(倒蓝/正橙)；过程式纹理 TextureBackdrop |
| 分享图生成 | `data/countdown/ShareCardRenderer.kt` | 纯 android.graphics 离屏 1080×1440；FileProvider 在 Manifest |
| 亮暗/开屏 | `Theme.kt` + `themes.xml` + `MainActivity.kt` | 独立于系统 |
| 背景图缓存 | `data/image/BackgroundImageStore.kt` | 覆盖同名文件后必须 `clearCache()`；倒数日每事件图走 EventImageStore |
| 待办列表/编辑 | `ui/todo/TodoListScreen.kt`(黑底+已完成折叠) + `TodoEditSheet.kt`(图1底板) + `ReminderTimeSheet.kt`(图2日历) | 勾选下沉/回升、设置提醒胶囊→日历、橙色完成、黄FAB；重构后无独立编辑页（Sheet直管） |
| 待办数据层 | `data/local/TodoItem.kt` + `TodoDao.kt` + `TodoRepository.kt` | 单表 todo_items，字段：id/text/done/sortOrder/createdAt/reminderAt/repeatRule/alarmMode(0=通知,1=闹钟响铃)；DAO: observeAll/getAll/getDueReminders；**save/delete 内同步闹钟**（UI 不再直接调 Scheduler），`updateReminder` 已删 |
| 待办提醒调度 | `data/todo/TodoReminderScheduler.kt` + `TodoNotificationHelper.kt` + `TodoAlarmNotifier.kt` + `receiver/TodoAlarmReceiver.kt` + `BootCompletedReceiver.kt` | 通知模式 setExactAndAllowWhileIdle；**闹钟模式(alarmMode=1) 走 setAlarmClock（Doze 豁免）→ `TodoAlarmNotifier` 全屏 Intent 拉起 `TodoRingActivity` 循环铃声+震动**；requestCode=`id xor (id ushr 32)` 防截断；重复按日历日+1（DST 安全）；`snooze()` 5 分钟后重响（snoozed 标记不推进重复规则）；BOOT/MY_PACKAGE_REPLACED/TIME_SET/TIMEZONE_CHANGED 全量重排 + 补发错过的非重复通知；通知带 `open_todo_id` → 待办 Tab；精确闹钟/全屏 Intent 无权限时 UI SnackBar 深链 |

## CODE MAP

| 符号 | 类型 | 位置 | 角色 |
|------|------|------|------|
| `LiquidGlassBottomBar` | @Composable | ui/navigation/LiquidGlassBottomBar.kt | 悬浮玻璃胶囊底栏：外框 vibrancy+blur(8)+lens(24,24) / 染色 ghost / 折射指示器（Highlight+Shadow+InnerShadow）+ 按压透镜 + 速度挤压 |
| `DockTouchState` | class | ui/navigation/liquid/DockTouchState.kt | 拖动命中格索引与指示器位置**解耦**（解决拖到一半判定跳格） |
| `DampedDragAnimation` | class | ui/navigation/liquid/DampedDragAnimation.kt | 弹簧/阻尼跟随：`initialVelocity` + `CoroutineStart.UNDISPATCHED` + `gestureActive` + `animatedTarget` + Job 化取消 + `releaseAfterSettle()` |
| `DragGestureInspector` | fun | ui/navigation/liquid/DragGestureInspector.kt | `inspectDragGestures(consumeChanges)` 统一手势层归属（固定输入层，不与 tab clickable 抢事件） |
| `InteractiveHighlight` | class | ui/navigation/liquid/InteractiveHighlight.kt | 按压 radialGradient 光晕 + 由指示器中心派生 |
| `GlassCapsule` / `GlassFab` | @Composable | ui/navigation/GlassSurface.kt | 玻璃胶囊 / 悬浮按钮；`glassContainerColor()` = surfaceContainer α0.40（**@Composable，必须在 lambda 外算好捕获**） |
| `LocalBottomBarInset` / `LocalFloatingBar` | CompositionLocal | ui/navigation/GlassSurface.kt | 双模底栏的内容避让与模式广播（倒数日详情页据此选胶囊 or 原生栏） |
| `DiaryRepository.saveEntry` | suspend | data/repository | 日记唯一写入口（插入/更新/搬移/冲突判定） |
| `BackgroundImageStore.decode` | suspend | data/image | 内存缓存解码（path+mtime+maxDim 键） |
| `markdownToPlainText` | fun | ui/editor/MarkdownText.kt | 列表预览语法剥离 |
| `HabitsViewModel.loadAllStats` | private | ui/habits/HabitsViewModel.kt | 四数据集全量刷新入口 |
| `habitColor` / `HabitColorPalette` | fun/val | ui/habits/LineChart.kt | 习惯配色（全局引用） |
| `AppShapes` / `Spacing` | val | ui/theme | 圆角/间距令牌（禁止字面量） |
| `TodoRepository.save` | suspend | data/repository | 待办插入/更新 + **内部 schedule/cancel 闹钟**（带 context 构造时） |
| `TodoDao.observeAll` | Flow<List<TodoItem>> | data/local | 待办列表实时观察（按 sortOrder 排序） |
| `TodoReminderScheduler.schedule` | fun | data/todo | 精确闹钟（alarmMode=1 用 setAlarmClock）；过期不排、done 取消；重复走 `nextDailyOccurrence` 日历日+1 |
| `TodoReminderScheduler.snooze` | fun | data/todo | 稍后提醒：默认 +5min，snoozed extra 让接收器只响铃不推进重复规则 |
| `TodoReminderScheduler.requestCodeFor` | fun | data/todo | `(id xor (id ushr 32)).toInt()` —— 防 `toInt()` 截断撞号 |
| `TodoNotificationHelper.show` | fun | data/todo | 高优通知（BigText，点穿透 `open_todo_id` 至待办 Tab）——通知模式 |
| `TodoAlarmNotifier.show` | fun | data/todo | 闹钟模式：`todo_alarm` 渠道（闹钟铃声+长震动+免打扰穿透）+ 全屏 Intent；silent=true 用于响铃页退后台的回入口 |
| `SwipeDeleteCard` / `ConfirmDialog` / `SearchTextField` / `UtcDatePickerDialog` / `PresetChipRow` | fun | ui/components/SharedUi.kt | 列表滑删/确认弹窗/搜索框/UTC 日期/心情天气 chip 行（日记+待办+倒数日+编辑器共用；chip **无描边** `border = null`） |
| `BackupRepository.export/importData` | suspend | data/backup | 迁移唯一入口（Uri SAF）；内部走 Export/ImportService |
| `BackgroundImageStore.normalizeStoredPath` | fun | data/image | 绝对路径 → filesDir 相对（备份/导入用） |
| `DateUtils.format*` | fun | util | 日期/提醒共享格式化——**新屏必须复用，勿复制** |

## 关键约定（务必遵守）

### 数据库
- **日期一律存 `yyyy-MM-dd` 字符串**（`LocalDate.toString()`），解析用 `LocalDate.parse`
- 日记一天一篇：`diary_entries.date` 有 UNIQUE 索引；保存走 `DiaryRepository.saveEntry`
  - id==0 → 按日期查重后插入；id!=0 → 按 id 整条 UPDATE（改日期=搬移，冲突返回 `SaveResult.DateConflict`）
- **schema 变更 → version +1**（v1.2→4；v1.7→8；v1.8→9 新增 reminderAt/repeatRule；当前 14）；同步 bump `versionCode`；**提供 Migration**（见 `AppDatabase` 的 MIGRATION_* 链），仅无历史 schema 的旧版本才走 `fallbackToDestructiveMigration()`（会清数据，需告知用户）
- DAO 查询只写必要字段；统计查询按需加载（切年只查月统计、切月只查日统计）
- **新表只加不改旧表**：新增 `todo_items` 表不影响现有 Diary/Habit/Countdown 表

### 版本号
- **唯一来源**：`app/build.gradle.kts` 的 `versionName`/`versionCode`
- 设置页「关于」读 `BuildConfig.VERSION_NAME`（`buildFeatures.buildConfig = true`）——**改版本时不要另改 Settings 里的字符串**，避免两处不一致

### 主题 / UI
- **所有圆角走 `MaterialTheme.shapes`**（AppShapes: 8/12/16/28/32），**禁止** `RoundedCornerShape(字面量)`——**唯一例外：胶囊/药丸形状用 `RoundedCornerShape(50.dp)`**（玻璃胶囊底栏、GlassCapsule、指示器 pill）
- 间距用 `ui/theme/Spacing.kt` 令牌（xs=4/s=8/m=12/l=16/xl=20/xxl=24）
- 卡片层次靠 `surfaceContainerLow/High` 色阶，**不靠阴影**（elevation 0）
- 亮暗模式**独立于系统**：以 App 内设置（DataStore）为准；开屏为无 logo 纯色（随软件内模式）
- **底栏双模**：悬浮玻璃胶囊（默认）/ 经典停靠原生 `NavigationBar`；由 `ThemePreferences.floatingBar` + 设置页开关控制；内容避让走 `LocalBottomBarInset`
- **标签防换行**：底栏 Tab 文案 `fontSize = 10.sp`、`letterSpacing = 0.sp`、`maxLines = 1`、`softWrap = false`——英文 "Countdown" 默认 `labelMedium` 会折成两行并**把图标顶高**；**不要用 Ellipsis 省略**（用户要求整词完整显示）

### 液态玻璃（backdrop 1.0.6）
- **API 面已核实**：1.0.6 自带 `drawBackdrop(highlight/shadow/innerShadow)`、`Highlight`/`Shadow`/`InnerShadow`、`rememberCombinedBackdrop`、`colorControls`、`drawPlainBackdrop`——**不需要升级到 2.x**（本地参考源码在 `E:\AndroidLiquidGlass-kmp\AndroidLiquidGlass-kmp`，是 2.0.1，仅供读源码，别照抄 API）
- 玻璃配方（胶囊）：`RoundedCornerShape(50.dp)` + `blur(8.dp)` + `lens(24.dp, 24.dp)` + `glassContainerColor()`（surfaceContainer α0.40）
- **降级**：`lens` 需 API33、`blur` 需 API31+，minSdk 26 由库内部自动降级，不要自己写版本分支
- `glassContainerColor()` 是 `@Composable`——**必须在 `drawBackdrop` lambda 外先算好并捕获**，写在里面会编译失败
- 图标/文字间距与倒数日 `ActionItem` 保持一致：`Arrangement.Center` + `Spacer(Spacing.xs)` + `lineHeight = 12.sp`
- 手感三件套：命中格与指示器坐标**必须解耦**（`DockTouchState`）；重定向要带 `initialVelocity` + `UNDISPATCHED`；手势监听放在**固定输入层**且 `consumeChanges`，不要挂在会动的 tab 上
- **鸣谢**：README.md / README.zh-CN.md 已列 Kyant0/AndroidLiquidGlass 与 perchA5uka/Niriko——新增玻璃实现/移植其代码时保持鸣谢

### Markdown
- 解析用 `org.commonmark:commonmark`，渲染用自研子集（`ui/editor/MarkdownText.kt`）——**不要**引入 mikepenz/richtext 等渲染库
- 列表卡片预览必须走 `markdownToPlainText()` 剥离语法

### 代码风格
- **不写新代码注释**（除用户明确要求）
- 命名/风格跟随邻近文件；改 UI 前先读相邻实现再动手

## 已知坑点（踩过的）

1. **PowerShell 编码**：严禁用 `Get-Content`/`Set-Content`/`-Replace` 管道读写 UTF-8 源码文件（GBK 会毁掉中文/emoji，`Select-String` 读中文也是乱码）——一律用 read/edit/write 工具；批量替换需 `[System.IO.File]::ReadAllText/WriteAllText` + 显式 UTF8
2. **adb 掉线**：设备经常中途掉线，`Start-Sleep`/`wait-for-device` 后重试即可，不要排查
3. **shell 中断**：命令被 kill（`ChildProcess.kill`）直接原样重试
4. **Gradle 输出**：不重定向到文件看不到失败原因；重定向后判 `$LASTEXITCODE`，别只看有没有输出
5. **截图误判**：黑屏/锁屏截图 ≠ 应用崩了，必须 read 图片实际看；用户会随时切走应用，截图前确认前台
6. **Compose API 位置**：`drawLayer` 在 `androidx.compose.ui.graphics.layer` 包；`DatePicker` 系列需 `@OptIn(ExperimentalMaterial3Api::class)`；`BoxWithConstraints` 在 `androidx.compose.foundation.layout`；`Offset.VisibilityThreshold` 需 `import androidx.compose.animation.core.VisibilityThreshold`
7. **底栏悬空（踩过）**：底栏根节点 `BoxWithConstraints` **只写 `fillMaxWidth()` 不写 `.height(BarHeight)`**，子 `Row.fillMaxSize()` 会吃满全屏导致图标垂直居中、底栏浮在屏幕中间。改底栏尺寸必须同时核对根节点高度约束
8. **英文长标签**：`NavigationBarItem` 默认 label 换行会把图标顶高（见「主题 / UI · 标签防换行」）
9. **签名**：debug 与 release 签名不互通，切换安装需先 `adb uninstall com.example.diary`（会清数据，需告知）
10. **KSP/Room**：Room 处理器对 DAO 中引用已删除类型敏感，删实体字段后全局 grep 残留引用
11. **嵌套密封类型引用**：`DateMath.CountState.Today` 必须带完整嵌套路径或 `import DateMath.CountState`——裸写 `DateMath.Today` 不解析（踩过）
12. **lerp 重载歧义**：`androidx.compose.ui.util.lerp` 的 Float/Color 重载易歧义，Color 版用全限定名
13. **Todo 列表交互**：彻底重构后为黑底+灰卡+折叠已完成（图3），勾选即下沉/回升，无拖拽；旧 `dragAndDrop/ SwipeToDismiss` 已移除
14. **Todo 提醒**：`POST_NOTIFICATIONS` (33+) 需运行时申请、`SCHEDULE_EXACT_ALARM` 在 S+ 需 `canScheduleExactAlarms()` 检测否则降级 `setAndAllowWhileIdle` 并 SnackBar 深链；14+ 闹钟模式需 `canUseFullScreenIntent()` 检测否则降级为响一次通知（`ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` 深链）；`BOOT_COMPLETED`/`MY_PACKAGE_REPLACED`/`TIME_SET`/`TIMEZONE_CHANGED` 重排 + 补发错过的非重复通知（错过只补普通通知，不补响铃）；过期非重复不排；**闹钟调度只走 `TodoRepository.save/delete`**，UI 勿再直接调 Scheduler
15. **Room 版本号同步**：每次 schema 变更必须同时更新 `AppDatabase.version` 和 `build.gradle.kts` 的 `versionCode`，两者保持同步（v9=1.8, v11=1.8.2/code 10, v14=1.9.0/code 13）；升级路径写 `addMigrations`，勿只靠破坏性回退；schema JSON 在 `app/schemas/…/14.json` **必须入库**
16. **Robolectric**：4.11 最高官方 SDK 34，而 targetSdk=35——用 Robolectric 的测试类必须 `@Config(sdk = [34])`，否则 `Package targetSdkVersion=35 > maxSdkVersion=34` 初始化失败
17. **备份安全**：导入必须先过版本闸门（`version < 1 || > CURRENT_VERSION` → Failure）；ZIP 解压防滑移（禁 `..`）；STORED 需 CRC 校验；日记背景路径入库前 `normalizeStoredPath`
18. **Kotlin DSL 签名配置**：`build.gradle.kts` 里用 `java.util.Properties` 必须文件头 `import java.util.Properties`（脚本内 `java.util` 会 Unresolved）

## 工作流约定

- **先方案后编码**：大改动先输出详细方案（含数据模型/UI/边界情况），用户确认（「开工」）后再动手
- **测试驱动提交**：构建 → 装机 → 用户实测确认 → 才 `git add/commit/push`
- **不主动提交/推送/发版**：除非用户明确要求
- **提交信息**：中文、一行概括（分号分隔多点）；推送目标 `origin main`；`.omo/`（plans/drafts）**不入库**
- **发版须用户确认**：上传/替换 GitHub Release 附件前先问
- **密钥安全**：`pocketdiary.jks`、`keystore.properties` 已 gitignore；`build.gradle.kts` **只读** `keystore.properties`（不写死密码）。**注意：git 历史曾 4 次误提交密钥 blob（2f41123 等），工作树已删但历史仍可达——如需彻底清除需 `git filter-repo` 重写历史（须用户确认）**；建议换新 keystore。提交前 `git check-ignore` 复核
- **KSP**：Room 走 `ksp`（非 kapt）；schema 导出 `app/schemas/…/14.json`（需入库以便迁移测试）
