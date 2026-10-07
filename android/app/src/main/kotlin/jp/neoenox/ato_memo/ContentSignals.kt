package jp.neoenox.ato_memo

/** Screenshot detection shared by auto-import (#9). Pure for unit tests. */
object ScreenshotFilter {
    private val keywords = listOf(
        "screenshot",
        "screen_shot",
        "screen-shot",
        "screencap",
        "screen_cap",
        "screen capture",
        "screenshots",
        "スクリーンショット",
        "スクショ",
    )
    private val filePatterns = listOf(
        Regex("screenshot", RegexOption.IGNORE_CASE),
        Regex("screen[_\\- ]?shot", RegexOption.IGNORE_CASE),
        Regex("screen[_\\- ]?cap", RegexOption.IGNORE_CASE),
        Regex("スクリーンショット"),
        Regex("スクショ"),
    )
    fun isScreenshot(displayName: String?, bucket: String?, relativePath: String?): Boolean {
        val info = listOfNotNull(displayName, bucket, relativePath)
            .joinToString(" ").lowercase()
        if (keywords.any { info.contains(it) }) return true
        val name = displayName ?: ""
        if (filePatterns.any { it.containsMatchIn(name) }) return true
        val path = (relativePath ?: "").lowercase()
        // OEM paths like Pictures/Screenshots, DCIM/Screenshots.
        if (path.contains("screenshot")) return true
        return false
    }
}

/** Shared URL handling (#11). Pure for unit tests. */
object LinkUtils {
    private val urlPattern = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
    private val trailing = setOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"', '、', '。', '」', '』', '）', '…', '・')
    fun findFirst(raw: String): String {
        val hit = urlPattern.find(raw)?.value ?: return ""
        return sanitize(hit)
    }
    fun sanitize(raw: String): String {
        var value = raw.trim()
        while (value.isNotEmpty() && value.last() in trailing) {
            value = value.dropLast(1)
        }
        // Balance a stray opening parenthesis captured at the end.
        return value
    }
}
