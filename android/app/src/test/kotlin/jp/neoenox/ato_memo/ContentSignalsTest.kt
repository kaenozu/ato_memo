package jp.neoenox.ato_memo

import org.junit.Assert.*
import org.junit.Test

class ContentSignalsTest {
    @Test fun screenshotKeywords() {
        assertTrue(ScreenshotFilter.isScreenshot("Screenshot_20261005.png", "Screenshots", "Pictures/Screenshots/"))
        assertTrue(ScreenshotFilter.isScreenshot("スクリーンショット_01.png", null, null))
        assertTrue(ScreenshotFilter.isScreenshot("スクショ 2026.png", null, null))
        assertTrue(ScreenshotFilter.isScreenshot("Screen-shot 1.png", null, null))
        assertTrue(ScreenshotFilter.isScreenshot("IMG_001.png", "Screenshots", "DCIM/Screenshots/"))
    }
    @Test fun nonScreenshotRejected() {
        assertFalse(ScreenshotFilter.isScreenshot("IMG_20261005.jpg", "Camera", "DCIM/Camera/"))
        assertFalse(ScreenshotFilter.isScreenshot("coupon.png", "Download", "Download/"))
        assertFalse(ScreenshotFilter.isScreenshot(null, null, null))
    }
    @Test fun urlTrailingPunctuationStripped() {
        assertEquals("https://example.com/a", LinkUtils.findFirst("see https://example.com/a。"))
        assertEquals("https://example.com/a", LinkUtils.findFirst("（https://example.com/a）"))
        assertEquals("", LinkUtils.findFirst("no link here"))
    }
}
