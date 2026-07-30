package com.example.apexdownloader.data

import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * Basic sanity tests for [DownloadItem] default values.
 * DownloadRepository itself needs an Android Context (SharedPreferences),
 * so it's exercised via an instrumented/androidTest, not here.
 */
class DownloadItemTest {
    @Test
    fun defaultStatus_isQueued() {
        val item = DownloadItem(id = "1", title = "Test", url = "https://example.com/video.mp4")
        assertEquals("queued", item.status)
    }

    @Test
    fun defaultType_isDirectLink() {
        val item = DownloadItem(id = "1", title = "Test", url = "https://example.com/video.mp4")
        assertEquals("direct-link", item.type)
    }
}
