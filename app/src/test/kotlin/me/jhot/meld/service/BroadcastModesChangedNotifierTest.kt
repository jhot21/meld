package me.jhot.meld.service

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

// Intent is a stub in JVM tests (isReturnDefaultValues), so extras and action cannot be asserted
// here; they are verified on the emulator. These tests pin gating and forwarding only.
class BroadcastModesChangedNotifierTest {

    private val sent = mutableListOf<Pair<Intent, String>>()

    private fun notifier(enabled: Boolean = true) =
        BroadcastModesChangedNotifier(isSharingEnabled = { enabled }, send = { intent, permission -> sent += intent to permission })

    @Test
    fun sendsOnceWithPermissionConstant() {
        notifier().onChanged(listOf("Focus"))
        assertEquals(1, sent.size)
        assertEquals("me.jhot.meld.permission.READ_MODES", sent.single().second)
    }

    @Test
    fun sharingDisabledSendsNothing() {
        notifier(enabled = false).onChanged(listOf("Focus"))
        assertEquals(0, sent.size)
    }

    @Test
    fun emptyListStillSends() {
        notifier().onChanged(emptyList())
        assertEquals(1, sent.size)
    }

    @Test
    fun constantsMatchTheDocumentedContract() {
        assertEquals("meld.intent.MODES_CHANGED", BroadcastModesChangedNotifier.ACTION)
        assertEquals("modes", BroadcastModesChangedNotifier.EXTRA_MODES)
        assertEquals("me.jhot.meld.permission.READ_MODES", BroadcastModesChangedNotifier.PERMISSION)
    }
}
