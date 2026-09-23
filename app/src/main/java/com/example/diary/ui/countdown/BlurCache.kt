package com.example.diary.ui.countdown

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 照片卡跨版本模糊工具：
 *  - API 31+ 走系统 RenderEffect（Compose Modifier.blur），零成本高质量
 *  - API 26–30 无 RenderEffect → 纯 Kotlin 栈模糊（三次 box blur 近似高斯），
 *    按 6 档（0/5/10/15/20/25 dp）离散缓存，滑杆拖动零卡顿
 */

/** 任意半径吸附到最近的 5 的倍数档位，夹在 0..25。 */
fun nearestLevel(r: Int): Int = (((r + 2) / 5) * 5).coerceIn(0, 25)

/**
 * 纯 Kotlin 栈模糊——水平/垂直/水平三次 box blur（半径三等分）近似高斯。
 * 调用方保证在 Dispatchers.Default 执行；返回新 Bitmap（不修改 src）。
 */
fun stackBlur(src: Bitmap, radiusPx: Int): Bitmap {
    if (radiusPx <= 0) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, false)
    val w = src.width
    val h = src.height
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    val r = radiusPx.coerceAtMost(w.coerceAtLeast(h) / 2).coerceAtLeast(1)
    val div = r + r + 1

    val pixels = IntArray(w * h)
    out.getPixels(pixels, 0, w, 0, 0, w, h)

    // 三次 pass：水平、垂直、水平（近似高斯）
    var temp = IntArray(w * h)
    var outPx = IntArray(w * h)
    val divSum = (div + 1) shr 1 // (div+1)/2，box blur 归一化

    repeat(2) { pass ->
        if (pass == 0) {
            boxBlurH(pixels, temp, w, h, r, divSum)
            boxBlurV(temp, outPx, w, h, r, divSum)
        } else {
            boxBlurH(outPx, temp, w, h, r, divSum)
            boxBlurV(temp, outPx, w, h, r, divSum)
        }
    }

    // 最终结果在 outPx（第二次 pass 后）
    out.setPixels(outPx, 0, w, 0, 0, w, h)
    temp = IntArray(0)
    outPx = IntArray(0)
    return out
}

/** 水平 box blur：src → dst，宽度 w 高度 h 半径 r。 */
private fun boxBlurH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, divSum: Int) {
    for (y in 0 until h) {
        val rowStart = y * w
        var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
        // 初始窗口 [-r, r]
        for (i in -r..r) {
            val p = src[rowStart + i.coerceIn(0, w - 1)]
            aSum += p ushr 24 and 0xFF
            rSum += p ushr 16 and 0xFF
            gSum += p ushr 8 and 0xFF
            bSum += p and 0xFF
        }
        for (x in 0 until w) {
            dst[rowStart + x] = (aSum / divSum shl 24) or
                    (rSum / divSum shl 16) or
                    (gSum / divSum shl 8) or
                    (bSum / divSum)
            // 滑动窗口：加右端 +1，减左端 -r
            val pAdd = src[rowStart + (x + r + 1).coerceIn(0, w - 1)]
            val pSub = src[rowStart + (x - r).coerceIn(0, w - 1)]
            aSum += (pAdd ushr 24 and 0xFF) - (pSub ushr 24 and 0xFF)
            rSum += (pAdd ushr 16 and 0xFF) - (pSub ushr 16 and 0xFF)
            gSum += (pAdd ushr 8 and 0xFF) - (pSub ushr 8 and 0xFF)
            bSum += (pAdd and 0xFF) - (pSub and 0xFF)
        }
    }
}

/** 垂直 box blur：src → dst，宽度 w 高度 h 半径 r。 */
private fun boxBlurV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, divSum: Int) {
    for (x in 0 until w) {
        var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
        for (i in -r..r) {
            val p = src[i.coerceIn(0, h - 1) * w + x]
            aSum += p ushr 24 and 0xFF
            rSum += p ushr 16 and 0xFF
            gSum += p ushr 8 and 0xFF
            bSum += p and 0xFF
        }
        for (y in 0 until h) {
            dst[y * w + x] = (aSum / divSum shl 24) or
                    (rSum / divSum shl 16) or
                    (gSum / divSum shl 8) or
                    (bSum / divSum)
            val pAdd = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
            val pSub = src[(y - r).coerceIn(0, h - 1) * w + x]
            aSum += (pAdd ushr 24 and 0xFF) - (pSub ushr 24 and 0xFF)
            rSum += (pAdd ushr 16 and 0xFF) - (pSub ushr 16 and 0xFF)
            gSum += (pAdd ushr 8 and 0xFF) - (pSub ushr 8 and 0xFF)
            bSum += (pAdd and 0xFF) - (pSub and 0xFF)
        }
    }
}

/**
 * 内存缓存：键 "eventId:档位"，按字节限额（≈16MB）的 LruCache，线程安全。
 * 超限由 LruCache 自动淘汰最久未用项。
 */
private const val BLUR_CACHE_MAX_BYTES = 16 * 1024 * 1024
private val memCache = object : android.util.LruCache<String, Bitmap>(BLUR_CACHE_MAX_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

/**
 * 取事件照片在指定模糊档位的位图（未命中则栈模糊后缓存）。
 * [src] 来自 BackgroundImageStore.decode 的降采样位图（≤1400px）。
 * 必须在 Dispatchers.Default 调用。
 */
fun blurredFor(eventId: Long, src: Bitmap, radiusDp: Int): Bitmap {
    val level = nearestLevel(radiusDp)
    if (level == 0) return src
    val key = "$eventId:$level"
    memCache.get(key)?.let { return it }
    // dp→px：src 已降采样，模糊半径按位图像素近似 ×2（视觉等效卡片显示尺寸）
    val blurred = stackBlur(src, level * 2)
    memCache.put(key, blurred)
    return blurred
}

/** 清空指定事件的模糊缓存（换图/删除事件时调用）。 */
fun clearBlurCache(eventId: Long? = null) {
    if (eventId == null) {
        memCache.evictAll()
    } else {
        val prefix = "$eventId:"
        memCache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { memCache.remove(it) }
    }
}

/**
 * 照片卡背景图：bitmap 空或零模糊 → 普通 Crop Image；
 * API 31+ 系统实时模糊；API <31 栈模糊档位缓存。
 */
@Composable
fun BlurCardImage(
    bitmap: ImageBitmap?,
    radiusDp: Int,
    eventId: Long,
    modifier: Modifier = Modifier
) {
    if (bitmap == null || radiusDp <= 0) {
        Image(
            bitmap = bitmap ?: return,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.blur(radiusDp.dp)
        )
    } else {
        // key3 = bitmap 身份：换图后 produceState 重新计算，避免串旧图
        val bitmapIdentity = remember(bitmap) { System.identityHashCode(bitmap) }
        val blurred by produceState<ImageBitmap?>(
            initialValue = null,
            key1 = eventId,
            key2 = nearestLevel(radiusDp),
            key3 = bitmapIdentity
        ) {
            value = withContext(Dispatchers.Default) {
                blurredFor(eventId, bitmap.asAndroidBitmap(), radiusDp).asImageBitmap()
            }
        }
        val shown = blurred ?: bitmap
        Image(
            bitmap = shown,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}
