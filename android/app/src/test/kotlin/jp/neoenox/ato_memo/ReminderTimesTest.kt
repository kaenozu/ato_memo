package jp.neoenox.ato_memo

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ReminderTimesTest {
    private val now = LocalDateTime.of(2026, 10, 4, 21, 0)
    @Test fun tomorrowIsCalendarDayNot24Hours() {
        assertEquals(LocalDateTime.of(2026, 10, 5, 20, 0), ReminderTimes.defaultTime(now, 20, 0, 1, false, 6))
    }
    @Test fun todayPastTimeRollsToNextDay() {
        assertEquals(LocalDateTime.of(2026, 10, 5, 20, 0), ReminderTimes.defaultTime(now, 20, 0, 0, false, 6))
    }
    @Test fun weeklyUsesSelectedWeekday() {
        assertEquals(LocalDateTime.of(2026, 10, 10, 9, 30), ReminderTimes.defaultTime(now, 9, 30, 1, true, 6))
    }
    @Test fun ocrRejectsInvalidDatesAndPricesAndKeepsExplicitPastYear() {
        val result = ReminderTimes.candidates("価格19.00円 2/30まで 2025年10月15日 10/15まで 19:00開演", now)
        assertEquals(2, result.size)
        assertTrue(result.any { it.year == 2025 })
        assertTrue(result.any { it.year == 2026 && it.monthValue == 10 && it.dayOfMonth == 15 })
        assertTrue(result.all { it.hour == 19 && it.minute == 0 })
    }
    @Test fun ocrTimeWithoutDateOffersNextOccurrence() {
        assertEquals(listOf(LocalDateTime.of(2026, 10, 5, 19, 0)), ReminderTimes.candidates("19:00開演", now))
    }
    @Test fun fullWidthAndYearRolloverAreRecognized() {
        val result = ReminderTimes.candidates("１／５まで", LocalDateTime.of(2026, 12, 30, 10, 0))
        assertEquals(LocalDateTime.of(2027, 1, 5, 20, 0), result.single())
    }
}
