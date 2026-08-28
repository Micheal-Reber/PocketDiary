# Countdown 卡片风格增强：统一纹理库 + CLASSIC 字色开关 + PHOTO_CARD 无图纹理支持

## TL;DR
将现有 4 种手绘 Canvas 纹理升级为 12 种 Compose Shader 纹理，统一两种卡片风格（CLASSIC/PHOTO_CARD）共享；CLASSIC 新增 `fontDark` 开关（黑/白字），PHOTO_CARD 无图模式复用纹理库；编辑页统一入口。

## 背景
- **CLASSIC**：目前仅 4 种纹理（点阵/方格/斜纹/波纹），字色跟随系统主题，深色照片/纹理上白字对比度不足
- **PHOTO_CARD**：有 `fontDark` + `blurRadius`，但无图时 fallback 为纯色分段框，未复用纹理
- **编辑页**：字色开关仅在 PHOTO_CARD 显示，纹理选择仅在 CLASSIC 显示

## 实施范围

### 必须有
1. **TextureLibrary.kt** — 12 种 Compose Shader 纹理（含现有 4 种迁移 + 8 种新增）
2. **CLASSIC 字色支持** — `ClassicFullscreenContent` 读取 `fontDark`，文字色 = `if (fontDark) Black else White`
3. **CLASSIC 底表** — `ClassicBackgroundSheet` 纹理网格改用 TextureLibrary 预览组件
4. **PHOTO_CARD 无图模式** — `PhotoCardContent` 无图分支：Header(事件色) + Body(TextureLibrary 纹理/渐变) + Footer
5. **PHOTO_CARD 底表** — 无图时显示纹理选择器（复用 CLASSIC 逻辑）
6. **编辑页统一** — 进阶区：两种风格都显示「字色开关」；纹理选择器在 CLASSIC/无图 PHOTO_CARD 均显示
7. **分享图同步** — `ShareCardRenderer.render()` 新增 `fontDark` 参数，CLASSIC 分享图按 `fontDark` 定字色

### 不做
- ❌ 纹理动画/交互
- ❌ 自定义纹理导入
- ❌ 数据库迁移（`fontDark` 已存在，默认 false；`textureIndex` 复用现有字段）

## 架构变更

### 新文件
```
data/countdown/
  TextureLibrary.kt          # 12 种纹理 Shader 定义 + 预览组件
```

### 修改文件
- `Entities.kt` — fontDark 注释改为通用
- `CountdownUi.kt` — `TextureBackdrop` 改用 TextureLibrary，保留同名函数签名兼容
- `CountdownDetailScreen.kt` — ClassicFullscreenContent/PhotoCardContent/两个 BottomSheet 均接入 fontDark + TextureLibrary
- `CountdownEditScreen.kt` — 进阶区统一显示字色开关 + 纹理选择器（条件显示）
- `ShareCardRenderer.kt` — `render()` 新增 `fontDark`，CLASSIC 分支按参数定字色

## 纹理库设计 (TextureLibrary.kt)

### 12 种纹理
| 索引 | 名称 | 类型 | 描述 |
|-----|------|------|------|
| 0 | 点阵 | Shader | 原有：大圆点阵，改用 `DrawScope.drawCircle` 循环 → `ComposeShader` |
| 1 | 方格纸 | Shader | 原有：横竖线网格 |
| 2 | 斜纹 | Shader | 原有：宽斜纹带 |
| 3 | 波纹 | Shader | 原有：粗波浪 |
| 4 | 细噪点 | Shader | 高频白噪点，`perlinNoise` 近似或随机圆点 |
| 5 | 斜格子 | Shader | 45° 菱形网格 |
| 6 | 六边形 | Shader | 蜂窝六边形网格 |
| 7 | 径向渐变波纹 | Shader | 从中心向外的同心圆波纹 |
| 8 | 双色斜条 | Shader | 两色交替的斜向条纹 |
| 9 | 网格点阵 | Shader | 方格交叉处加圆点 |
| 10 | 细横线 | Shader | 等距细横线（笔记本纸感） |
| 11 | 渐变叠加 | Shader | 事件色径向渐变 + 细噪点 |

### API
```kotlin
object TextureLibrary {
    val textures: List<Texture> = listOf(...)
    
    @Composable
    fun TextureBackdrop(index: Int, accent: Color, modifier: Modifier = Modifier)
    
    @Composable  
    fun TexturePreview(index: Int, accent: Color, size: Dp = 72.dp, selected: Boolean = false)
}
```

## 实施顺序

```
T1: TextureLibrary.kt (新建) 
T2: Entities.kt (注释)
T3: CountdownUi.kt (TextureBackdrop 迁移)
T4: CountdownDetailScreen.kt (ClassicFullscreenContent/PhotoCardContent/两个 Sheet)
T5: CountdownEditScreen.kt (进阶区统一)
T6: ShareCardRenderer.kt (render 新增 fontDark)
T7: 编译验证 + 设备烟测
```

## 验收标准

1. **CLASSIC 详情页**：打开字色开关 → 文字变黑（含大数字/状态/日期），再关 → 变白
2. **PHOTO_CARD 无图**：底表「背景」显示纹理网格，选中后 Body 区域渲染对应纹理
3. **编辑页**：进阶区「字色」开关在两种风格都可见；「纹理」选择器在 CLASSIC/无图 PHOTO_CARD 可见
4. **分享图**：CLASSIC 分享图按 `fontDark` 输出黑/白字；PHOTO_CARD 保持现有行为
5. **回归**：原有 4 种纹理视觉不退化；有图模式完全不受影响

## 技术约束
- Kotlin 1.9.24 / Compose 1.5.14 / M3 1.3 —— 只能用稳定 API，避免 `ExperimentalGraphicsApi` 以外的实验性 Shader
- 纹理绘制必须在 `Canvas` 内用 `drawRect(shader)` 或 `drawPath(shader)`，避免逐像素循环
- `TextureLibrary` 无副作用、无状态、纯 Compose，便于预览/测试
- 数据库 `fallbackToDestructiveMigration()`，字段默认值生效，无需迁移脚本

---

## 确认开工？

回复 **"开工"** 我将把此方案写入 `.omo/plans/countdown-texture-font.md`，随后你可运行 `/start-work` 启动实施。