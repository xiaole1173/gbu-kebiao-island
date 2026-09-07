// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gbu.classisland.data.Course
import com.gbu.classisland.model.SectionTime
import com.gbu.classisland.ui.common.swipeForPage
import com.gbu.classisland.util.TimetableEngine
import java.time.LocalDate

/** 周课表视图：顶部周次栏 + 课表网格 + 手动添加课程。 */
@Composable
fun WeekScreen(viewModel: WeekViewModel = viewModel()) {
    val courses by viewModel.courses.collectAsState()
    val selectedWeek by viewModel.selectedWeek.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val today by viewModel.today.collectAsState()
    val termStart by viewModel.termStartDate.collectAsState()
    val effectiveSemester by viewModel.effectiveSemester.collectAsState()
    val availableSemesters by viewModel.availableSemesters.collectAsState()
    val isCurrentSemester by viewModel.isCurrentSemester.collectAsState()
    val libraryByCode by viewModel.libraryByCode.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        WeekHeader(
            selectedWeek = selectedWeek,
            currentWeek = currentWeek,
            isCurrentSemester = isCurrentSemester,
            semesters = availableSemesters,
            selectedSemester = effectiveSemester,
            onSelectSemester = viewModel::selectSemester,
            onSelectWeek = viewModel::selectWeek,
            onAddCourse = { showAdd = true }
        )
        // 周次切换动画：按方向滑动翻页（下一周从右滑入，上一周从左滑入）
        AnimatedContent(
            targetState = selectedWeek,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // 左右滑动切换上下周
                .swipeForPage(
                    onPrev = { viewModel.selectWeek((selectedWeek - 1).coerceAtLeast(1)) },
                    onNext = { viewModel.selectWeek(selectedWeek + 1) }
                ),
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(tween(340)) { it } + fadeIn(tween(340))) togetherWith
                        (slideOutHorizontally(tween(340)) { -it / 3 } + fadeOut(tween(230)))
                } else {
                    (slideInHorizontally(tween(340)) { -it } + fadeIn(tween(340))) togetherWith
                        (slideOutHorizontally(tween(340)) { it / 3 } + fadeOut(tween(230)))
                }
            },
            label = "weekGrid"
        ) { week ->
            TimetableGrid(
                courses = courses,
                week = week,
                sections = sections,
                today = today,
                termStart = if (isCurrentSemester) termStart else null,
                onCourseClick = { selectedCourse = it },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // 课程详情弹窗
    selectedCourse?.let { c ->
        AlertDialog(
            onDismissRequest = { selectedCourse = null },
            title = { Text(c.name, style = MaterialTheme.typography.titleMedium) },
            text = { CourseDetailContent(c, sections, libraryByCode) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (c.source == "manual") {
                        TextButton(
                            onClick = { viewModel.deleteCourse(c); selectedCourse = null }
                        ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { selectedCourse = null }) { Text("关闭") }
                }
            }
        )
    }

    if (showAdd) {
        AddCourseSheet(
            semesterId = effectiveSemester,
            onDismiss = { showAdd = false },
            onSave = { course ->
                viewModel.upsertCourse(course)
                showAdd = false
            }
        )
    }
}

@Composable
private fun WeekHeader(
    selectedWeek: Int,
    currentWeek: Int,
    isCurrentSemester: Boolean,
    semesters: List<String>,
    selectedSemester: String,
    onSelectSemester: (String) -> Unit,
    onSelectWeek: (Int) -> Unit,
    onAddCourse: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SemesterSwitcher(semesters, selectedSemester, isCurrentSemester, onSelectSemester)
            TextButton(onClick = onAddCourse) {
                androidx.compose.material3.Icon(
                    painter = painterResource(com.gbu.classisland.R.drawable.lucide_plus),
                    contentDescription = "添加课程",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text("添加", fontSize = 13.sp)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isCurrentSemester && currentWeek <= 0) "未开学 · 第 $selectedWeek 周" else "第 $selectedWeek 周",
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButtonCompat("上一周") { onSelectWeek((selectedWeek - 1).coerceAtLeast(1)) }
                TextButtonCompat("下一周") { onSelectWeek(selectedWeek + 1) }
            }
        }
    }
}

/** 学期标识 → 可读标签："2026-2027-1" → "2026-2027 秋季"。 */
private fun semesterLabel(semesterId: String): String {
    val parts = semesterId.split("-")
    if (parts.size < 3) return semesterId
    val xq = when (parts[2]) {
        "1" -> "秋季"
        "2" -> "春季"
        "3" -> "夏季"
        else -> "第${parts[2]}学期"
    }
    return "${parts[0]}-${parts[1]} $xq"
}

/** 学期切换下拉：默认当前学期，可切换到历史学期。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SemesterSwitcher(
    semesters: List<String>,
    selected: String,
    isCurrent: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = remember(selected, isCurrent) {
        val base = semesterLabel(selected)
        when {
            base.isBlank() -> "选择学期"
            isCurrent -> "$base（当前）"
            else -> base
        }
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                .width(190.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            semesters.forEach { sem ->
                DropdownMenuItem(
                    text = {
                        Text(
                            semesterLabel(sem) + if (sem == selected) "（当前）" else "",
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        onSelect(sem)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TextButtonCompat(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(text, fontSize = 13.sp)
    }
}

private val WEEK_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 课表网格布局参数（Canvas 绘制与点击命中共用）。 */
private data class TimetableLayout(
    val dayRange: IntRange,
    val colW: Float,
    val rowH: Float,
    val headerH: Float,
    val rowHeaderW: Float,
    val maxSection: Int,
    val weekCourses: List<Course>
) {
    /** 点击命中测试：返回被点中的课程块。 */
    fun hitTest(x: Float, y: Float): Course? {
        if (x < rowHeaderW || y < headerH || colW <= 0f || rowH <= 0f) return null
        val dayIdx = ((x - rowHeaderW) / colW).toInt()
        val section = ((y - headerH) / rowH).toInt() + 1
        return weekCourses.firstOrNull { c ->
            c.dayOfWeek == dayRange.first + dayIdx && section in c.startSection..c.endSection
        }
    }
}

private fun computeLayout(
    courses: List<Course>,
    week: Int,
    canvasPx: IntSize,
    density: Float
): TimetableLayout {
    val headerH = 34f * density
    val rowHeaderW = 52f * density
    val width = canvasPx.width.toFloat()
    val height = canvasPx.height.toFloat()
    if (width <= 0f || height <= 0f) {
        return TimetableLayout(1..5, 0f, 0f, headerH, rowHeaderW, 12, emptyList())
    }
    // 该周有效的课程
    val weekCourses = courses.filter { c ->
        c.weeks.isBlank() || week in TimetableEngine.parseWeeks(c.weeks)
    }
    // 动态天范围：只显示有课的天（默认到周五，避免无课周末空列）
    val daysWithClasses = weekCourses.map { it.dayOfWeek }.distinct().sorted()
    val dayRange = if (daysWithClasses.isEmpty()) 1..5
    else (daysWithClasses.first().coerceAtMost(5))..(daysWithClasses.last().coerceAtLeast(1))
    val colCount = dayRange.count()
    val colW = (width - rowHeaderW) / colCount
    val maxSection = maxOf(courses.maxOfOrNull { it.endSection } ?: 0, 12)
    val rowH = (height - headerH) / maxSection
    return TimetableLayout(dayRange, colW, rowH, headerH, rowHeaderW, maxSection, weekCourses)
}

/** 课表网格（Canvas 绘制）：列头（星期+日期）、行头（节次+时间）、单双周过滤、今天高亮、点击查看详情。 */
@Composable
fun TimetableGrid(
    courses: List<Course>,
    week: Int,
    sections: List<SectionTime>,
    today: LocalDate,
    termStart: LocalDate?,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    var canvasPx by remember { mutableStateOf(IntSize.Zero) }

    val layout = remember(courses, week, canvasPx, density) {
        computeLayout(courses, week, canvasPx, density)
    }
    val currentLayout by rememberUpdatedState(layout)
    // 主题感知的文字色（深色模式下 Canvas 文字/线必须跟随 onSurface，否则黑字看不见）
    val onSurface = MaterialTheme.colorScheme.onSurface
    // 预计算全部文本布局：重绘/切页动画时直接画缓存，不再逐格 re-measure（消除主线程文本测量卡顿）
    val texts = remember(layout, sections, week, termStart, today, onSurface) {
        buildGridTexts(textMeasurer, layout, sections, week, termStart, today, onSurface)
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasPx = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentLayout.hitTest(offset.x, offset.y)?.let { onCourseClick(it) }
                }
            }
    ) {
        if (layout.colW <= 0f) return@Canvas
        drawColumnHeader(texts, layout, week, termStart, today)
        drawRowHeader(texts, layout, sections)

        // 课程块
        clipRect(top = layout.headerH, left = layout.rowHeaderW) {
            layout.weekCourses.filter { it.dayOfWeek in layout.dayRange }.forEach { c ->
                val x = layout.rowHeaderW + (c.dayOfWeek - layout.dayRange.first) * layout.colW
                val y = layout.headerH + (c.startSection - 1) * layout.rowH
                val h = (c.endSection - c.startSection + 1) * layout.rowH
                drawCourseBlock(texts, c, x, y, layout.colW - 2f, h - 2f)
            }
        }

        // 网格线画在最上层，保证格子结构始终清晰可见（线压在色块上）
        drawGrid(layout.headerH, layout.rowHeaderW, layout.colW, layout.rowH, layout.dayRange.count(), layout.maxSection, onSurface)
    }
}

/** 课程块文本键（同一块唯一；与去重键一致避免预览/重复块冲突）。 */
private data class BlockKey(val externalId: String, val day: Int, val start: Int)

/** 预计算文本布局集合：Canvas 重绘直接绘制，不再逐格 measure。 */
private data class GridTexts(
    val headerLabels: Map<Int, androidx.compose.ui.text.TextLayoutResult>,
    val headerDates: Map<Int, androidx.compose.ui.text.TextLayoutResult>,
    val rowNumbers: Map<Int, androidx.compose.ui.text.TextLayoutResult>,
    val rowTimes: Map<Int, androidx.compose.ui.text.TextLayoutResult>,
    val blockTitles: Map<BlockKey, androidx.compose.ui.text.TextLayoutResult>,
    val blockLocs: Map<BlockKey, androidx.compose.ui.text.TextLayoutResult>
)

private fun buildGridTexts(
    textMeasurer: TextMeasurer,
    layout: TimetableLayout,
    sections: List<SectionTime>,
    week: Int,
    termStart: LocalDate?,
    today: LocalDate,
    onSurface: Color
): GridTexts {
    val dim = onSurface.copy(alpha = 0.55f)
    val headerLabels = layout.dayRange.associateWith { day ->
        textMeasurer.measure(
            text = androidx.compose.ui.text.buildAnnotatedString { append(WEEK_LABELS[day - 1]) },
            style = androidx.compose.ui.text.TextStyle(
                color = onSurface,
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        )
    }
    val headerDates = layout.dayRange.mapNotNull { day ->
        val date = termStart?.plusWeeks(week - 1L)?.plusDays((day - 1).toLong()) ?: return@mapNotNull null
        day to textMeasurer.measure(
            text = androidx.compose.ui.text.buildAnnotatedString { append("${date.monthValue}/${date.dayOfMonth}") },
            style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = dim)
        )
    }.toMap()
    val rowNumbers = (1..layout.maxSection).associateWith { s ->
        textMeasurer.measure(
            text = androidx.compose.ui.text.buildAnnotatedString { append("$s") },
            style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = onSurface)
        )
    }
    val rowTimes = (1..layout.maxSection).mapNotNull { s ->
        val sec = sections.find { it.section == s } ?: return@mapNotNull null
        s to textMeasurer.measure(
            text = androidx.compose.ui.text.buildAnnotatedString { append("${sec.start}-${sec.end}") },
            style = androidx.compose.ui.text.TextStyle(fontSize = 7.sp, color = dim)
        )
    }.toMap()

    val blockW = layout.colW - 2f
    val inset = 3f
    val bw = (blockW - inset * 2).coerceAtLeast(8f)
    val textMaxW = (bw - 6f).coerceAtLeast(20f).toInt()
    val nameSize = if (blockW < 90f) 9.sp else 11.sp
    val blockTitles = layout.weekCourses.associate { c ->
        BlockKey(c.externalId, c.dayOfWeek, c.startSection) to textMeasurer.measure(
            text = androidx.compose.ui.text.buildAnnotatedString { append(c.name) },
            style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = nameSize),
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = textMaxW, maxHeight = Int.MAX_VALUE)
        )
    }
    val blockLocs = layout.weekCourses.filter { it.location.isNotBlank() }.associate { c ->
        BlockKey(c.externalId, c.dayOfWeek, c.startSection) to textMeasurer.measure(
            text = androidx.compose.ui.text.buildAnnotatedString { append(c.location) },
            style = androidx.compose.ui.text.TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 8.sp),
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = textMaxW, maxHeight = Int.MAX_VALUE)
        )
    }
    return GridTexts(headerLabels, headerDates, rowNumbers, rowTimes, blockTitles, blockLocs)
}

/** 列头：星期 + 日期，今天所在列高亮。 */
private fun DrawScope.drawColumnHeader(
    texts: GridTexts,
    layout: TimetableLayout,
    week: Int,
    termStart: LocalDate?,
    today: LocalDate
) {
    layout.dayRange.forEach { day ->
        val x = layout.rowHeaderW + (day - layout.dayRange.first) * layout.colW
        val date = termStart?.plusWeeks(week - 1L)?.plusDays((day - 1).toLong())
        if (today == date) {
            drawRect(
                color = Color(0x1A1B5E9E),
                topLeft = Offset(x, 0f),
                size = Size(layout.colW, layout.headerH)
            )
        }
        val label = texts.headerLabels[day]
        if (label != null) {
            drawText(textLayoutResult = label, topLeft = Offset(x + layout.colW / 2 - label.size.width / 2, 3f))
        }
        texts.headerDates[day]?.let { dateLabel ->
            drawText(
                textLayoutResult = dateLabel,
                topLeft = Offset(x + layout.colW / 2 - dateLabel.size.width / 2, 3f + (label?.size?.height ?: 0) + 2f)
            )
        }
    }
}

/** 行头：节次号在上、起止时间在下（居中排版，避免贴边被遮挡）。 */
private fun DrawScope.drawRowHeader(
    texts: GridTexts,
    layout: TimetableLayout,
    sections: List<SectionTime>
) {
    (1..layout.maxSection).forEach { s ->
        val cy = layout.headerH + (s - 1) * layout.rowH
        val num = texts.rowNumbers[s] ?: return@forEach
        drawText(
            textLayoutResult = num,
            topLeft = Offset(layout.rowHeaderW / 2 - num.size.width / 2, cy + 2f)
        )
        if (layout.rowH >= 26f) {
            texts.rowTimes[s]?.let { time ->
                drawText(
                    textLayoutResult = time,
                    topLeft = Offset(layout.rowHeaderW / 2 - time.size.width / 2, cy + 2f + num.size.height + 2f)
                )
            }
        }
    }
}

private fun DrawScope.drawGrid(
    headerH: Float,
    rowHeaderW: Float,
    colW: Float,
    rowH: Float,
    colCount: Int,
    maxSection: Int,
    onSurface: Color
) {
    val lineColor = onSurface.copy(alpha = 0.14f)
    // 竖线
    for (i in 0..colCount) {
        val x = rowHeaderW + i * colW
        drawLine(lineColor, Offset(x, headerH), Offset(x, size.height), 1f)
    }
    // 横线
    for (i in 0..maxSection) {
        val y = headerH + i * rowH
        drawLine(lineColor, Offset(rowHeaderW, y), Offset(size.width, y), 1f)
    }
}

private fun DrawScope.drawCourseBlock(
    texts: GridTexts,
    course: Course,
    x: Float,
    y: Float,
    w: Float,
    h: Float
) {
    val color = courseColor(course.externalId)
    // 色块内缩留出边距，让网格格子可见
    val inset = 3f
    val bw = (w - inset * 2).coerceAtLeast(8f)
    val bh = (h - inset * 2).coerceAtLeast(8f)
    drawRoundRect(
        color = color,
        topLeft = Offset(x + inset, y + inset),
        size = Size(bw, bh),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.20f),
        topLeft = Offset(x + inset, y + inset),
        size = Size(bw, bh),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f)
    )
    // 文字（课程名 2 行截断 + 地点小字），布局已预计算缓存
    val key = BlockKey(course.externalId, course.dayOfWeek, course.startSection)
    val title = texts.blockTitles[key] ?: return
    drawText(textLayoutResult = title, topLeft = Offset(x + inset + 3f, y + inset + 3f))
    if (bh > title.size.height + 22f) {
        texts.blockLocs[key]?.let { loc ->
            drawText(textLayoutResult = loc, topLeft = Offset(x + inset + 3f, y + inset + 3f + title.size.height + 2f))
        }
    }
}

/** 同一课程（externalId）稳定同色。 */
internal fun courseColor(externalId: String): Color {
    val h = (externalId.hashCode() and 0x7fffffff) % 360
    val s = 0.55f
    val l = 0.45f
    val c = (1 - kotlin.math.abs(2 * l - 1)) * s
    val x = c * (1 - kotlin.math.abs((h / 60.0f) % 2 - 1))
    val m = l - c / 2
    val (r, g, b) = when (h / 60) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

private fun DrawScope.px(dp: Float): Float = dp * density

/** 课程详情内容（名称/教师/教室/学分/时间/周次/来源/体育项目）。 */
@Composable
private fun CourseDetailContent(
    course: Course,
    sections: List<SectionTime>,
    libraryByCode: Map<String, com.gbu.classisland.data.LibraryCourse>
) {
    val startSec = sections.find { it.section == course.startSection }
    val endSec = sections.find { it.section == course.endSection }
    val isPe = course.name.contains("体育") || course.externalId.contains("PEC")
    val peHint = if (isPe) {
        com.gbu.classisland.data.credits.CourseCatalog.peProjectHint(course.teacher)
    } else null
    // 优先课程库查学分/性质，未命中回退内置目录
    val creditEntry = com.gbu.classisland.data.credits.CourseCatalog.extractCode(course.externalId)
        ?.let { libraryByCode[it] }
        ?.let { com.gbu.classisland.data.credits.CourseCatalog.fromLibrary(it) }
        ?: com.gbu.classisland.data.credits.CourseCatalog.findByRwh(course.externalId)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailRow("教师", course.teacher.ifBlank { "—" })
        DetailRow("教室", course.location.ifBlank { "—" })
        if (creditEntry != null) {
            val nature = when {
                creditEntry.required -> "必修"
                creditEntry.category != null -> "通识选修"
                else -> "选修"
            }
            DetailRow(
                "学分",
                "${fmtCredit(creditEntry.credits)} 分（$nature）"
            )
        }
        creditEntry?.category?.let { cat ->
            DetailRow("类别", "${cat.label} ${cat.desc}")
        }
        DetailRow(
            "时间",
            "${WEEK_LABELS[course.dayOfWeek - 1]} 第${course.startSection}-${course.endSection}节" +
                (if (startSec != null) "（${startSec.start} - ${endSec?.end ?: ""}）" else "")
        )
        DetailRow("周次", if (course.weeks.isBlank()) "每周" else course.weeks)
        if (peHint != null) {
            DetailRow("体育项目", "$peHint（按任课教师）")
        }
        DetailRow("来源", if (course.source == "manual") "手动添加" else "教务同步")
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(
            "$label：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 学分显示：去掉整数的小数位（23.0 → 23）。 */
private fun fmtCredit(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else v.toString()

@Preview(showBackground = true)
@Composable
private fun WeekScreenPreview() {
    Surface {
        TimetableGrid(
            courses = listOf(
                Course(name = "高等数学1", teacher = "王福东", location = "B101", dayOfWeek = 1, startSection = 1, endSection = 2, weeks = (1..16).joinToString(","), externalId = "2026-2027-1-MATH101-001A"),
                Course(name = "线性代数", teacher = "柴子巍", location = "B102", dayOfWeek = 3, startSection = 3, endSection = 4, weeks = (1..16).joinToString(","), externalId = "2026-2027-1-MATH103-001B"),
                Course(name = "物理原理1【实验】", location = "B303", dayOfWeek = 5, startSection = 6, endSection = 8, weeks = "2,4", externalId = "2026-2027-1-PHY101-002C"),
                Course(name = "大学英语1", location = "B304", dayOfWeek = 2, startSection = 6, endSection = 7, weeks = (1..16).joinToString(","), externalId = "2026-2027-1-ENG101-001")
            ),
            week = 2,
            sections = com.gbu.classisland.model.DefaultSections.list,
            today = LocalDate.now(),
            termStart = LocalDate.now().minusDays(7),
            onCourseClick = {}
        )
    }
}
