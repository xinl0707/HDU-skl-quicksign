package dev.xinl0707.quicksign

import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * 课表解析共享工具：杭电助手 ICS → 课程列表。MainActivity 面板当前课程显示使用。
 */
object ScheduleParser {

    data class Course(
        val start: Long,
        val end: Long,
        val name: String,
        val location: String,
        val teacher: String,
    )

    /** 解析 ICS 文本；只处理真正的课程块（VEVENT 必有 SUMMARY；VTIMEZONE/头部等无 SUMMARY 的块跳过） */
    fun parse(text: String): List<Course> {
        val out = mutableListOf<Course>()
        for (block in text.split("BEGIN:VEVENT")) {
            if (!block.contains("SUMMARY")) continue
            if (!block.contains("DTSTART")) continue
            val start = icsTime(block.lineOf("DTSTART")) ?: continue
            val end = icsTime(block.lineOf("DTEND")) ?: continue
            val name = block.lineOf("SUMMARY").replace("\\n", " ").trim()
            val loc = block.lineOf("LOCATION").replace("\\n", " ").trim()
            val teacher = Regex("授课老师[：:]\\s*([^\\\\\\n]+)").find(block)?.groupValues?.get(1)?.trim().orEmpty()
            out.add(Course(start, end, name, loc, teacher))
        }
        return out
    }

    private fun String.lineOf(prefix: String): String =
        lineSequence().firstOrNull { it.startsWith(prefix) }?.substringAfterLast(':')?.trim().orEmpty()

    /** 杭电助手 ICS 时间为 yyyyMMdd'T'HHmmss 格式（如 20261116T084000），解析前去掉 T 分隔符 */
    private fun icsTime(raw: String): Long? {
        val t = raw.removeSuffix("Z").trim().replace("T", "")
        return try {
            LocalDateTime.parse(t, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    /** 时间区间显示：HH:mm-HH:mm */
    fun timeRange(start: Long, end: Long): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.CHINA)
        return "${fmt.format(Date(start))}-${fmt.format(Date(end))}"
    }

    /** 是否今天（Asia/Shanghai） */
    fun isToday(ts: Long, now: Long): Boolean =
        SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date(now)) ==
                SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date(ts))
}
