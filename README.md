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

### ⚙️ 设置
- 暗色 / 亮色模式切换
- 日记背景自定义与恢复默认

## 📥 下载安装

前往 [**Releases 页面**](https://github.com/Micheal-Reber/PocketDiary/releases) 下载最新的 `app-release.apk`，传输到手机直接安装（需允许安装未知来源应用）。

> 要求 Android 8.0（API 26）及以上。

## 🛠️ 技术栈

| 分类 | 方案 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3（动态取色） |
| 数据库 | Room（DiaryEntry / Habit / HabitRecord 三表） |
| 偏好 | DataStore Preferences |
| 导航 | Navigation Compose（底部三 Tab + 编辑器 + 统计页） |
| 架构 | ViewModel + Repository + Flow 单向数据流 |
| 天气 | Open-Meteo API（免 Key） |
| 定位 | 系统 LocationManager（无 Play Services 依赖） |
| 开屏 | androidx core-splashscreen |

## 🚀 构建

1. 安装 JDK 17 与 Android SDK（platform 35）
2. 配置环境变量 `JAVA_HOME`、`ANDROID_HOME`
3. 执行：

```bash
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 签名发布包（需配置签名密钥）
```

## 📂 项目结构

```
app/src/main/java/com/example/diary/
├── MainActivity.kt            # 入口：开屏接管 + 主题 Flow
├── DiaryApplication.kt
├── data/
│   ├── local/                 # Room 数据库、DAO、实体
│   ├── image/                 # 背景图导入与降采样解码
│   ├── location/              # 定位封装（无 GMS）
│   ├── preferences/           # DataStore 主题/背景偏好
│   ├── repository/            # 数据仓库层
│   └── weather/               # Open-Meteo 天气查询
└── ui/
    ├── diary/                 # 日历列表：卡片、月份分割、滑动删除、背景
    ├── editor/                # 日记编辑器：心情/天气/定位
    ├── habits/                # 打卡日历 + 统计图表 + ViewModel
    ├── navigation/            # 底部导航 + 路由
    ├── settings/              # 设置页
    └── theme/                 # Material 3 主题
```

## 📄 License

[MIT](LICENSE) © Micheal-Reber
