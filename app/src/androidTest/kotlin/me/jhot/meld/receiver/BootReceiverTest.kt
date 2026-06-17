package me.jhot.meld.receiver

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import me.jhot.meld.MeldApplication
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that [BootReceiver] starts [me.jhot.meld.service.MeldForegroundService] on boot.
 *
 * Uses mockk-android to mock [Context] so we can verify [Context.startForegroundService]
 * without actually launching the service. goAsync() returns null when called outside the
 * Android broadcast dispatch system — the implementation handles this with a null-safe call.
 */
@RunWith(AndroidJUnit4::class)
class BootReceiverTest {

    @Test
    fun onReceive_bootCompleted_startsforegroundService() {
        val receiver = BootReceiver()
        val context = mockk<Context>(relaxed = true)
        val app = mockk<MeldApplication>(relaxed = true)
        every { context.applicationContext } returns app
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_LOCKED_BOOT_COMPLETED

        receiver.onReceive(context, intent)

        verify { context.startForegroundService(any()) }
    }

    @Test
    fun onReceive_otherAction_doesNotStartService() {
        val receiver = BootReceiver()
        val context = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BOOT_COMPLETED

        receiver.onReceive(context, intent)

        verify(exactly = 0) { context.startForegroundService(any()) }
    }
}
