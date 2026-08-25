# AGENTS.md — ui/editor（日记编辑器 + Markdown）

编辑器与 Markdown 渲染。父级约定（AGENTS.md 根）全部适用，不重复。

## 结构

- `DiaryEditorScreen.kt`（~560 行）：编辑器主体——日期胶囊、心情/天气 chips、定位、正文、保存/删除/改期
- `MarkdownText.kt`：`MarkdownText` 渲染器 + `markdownToPlainText` 预览剥离

## WHERE TO LOOK

| 任务 | 位置 |
|------|------|
| 保存/改期/冲突提示 | `saveEntryAction` lambda（Scaffold 前）+ `DiaryRepository.saveEntry` |
| 未保存检测 | `Snapshot` data class + `isDirty`（**date 在快照内**：只改日期也算脏） |
| 预览切换 | 顶栏 👁 `showPreview`（rememberSaveable） |
| 日期选择 | `showDatePicker` → UTC 毫秒转 LocalDate（勿用 systemDefault） |

## 约定

- **所有表单状态必须 `rememberSaveable`**——旋转/进程恢复不丢输入；File 用路径字符串 saver
- **改日期 = 搬移语义**：改日期不清空内容、不弹放弃框，保存时按 id UPDATE；冲突（目标日已有日记）→ Snackbar + 日期回退
- `loadedForDate` 守卫 `LaunchedEffect`：非空 = 已从 rememberSaveable 恢复，跳过重载（防旋转覆盖输入）
- 保存成功后同步 `loadedSnapshot`（用原始值非 trim 值，保持 isDirty 一致性）
- Geocoder/定位必须在 `Dispatchers.IO`（网络阻塞调用）

## 反模式

- ❌ 引入 WYSIWYG Markdown 编辑库（compose-rich-editor 无法输原始语法、无代码块）——编辑=纯文本，预览=渲染
- ❌ 在 Main 线程调 Geocoder
- ❌ 吞 `CancellationException`（保存协程被销毁时必须 rethrow）
