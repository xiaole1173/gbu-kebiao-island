// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gbu.classisland.data.Course

private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 解析周次文本："1-16"、"2,4"、"1-8,10-16" → 周号集合。 */
internal fun parseWeeksText(text: String): Set<Int> {
    val result = mutableSetOf<Int>()
    text.split(',').forEach { part ->
        val r = part.trim().split('-')
        when {
            r.size == 1 -> r[0].trim().toIntOrNull()?.let { if (it in 1..30) result += it }
            r.size == 2 -> {
                val a = r[0].trim().toIntOrNull()
                val b = r[1].trim().toIntOrNull()
                if (a != null && b != null && a in 1..30 && b in 1..30 && a <= b) result += a..b
            }
        }
    }
    return result
}

/** 手动添加课程表单（底部弹层）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseSheet(
    semesterId: String,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(1) }
    var startSection by remember { mutableStateOf(1) }
    var endSection by remember { mutableStateOf(2) }
    var weeksText by remember { mutableStateOf("1-16") }
    var error by remember { mutableStateOf<String?>(null) }

    var dayMenu by remember { mutableStateOf(false) }
    var startMenu by remember { mutableStateOf(false) }
    var endMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("添加课程", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("课程名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = teacher,
                onValueChange = { teacher = it },
                label = { Text("教师（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("教室（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 星期
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("星期", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { dayMenu = true }) { Text(DAY_NAMES[day - 1]) }
                DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                    DAY_NAMES.forEachIndexed { i, n ->
                        DropdownMenuItem(text = { Text(n) }, onClick = { day = i + 1; dayMenu = false })
                    }
                }
            }

            // 节次
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("开始节次")
                    OutlinedButton(onClick = { startMenu = true }) { Text("第 $startSection 节") }
                    DropdownMenu(expanded = startMenu, onDismissRequest = { startMenu = false }) {
                        (1..18).forEach { s ->
                            DropdownMenuItem(text = { Text("第 $s 节") }, onClick = {
                                startSection = s
                                if (endSection < s) endSection = s
                                startMenu = false
                            })
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("结束节次")
                    OutlinedButton(onClick = { endMenu = true }) { Text("第 $endSection 节") }
                    DropdownMenu(expanded = endMenu, onDismissRequest = { endMenu = false }) {
                        (startSection..18).forEach { s ->
                            DropdownMenuItem(text = { Text("第 $s 节") }, onClick = { endSection = s; endMenu = false })
                        }
                    }
                }
            }

            // 周次
            OutlinedTextField(
                value = weeksText,
                onValueChange = { weeksText = it },
                label = { Text("周次（如 1-16 / 2,4 / 1-8,10-16）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = {
                        val weeks = parseWeeksText(weeksText)
                        when {
                            name.isBlank() -> error = "请填写课程名"
                            weeks.isEmpty() -> error = "周次格式不正确"
                            else -> {
                                onSave(
                                    Course(
                                        name = name.trim(),
                                        teacher = teacher.trim(),
                                        location = location.trim(),
                                        dayOfWeek = day,
                                        startSection = startSection,
                                        endSection = endSection,
                                        startWeek = weeks.min(),
                                        endWeek = weeks.max(),
                                        weekParity = com.gbu.classisland.edu.TimetableParser.computeParity(weeks),
                                        weeks = weeks.sorted().joinToString(","),
                                        semesterId = semesterId,
                                        source = "manual"
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }
        }
    }
}
