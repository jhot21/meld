package me.jhot.meld.service

import io.mockk.*
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OverrideSessionStoreTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var store: OverrideSessionStore

    @Before
    fun setup() {
        prefs = mockk()
        editor = mockk()
        every { prefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } just Runs
        store = OverrideSessionStore(prefs)
    }

    @Test
    fun isActive_returnsFalse_whenNotSet() {
        every { prefs.getBoolean("volume_override_active", false) } returns false
        assertFalse(store.isActive())
    }

    @Test
    fun isActive_returnsTrue_whenSet() {
        every { prefs.getBoolean("volume_override_active", false) } returns true
        assertTrue(store.isActive())
    }

    @Test
    fun setActive_storesTrue() {
        store.setActive()
        verify { editor.putBoolean("volume_override_active", true) }
        verify { editor.apply() }
    }

    @Test
    fun clear_storesFalse() {
        store.clear()
        verify { editor.putBoolean("volume_override_active", false) }
        verify { editor.apply() }
    }
}
