<div align="center">

# 📖 PocketDiary

**简洁好用的 Android 日记 App · 真液态玻璃 UI · 数据纯本地、零联网**

![Platform](https://img.shields.io/badge/platform-Android_8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white)
![Room](https://img.shields.io/badge/Room-v14-3DDC84)
![Version](https://img.shields.io/badge/version-1.9.1-blue)
![License](https://img.shields.io/badge/license-MIT-green)

[English](README.md) | **简体中文**

**[⬇️ 下载安装](#-下载安装)** · **[✨ 功能](#-功能一览)** · **[🛠️ 技术栈](#-技术栈)**

</div>

---

## 📸 界面预览

<table>
  <tr>
    <td align="center" width="33%">
      <img src="docs/screenshots/01-diary.jpg" width="260" alt="日记列表">
      <br><sub><b>日记</b> · 空状态 + 玻璃底栏/加号</sub>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/02-calendar.jpg" width="260" alt="日历打卡">
      <br><sub><b>日历打卡</b> · 统计入口 + 月视图</sub>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/03-countdown.jpg" width="260" alt="倒数日列表">
      <br><sub><b>倒数日</b> · 列表/网格双视图</sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="docs/screenshots/04-todo.jpg" width="260" alt="待办列表">
      <br><sub><b>待办</b> · 黑底列表 + 已完成折叠</sub>
    </td>
    <td align="center">
      <img src="docs/screenshots/05-settings.jpg" width="260" alt="设置页">
      <br><sub><b>设置</b> · 外观 / 隐私 / 数据迁移</sub>
    </td>
    <td align="center">
      <img src="docs/screenshots/06-editor.jpg" width="260" alt="日记编辑器">
      <br><sub><b>编辑器</b> · 心情 / 天气 / 定位 / 插图</sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="docs/screenshots/07-stats.jpg" width="260" alt="习惯统计">
      <br><sub><b>统计</b> · 周频率 / 月视图 / 年视图</sub>
    </td>
    <td align="center">
      <img src="docs/screenshots/08-countdown-edit.jpg" width="260" alt="新建倒数日">
      <br><sub><b>新建倒数日</b> · 经典/照片卡双风格</sub>
    </td>
    <td align="center">
      <img src="docs/screenshots/09-countdown-detail.jpg" width="260" alt="倒数日详情">
      <br><sub><b>倒数日详情</b> · 全屏大字 + 分享图</sub>
    </td>
  </tr>
</table>

---

## ✨ 功能一览

### 📖 日记
- **一天一篇**，按日期自动归档（`date` 唯一索引，改日期=搬移语义），月份大数字分割
- 记录**心情、天气、位置**——一键定位并反向解析地址，天气手动点选
- **Markdown 书写与预览**（commonmark 解析 + 自研子集渲染），**图文混排**最多 7 张插图
- **全文搜索**（250ms 防抖）、**左滑删除**（确认弹窗兜底）、卡片正文两行预览
- **自定义列表背景**：设置里导入照片，卡片自动 **backdrop 磨砂模糊**（GraphicsLayer + BlurEffect，API 31+）
- **日记密码锁**：4–6 位 PIN，三种验证时机（会话内 / 每次进日记 / 每天一次），改密码需验旧，SHA-256 盐值哈希，连错 5 次冷却 30s

### 📅 日历打卡（习惯）
- 月历视图 + 习惯打卡系统，日期格显示当天已打卡习惯的**彩色圆点**（最多 3 个）
- 点击日期弹窗逐项勾选，**可补打卡**，未来日期锁定；习惯名称与 emoji 完全自定义
- **统计页**三视图：**周频率**（最近十周滚动窗口）/ **月视图** / **年视图**
- 折线图每点带**数值标签**、当前周期高亮，底部勾选习惯控制曲线显隐

### ⏳ 倒数日
- **经典全屏 / 照片卡片**双风格；蓝橙徽章、置顶、重复（每年/每月）
- 正倒数三态判定（Today / Countdown / Countup，跨月跨闰年安全 +1 日）
- 分享图**离屏渲染**（1080×1440），每事件独立背景图；照片卡支持上下拖动取景
- 列表 / 网格双视图，过程式纹理背景

### ✅ 待办
- 黑底列表 + **已完成折叠**，勾选即下沉/回升；Sheet 直管编辑（无独立编辑页）
- **精确闹钟提醒**：通知模式 / 闹钟响铃模式（`setAlarmClock` 全屏 Intent 锁屏响铃），支持每天重复、稍后 5 分钟再响
- **开机、改时区、改时间自动重排**并补发错过的普通通知；MIUI 自启动检测与深链引导
- 通知/响铃点透深链直达待办条目（`open_todo_id`）

### ⚙️ 设置
- 暗色 / 亮色模式**独立于系统**，开屏纯色随软件内模式；**壁纸取色**（Material You）
- 隐私分区（密码锁）、数据迁移默认折叠、日记背景自定义与恢复默认
- **数据迁移**：导出 / 导入 ZIP（全量覆盖 + 事务回滚 + 版本闸门 + zip-slip/CRC 校验）
- 关于页版本号自动读 `BuildConfig.VERSION_NAME`

### 🎨 设计语言
- **真液态玻璃**：悬浮胶囊底栏 + 加号按钮实时取样背后内容做 backdrop 模糊
- 卡片层次靠 `surfaceContainerLow/High` 色阶，圆角/间距全部走令牌（无字面量）

## 📥 下载安装

前往 [**Releases 页面**](https://github.com/Micheal-Reber/PocketDiary/releases) 下载最新的 `app-release.apk`，传输到手机直接安装（需允许安装未知来源应用）。

> 要求 **Android 8.0（API 26）及以上**；backdrop 模糊等玻璃效果在 **Android 12（API 31）+** 上完整呈现。

## 🛠️ 技术栈

| 分类 | 方案 |
|------|------|
| 语言 | Kotlin 2.3.21 + Jetpack Compose BOM 2026.01.01（Compose 1.10.2 / M3 1.4） |
| UI | Jetpack Compose + Material 3，玻璃效果用 `GraphicsLayer` + `BlurEffect` |
| 数据库 | Room **v14**（DiaryEntry / Habit / HabitRecord / CountdownEvent / TodoItem 五表；v9→14 手写迁移链） |
| 偏好 | DataStore Preferences |
| 导航 | Navigation Compose（底部五 Tab + 编辑器 + 统计 + 倒数日子路由） |
| 架构 | ViewModel + Repository + Flow 单向数据流 |
| 定位 | 系统 LocationManager（无 Play Services 依赖） |
| 备份 | kotlinx-serialization JSON + 流式 ZIP（STORED CRC + 版本闸门） |
| 提醒 | AlarmManager（`setExactAndAllowWhileIdle` / `setAlarmClock`）+ 开机/改时区重排 |
| Markdown | org.commonmark 解析 + 自研子集渲染 |
| 处理器 | **KSP**（Room，非 kapt） |
| 开屏 | 系统开屏纯色化（无 logo，随软件内亮暗设置秒进界面） |

## 🚀 构建

1. 安装 **JDK 17** 与 Android SDK（platform 35）
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
├── MainActivity.kt            # 入口：亮暗模式接管（独立于系统）+ open_todo_id 深链
├── DiaryApplication.kt
├── receiver/                  # TodoAlarmReceiver / BootCompletedReceiver（闹钟 + 开机/改时区重排）
├── util/                      # DateUtils 共享日期/提醒格式化
├── data/
│   ├── backup/                # BackupData / ExportService / ImportService / BackupRepository
│   ├── countdown/             # DateMath + ShareCardRenderer（分享图）+ TextureLibrary（纹理）
│   ├── image/                 # BackgroundImageStore（相对路径）/ EventImageStore
│   ├── local/                 # Room v14：五实体 + DAO + MIGRATION_* 链
│   ├── location/              # 定位封装（无 GMS）
│   ├── photo/                 # DiaryPhotoStore 图文混排两阶段生命周期
│   ├── preferences/           # DataStore：亮暗 / 背景 / 壁纸取色 / 密码锁
│   ├── repository/            # 数据仓库层（SaveResult 日期冲突处理）
│   └── todo/                  # TodoReminderScheduler + TodoNotificationHelper + TodoAlarmNotifier（全屏响铃）
└── ui/
    ├── components/            # SharedUi：滑删/确认/搜索/日期选择/预设 chip（多屏共用）
    ├── countdown/             # 倒数日：列表/编辑/详情三屏 + 双卡片风格
    ├── diary/                 # 日记列表：月份分割、滑动删除、背景磨砂、全文搜索
    ├── editor/                # 编辑器：无边框书写、Markdown 预览、图文混排
    ├── habits/                # 打卡日历 + 统计图表（LineChart 自研）+ ViewModel
    ├── lock/                  # 密码锁键盘屏
    ├── navigation/            # 底部五 Tab + 路由 + 玻璃底栏/加号（GlassBottomBar）
    ├── settings/              # 设置页（数据迁移 + 关于读 BuildConfig）
    ├── theme/                 # Material 3 主题 + 圆角/间距令牌
    └── todo/                  # 待办列表 + 编辑/提醒 Sheet + 响铃页
```

## 🧪 测试

JVM 单测覆盖：`DateMathTest`（倒数日正倒判定）/ `TodoReminderSchedulerTest`（提醒调度）/ `BackupSerializationTest` + `BackupVersionGateTest`（备份序列化与版本闸门）/ `TodoRepositoryTest`（Robolectric）/ `BlurCacheTest`（模糊缓存等级）：

```bash
./gradlew test
```

## 📄 License

[MIT](LICENSE) © Micheal-Reber
