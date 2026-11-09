package com.aistudy.aistudy.xp

import com.aistudy.aistudy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockCatalogTest {

    @Test
    fun avatarAndThemeUnlocks_respectLevelThresholds() {
        assertTrue(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_FOX, 1))
        assertFalse(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_OWL, 1))
        assertTrue(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_OWL, 2))
        assertTrue(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_CAT, 3))
        assertFalse(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_ROCKET, 3))
        assertTrue(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_ROCKET, 4))

        assertTrue(UnlockCatalog.isThemeUnlocked(UnlockCatalog.THEME_DEFAULT, 1))
        assertFalse(UnlockCatalog.isThemeUnlocked(UnlockCatalog.THEME_NIGHT, 1))
        assertTrue(UnlockCatalog.isThemeUnlocked(UnlockCatalog.THEME_NIGHT, 2))
        assertFalse(UnlockCatalog.isThemeUnlocked(UnlockCatalog.THEME_PASTEL, 2))
        assertTrue(UnlockCatalog.isThemeUnlocked(UnlockCatalog.THEME_PASTEL, 3))
    }

    @Test
    fun normalizationAndLookup_fallbackToDefaults() {
        assertEquals(UnlockCatalog.AVATAR_FOX, UnlockCatalog.normalizeAvatarId(null))
        assertEquals(UnlockCatalog.AVATAR_FOX, UnlockCatalog.normalizeAvatarId("unknown"))
        assertEquals(UnlockCatalog.AVATAR_CAT, UnlockCatalog.normalizeAvatarId(UnlockCatalog.AVATAR_CAT))

        assertEquals(UnlockCatalog.THEME_DEFAULT, UnlockCatalog.normalizeThemeId(null))
        assertEquals(UnlockCatalog.THEME_DEFAULT, UnlockCatalog.normalizeThemeId("unknown"))
        assertEquals(UnlockCatalog.THEME_NIGHT, UnlockCatalog.normalizeThemeId(UnlockCatalog.THEME_NIGHT))

        assertEquals("🦉", UnlockCatalog.emojiForAvatar(UnlockCatalog.AVATAR_OWL))
        assertEquals("Night", UnlockCatalog.labelForTheme(UnlockCatalog.THEME_NIGHT))
        assertEquals(R.color.theme_night_bg, UnlockCatalog.backgroundColorRes(UnlockCatalog.THEME_NIGHT))
        assertEquals(R.color.theme_pastel_bg, UnlockCatalog.backgroundColorRes(UnlockCatalog.THEME_PASTEL))
        assertEquals(R.color.theme_default_bg, UnlockCatalog.backgroundColorRes(UnlockCatalog.THEME_DEFAULT))
    }
}

