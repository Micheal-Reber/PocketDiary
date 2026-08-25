# AGENTS.md — ui/habits（打卡日历 + 统计）

打卡日历、习惯管理与统计图表。父级约定（AGENTS.md 根）全部适用，不重复。

## 结构

- `HabitsScreen.kt`：页面骨架（顶栏/统计入口/FAB/弹窗编排）
- `HabitsViewModel.kt`：打卡状态 + 统计数据（按需加载）
- `CalendarComponents.kt`：月历格子（多习惯彩点）、月头、星期头
- `StatisticsSection.kt` / `StatisticsScreen.kt`：统计页（周/月/年三视图 + 勾选联动）
- `LineChart.kt`：自研折线图（`habitColor` 配色全局引用）
- `HabitDialogs.kt`：打卡/添加/管理弹窗

## WHERE TO LOOK

| 任务 | 位置 |
|------|------|
| 统计查询 | `HabitsViewModel.loadAllStats / loadMonthlyStats / loadDailyStats`（按需，勿全量） |
| 折线图渲染 | `LineChart.kt`（数值标签已 `remember(lines, step)` 预计算——勿移回 draw 内） |
| 习惯配色 | `habitColor(index)`（取模安全，全局引用勿改） |
| 打卡切换 | `HabitsViewModel.toggleHabitOnDate`（后按需刷新：日历点+全量统计+今日计数） |

## 约定

- 统计数据集**按需加载**：切年只查 `monthlyStats`、切月只查 `dailyStats`；记录变动才 `loadAllStats()`
- 折线图数值标签已预计算——新增绘制逻辑勿在 draw 内做文本 measure
- 日历格彩点上限 3 个/天（`CalendarGrid`），图例竖排列出全部习惯
- 勾选习惯控制曲线显隐：`habits.filter { it.id in selectedHabitIds }`

## 反模式

- ❌ 恢复已删除的「倒数日」（曾短暂存在，已整体移除——WIP 在根仓库 `stash@{0}`）
- ❌ 在 `LineChart` draw 作用域内调用 `textMeasurer.measure`（每帧重测）
