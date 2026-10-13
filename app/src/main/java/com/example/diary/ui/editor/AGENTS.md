# AGENTS.md — ui/editor（日记编辑器 + Markdown）

编辑器与 Markdown 渲染。父级约定（AGENTS.md 根）全部适用，不重复。

## 结构

- `DiaryEditorScreen.kt`：编辑器主体——日期胶囊、心情/天气 chips（共享 `PresetChipRow`）、定位、正文、保存/删除/改期；日期/删除/放弃弹窗走 `ui/components` 共享件
- `MarkdownText.kt`：`MarkdownText` 渲染器 + `markdownToPlainText` 预览剥离；解析器用 `buildMarkdownParser()`（**internal，单测复用同一套 extensions**）
- `DiaryContentView.kt`：图文混排（`[img:]` 标记切段 + 全屏查看 + 根部 `SelectionContainer` 长按选中）+ 公共 `PhotoFullscreenDialog`（编辑器缩略图也调它）
- `MarkdownEditing.kt`：编辑纯函数（**单测只测这一层**）——`TextFieldValueSaver` / `applyListContinuation` / `wrapOrUnwrap` / `toggleLinePrefix` / `insertAtCursor` / `insertBlockAtCursor`
- `MarkdownFormatBar.kt`：键盘上方格式条——`MarkdownAction` 枚举 + `MarkdownFormatBar(onAction)`；**它只发动作，不碰正文状态**

## WHERE TO LOOK

| 任务 | 位置 |
|------|------|
| 保存/改期/冲突提示 | `saveEntryAction` lambda（Scaffold 前）+ `DiaryRepository.saveEntry` |
| 未保存检测 | `Snapshot` data class + `isDirty`（**date 在快照内**：只改日期也算脏） |
| 预览切换 | 顶栏 👁 `showPreview`（rememberSaveable） |
| 日期选择 | `showDatePicker` → UTC 毫秒转 LocalDate（勿用 systemDefault） |
| 正文读写 | 读=`content`，写光标/插块/载入=`body`（`TextFieldValue`）；见「约定」第一条 |
| 回车自动续行 | `MarkdownEditing.kt` 的 `applyListContinuation`，挂在 TextField `onValueChange` |
| 格式条动作分发 | `DiaryEditorScreen` 的 `applyMarkdownAction` lambda（**文件底部，Scaffold 之前**） |
| 链接可点 / 预览可选中 | `MarkdownText.kt` 的 `Link` 分支（`LinkAnnotation.Url`）+ `DiaryContentView` 的 `SelectionContainer` |
| 表格 / 删除线 / 任务勾选 | `MarkdownText.kt` 的 `RenderTable` / `appendInline` 的 `Strikethrough` 分支 / `taskMarkerOf` |
| 图片全屏 / 占位 / 拍照 | `PhotoFullscreenDialog`（DiaryContentView.kt）+ `DiaryEditorScreen` 的 `ThumbState` 三态与 `launchCamera()` |

## 图片

- **正文图按原图比例显示**：`aspectRatio(bmp.width / bmp.height)` + `ContentScale.Fit`，**不要**写死 `aspectRatio(1.3f)` + `Crop`（会把竖图切头切脚）。缩略图条 88dp 方格仍用 `Crop`（方格里放原比例只会留白边）
- **加载/失败占位**：`ThumbState` 三态（`Loading` 转圈 / `Failed` `BrokenImage` / `Ready`），别再用 `bmp?.let {}` 让未加载时是透明空块
- **缩略图可点开全屏**：`Box` 挂 `clickable`，删除按钮**必须声明在后面**（内层 `IconButton` 先吃点击）
- **拍照走 `TakePicture` + FileProvider**：authority `${applicationId}.fileprovider`，暂存 `cacheDir/capture/`（`res/xml/file_paths.xml` 已加 `<cache-path name="capture">`），`captureUri` 用 `rememberSaveable` 存字符串防进程回收；成功复用 `importFromUri`（内建采样到 1600px）。**不申请 `CAMERA` 权限**（系统相机 Intent 自带）
- **导入态用 `importingSource: PhotoSource?` 而不是布尔**：相册/拍照两个按钮各自显示转圈，不能共用一个 `isImportingPhoto` 布尔（会不知道该在哪颗按钮上转圈）
- `MAX_PHOTOS_PER_ENTRY = 7`，两颗按钮与格式条图片键共用同一 `enabled` 条件

## 约定

- **所有表单状态必须 `rememberSaveable`**——旋转/进程恢复不丢输入；File 用路径字符串 saver
- **正文状态是 `var body: TextFieldValue`**，`val content = body.text` 是同一次组合里的**普通局部 val**。⚠️ **Kotlin 局部属性不能写 `get()`**（`val content get() = body.text` 直接语法错误），只能 `val content = body.text`；读取点（`photoNames` / `isDirty` / `Snapshot` / 预览）一律用 `content`，只有**载入、插图、按格式条**时才写 `body`。状态保存走 `TextFieldValueSaver`（listSaver 存 text + 两个光标下标），旋转后光标不丢
- **回车续行**：`applyListContinuation(old, new)` 返回 `null` = 不接管（用 `new` 原样）。只在「单个 `\n` 插入 + 选区塌缩 + 光标右移一位」时接管；空列表项回车 = 删掉该行退出列表；有序列表自动进位。**⚠️ `new.composition != null`（中文/日文 IME 正在上屏）一律放行**，否则输入法候选上屏会被二次处理打成空行
- **格式条时机**：`Scaffold.bottomBar` 里 `if (!showPreview)` **编辑态常显**（不看焦点，避免焦点切换导致底栏闪没）；Scaffold 根挂 `Modifier.imePadding()` 把底栏顶到键盘上方。图片按钮与顶栏 📷 走同一个 `pickPhotoLauncher`，插入统一用 `insertBlockAtCursor`（**插到光标处**，不再是追加到文末）
- **改日期 = 搬移语义**：改日期不清空内容、不弹放弃框，保存时按 id UPDATE；冲突（目标日已有日记）→ Snackbar + 日期回退
- `loadedForDate` 守卫 `LaunchedEffect`：非空 = 已从 rememberSaveable 恢复，跳过重载（防旋转覆盖输入）
- 保存成功后同步 `loadedSnapshot`（用原始值非 trim 值，保持 isDirty 一致性）
- Geocoder/定位必须在 `Dispatchers.IO`（网络阻塞调用）

## 已知限制

- **续行不做 fenced code block 感知**：代码块里行首 `- ` / `1. ` 仍会被当成列表续行。要做得在 `applyListContinuation` 里回扫 ``` 围栏计数（纯函数、可加测），目前按「手机日记很少写代码块」取舍
- **表格列宽均分 + 文字换行，不做横向滚动**：列宽靠 `Row(Modifier.fillMaxWidth())` + 单元格 `weight(1f)`，**这是刻意的**——`horizontalScroll` 会把行宽变成无界约束，`weight` 失效、各行各自按内容宽度算会导致**列对不上**。想加横滑得先统一所有行的宽度（例如 `Column(Modifier.width(IntrinsicSize.Max))`），别直接套 `horizontalScroll`
- **预览不渲染表格/删除线节点以外的 GFM 扩展**（无自动链接、无脚注、无告警块）；`markdownToPlainText` 已把表格行拍平成 `a · b`

## 反模式

- ❌ 引入 WYSIWYG Markdown 编辑库（compose-rich-editor 无法输原始语法、无代码块）——编辑=纯文本，预览=渲染
- ❌ 把 `content` 做成 `TextFieldValue` 派生的属性访问器（局部 `get()` 编译失败）；也别拆成多份状态，`Snapshot`/`photoNames` 全靠单一 `body` 派生
- ❌ 在 Main 线程调 Geocoder
- ❌ 吞 `CancellationException`（保存协程与定位协程被销毁时必须 rethrow——已覆盖保存与 Geocoder 分支）
