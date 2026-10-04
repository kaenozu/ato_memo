package jp.neoenox.ato_memo

import java.time.LocalDateTime
import java.time.DateTimeException

object ReminderTimes {
    fun defaultTime(now: LocalDateTime, hour: Int, minute: Int, delayDays: Int, weekly: Boolean, weekday: Int): LocalDateTime {
        var value = now.toLocalDate().plusDays(delayDays.toLong()).atTime(hour, minute)
        if (weekly) while (value.dayOfWeek.value != weekday) value = value.plusDays(1)
        if (!value.isAfter(now)) value = value.plusDays(if (weekly) 7 else 1)
        return value
    }

    fun candidates(raw: String, now: LocalDateTime): List<LocalDateTime> {
        val text = raw.map { if (it in '０'..'９') it - 0xfee0 else it }.joinToString("")
            .replace('／', '/').replace('：', ':')
        val time = Regex("(?<!\\d)([01]?\\d|2[0-3])(?:[:時])([0-5]\\d)(?:分)?(?!\\d)").find(text)
        val hour = time?.groupValues?.get(1)?.toInt() ?: 20
        val minute = time?.groupValues?.get(2)?.toInt() ?: 0
        val dates = Regex("(?<!\\d)(?:(20\\d{2})[年/.-])?(1[0-2]|0?[1-9])[月/](3[01]|[12]\\d|0?[1-9])日?(?!\\d)")
        val values = dates.findAll(text).mapNotNull { match ->
            try {
                val year = match.groupValues[1].toIntOrNull() ?: now.year
                var value = LocalDateTime.of(year, match.groupValues[2].toInt(), match.groupValues[3].toInt(), hour, minute)
                if (match.groupValues[1].isEmpty() && value.toLocalDate().isBefore(now.toLocalDate())) value = value.plusYears(1)
                value
            } catch (_: DateTimeException) { null }
        }.distinct().take(8).toList()
        if (values.isNotEmpty() || time == null) return values
        var value = now.toLocalDate().atTime(hour, minute)
        if (!value.isAfter(now)) value = value.plusDays(1)
        return listOf(value)
    }
}
