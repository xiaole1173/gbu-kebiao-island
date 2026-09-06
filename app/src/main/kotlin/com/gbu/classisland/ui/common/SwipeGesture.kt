// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.ui.common

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 横向滑动翻页手势：
 * - 左滑（位移 < -阈值）→ onNext（下一周/下一天）
 * - 右滑（位移 > 阈值）→ onPrev（上一周/前一天）
 * 每次手势开始/结束都重置位移，避免残留导致方向误判。
 */
fun Modifier.swipeForPage(
    onPrev: () -> Unit,
    onNext: () -> Unit
): Modifier = composed {
    val currentPrev by rememberUpdatedState(onPrev)
    val currentNext by rememberUpdatedState(onNext)
    pointerInput(Unit) {
        var totalDrag = 0f
        detectHorizontalDragGestures(
            onDragStart = { totalDrag = 0f },
            onDragEnd = {
                val threshold = 90f * density
                when {
                    totalDrag < -threshold -> currentNext()
                    totalDrag > threshold -> currentPrev()
                }
                totalDrag = 0f
            },
            onDragCancel = { totalDrag = 0f }
        ) { change, dragAmount ->
            change.consume()
            totalDrag += dragAmount
        }
    }
}
