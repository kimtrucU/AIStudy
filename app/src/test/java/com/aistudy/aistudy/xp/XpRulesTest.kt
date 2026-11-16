package com.aistudy.aistudy.xp

import com.aistudy.aistudy.database.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class XpRulesTest {

    @Test
    fun levelThresholdsAndProgress_matchSpec() {
        assertEquals(1, XpRules.levelFromXp(0))
        assertEquals(1, XpRules.levelFromXp(99))
        assertEquals(2, XpRules.levelFromXp(100))
        assertEquals(3, XpRules.levelFromXp(250))
        assertEquals(4, XpRules.levelFromXp(500))
        assertEquals(5, XpRules.levelFromXp(1000))
        assertEquals(6, XpRules.levelFromXp(2000))

        assertEquals(100, XpRules.nextLevelThreshold(0))
        assertEquals(250, XpRules.nextLevelThreshold(100))
        assertEquals(2000, XpRules.nextLevelThreshold(2500))

        assertEquals(50, XpRules.progressPercentToNext(50))
        assertEquals(100, XpRules.progressPercentToNext(2500))
        assertEquals(50, XpRules.xpToNextLevel(50))
        assertEquals(0, XpRules.xpToNextLevel(2500))
        assertTrue(XpRules.isNearNextLevel(60))
        assertFalse(XpRules.isNearNextLevel(40))
        assertFalse(XpRules.isNearNextLevel(2000))
    }

    @Test
    fun weekResetAndStreakRisk_behaveCorrectly() {
        val profile = Profile().apply {
            weeklyXp = 120
            weekStartMs = 1000L
        }
        XpRules.ensureWeek(profile)
        assertEquals(0, profile.weeklyXp)
        assertEquals(XpRules.currentWeekStartMs(), profile.weekStartMs)

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.time
        profile.streak = 3
        profile.lastActiveDay = fmt.format(yesterday)
        assertTrue(XpRules.streakAtRisk(profile))

        profile.lastActiveDay = XpRules.todayKey()
        assertFalse(XpRules.streakAtRisk(profile))
    }
}

