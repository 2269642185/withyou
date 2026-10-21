package com.withyou.anniversary

import org.junit.Assert.*
import org.junit.Test

class UpdateRulesTest {
    private val good=AppUpdate(7,"1.4.1","修复", "https://github.com/test/withyou-download/releases/download/v1.4.1/withyou.apk",1024,"a".repeat(64),26)
    private fun rejects(block: () -> Unit) { try {block(); fail("must reject")} catch(_: IllegalArgumentException) {} }
    @Test fun repositoryAndHttpsAreRestricted() {
        assertEquals("test/withyou-download",UpdateRules.repository("https://github.com/test/withyou-download.git"))
        listOf("http://github.com/test/a","test/a/../b","test/..","https://evil.test/a/b").forEach { rejects {UpdateRules.repository(it)} }
        listOf("http://github.com/a","https://user:password@github.com/a","file:///a","https://github.com/a#b").forEach { rejects {UpdateRules.https(it)} }
    }
    @Test fun updateMetadataRejectsBadOrForeignAssets() {
        UpdateRules.validate(good,"test/withyou-download")
        listOf(good.copy(size=0),good.copy(size=UpdateRules.MAX_APK+1),good.copy(sha256="bad"),good.copy(code=0),good.copy(url="https://github.com/other/app/releases/download/v1/a.apk"),good.copy(url="https://evil.test/a.apk"),good.copy(url="https://github.com/test/withyou-download/releases/download/v1/a.exe")).forEach { rejects {UpdateRules.validate(it,"test/withyou-download")} }
    }
    @Test fun versionsAndDailyChecks() {
        val sameName=good.copy(code=13,version="1.8.1",url="https://github.com/test/withyou-download/releases/download/v1.8.1-build13/withyou.apk")
        UpdateRules.validate(sameName,"test/withyou-download")
        assertTrue(UpdateRules.newer(sameName,12));assertFalse(UpdateRules.newer(sameName,13))
        assertTrue(UpdateRules.newer(good,6)); assertFalse(UpdateRules.newer(good,7)); assertFalse(UpdateRules.newer(good,8))
        assertFalse(UpdateRules.shouldCheck("2026-10-18","2026-10-18"))
        assertTrue(UpdateRules.shouldCheck("2026-10-19","2026-10-18"))
    }
}
