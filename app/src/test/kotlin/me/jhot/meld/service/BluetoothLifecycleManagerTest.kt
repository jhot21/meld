package me.jhot.meld.service

import android.bluetooth.BluetoothAdapter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothLifecycleManagerTest {

    private class FakeBluetoothToggler : BluetoothToggler {
        val calls = mutableListOf<Boolean>()
        override suspend fun setBluetooth(enable: Boolean): Boolean {
            calls += enable
            return true
        }
    }

    private class FailingBluetoothToggler : BluetoothToggler {
        val calls = mutableListOf<Boolean>()
        override suspend fun setBluetooth(enable: Boolean): Boolean {
            calls += enable
            return false
        }
    }

    private fun makeManager(
        toggler: BluetoothToggler,
        btState: MutableStateFlow<Int>,
        shizuku: MutableStateFlow<Boolean>,
        dispatcher: kotlinx.coroutines.test.TestDispatcher,
        clock: () -> Long = { 0L },
    ): BluetoothLifecycleManager {
        val scope = kotlinx.coroutines.CoroutineScope(dispatcher + kotlinx.coroutines.SupervisorJob())
        return BluetoothLifecycleManager(
            scope = scope,
            toggler = toggler,
            btStateSource = btState,
            shizukuSource = shizuku,
            clock = clock,
        )
    }

    @Test
    fun `no call when BT is transitional`() = runTest(UnconfinedTestDispatcher()) {
        val toggler = FakeBluetoothToggler()
        val btState = MutableStateFlow(BluetoothAdapter.STATE_TURNING_OFF)
        val shizuku = MutableStateFlow(true)
        val manager = makeManager(toggler, btState, shizuku, UnconfinedTestDispatcher())
        manager.setDesired(true)
        assertTrue(toggler.calls.isEmpty())
    }

    @Test
    fun `calls setBluetooth(true) when BT reaches STATE_OFF and desired is true`() =
        runTest(UnconfinedTestDispatcher()) {
            val toggler = FakeBluetoothToggler()
            val btState = MutableStateFlow(BluetoothAdapter.STATE_TURNING_OFF)
            val shizuku = MutableStateFlow(true)
            val manager = makeManager(toggler, btState, shizuku, UnconfinedTestDispatcher())
            manager.setDesired(true)
            btState.value = BluetoothAdapter.STATE_OFF
            assertEquals(listOf(true), toggler.calls)
        }

    @Test
    fun `only final desired value is acted on after multiple rapid setDesired calls`() =
        runTest(UnconfinedTestDispatcher()) {
            val toggler = FakeBluetoothToggler()
            val btState = MutableStateFlow(BluetoothAdapter.STATE_TURNING_OFF)
            val shizuku = MutableStateFlow(true)
            val manager = makeManager(toggler, btState, shizuku, UnconfinedTestDispatcher())
            manager.setDesired(true)
            manager.setDesired(false)
            manager.setDesired(true)
            btState.value = BluetoothAdapter.STATE_OFF
            assertEquals(listOf(true), toggler.calls)
        }

    @Test
    fun `fires immediately when Shizuku becomes available and BT already stable`() =
        runTest(UnconfinedTestDispatcher()) {
            val toggler = FakeBluetoothToggler()
            val btState = MutableStateFlow(BluetoothAdapter.STATE_OFF)
            val shizuku = MutableStateFlow(false)
            val manager = makeManager(toggler, btState, shizuku, UnconfinedTestDispatcher())
            manager.setDesired(true)
            assertTrue(toggler.calls.isEmpty())
            shizuku.value = true
            assertEquals(listOf(true), toggler.calls)
        }

    @Test
    fun `no call when desired is null`() = runTest(UnconfinedTestDispatcher()) {
        val toggler = FakeBluetoothToggler()
        val btState = MutableStateFlow(BluetoothAdapter.STATE_OFF)
        val shizuku = MutableStateFlow(true)
        val manager = makeManager(toggler, btState, shizuku, UnconfinedTestDispatcher())
        manager.setDesired(null)
        assertTrue(toggler.calls.isEmpty())
    }

    @Test
    fun `no call when desired matches current BT state`() = runTest(UnconfinedTestDispatcher()) {
        val toggler = FakeBluetoothToggler()
        val btState = MutableStateFlow(BluetoothAdapter.STATE_ON)
        val shizuku = MutableStateFlow(true)
        val manager = makeManager(toggler, btState, shizuku, UnconfinedTestDispatcher())
        manager.setDesired(true)
        assertTrue(toggler.calls.isEmpty())
    }

    @Test
    fun `desired=null after previous desired clears active intent`() =
        runTest(UnconfinedTestDispatcher()) {
            val toggler = FakeBluetoothToggler()
            val btState = MutableStateFlow(BluetoothAdapter.STATE_TURNING_OFF)
            val shizuku = MutableStateFlow(true)
            val manager = makeManager(toggler, btState, shizuku, UnconfinedTestDispatcher())
            manager.setDesired(true)
            manager.setDesired(null)
            btState.value = BluetoothAdapter.STATE_OFF
            assertTrue(toggler.calls.isEmpty())
        }

    @Test
    fun `same-direction toggle within cooldown is skipped`() =
        runTest(UnconfinedTestDispatcher()) {
            var fakeTime = 0L
            val toggler = FakeBluetoothToggler()
            val btState = MutableStateFlow(BluetoothAdapter.STATE_ON)
            val shizuku = MutableStateFlow(true)
            val manager = makeManager(toggler, btState, shizuku, UnconfinedTestDispatcher(), clock = { fakeTime })

            manager.setDesired(false)
            assertEquals(listOf(false), toggler.calls)

            btState.value = BluetoothAdapter.STATE_TURNING_OFF
            btState.value = BluetoothAdapter.STATE_OFF
            btState.value = BluetoothAdapter.STATE_TURNING_ON
            btState.value = BluetoothAdapter.STATE_ON

            assertEquals(listOf(false), toggler.calls)
        }

    @Test
    fun `same-direction toggle after cooldown expires succeeds`() =
        runTest(UnconfinedTestDispatcher()) {
            var fakeTime = 0L
            val toggler = FakeBluetoothToggler()
            val btState = MutableStateFlow(BluetoothAdapter.STATE_ON)
            val shizuku = MutableStateFlow(true)
            val manager = makeManager(toggler, btState, shizuku, UnconfinedTestDispatcher(), clock = { fakeTime })

            manager.setDesired(false)
            assertEquals(listOf(false), toggler.calls)

            fakeTime = 10_001L

            btState.value = BluetoothAdapter.STATE_TURNING_OFF
            btState.value = BluetoothAdapter.STATE_OFF
            btState.value = BluetoothAdapter.STATE_TURNING_ON
            btState.value = BluetoothAdapter.STATE_ON

            assertEquals(listOf(false, false), toggler.calls)
        }

    @Test
    fun `direction reversal is not throttled by cooldown`() =
        runTest(UnconfinedTestDispatcher()) {
            var fakeTime = 0L
            val toggler = FakeBluetoothToggler()
            val btState = MutableStateFlow(BluetoothAdapter.STATE_OFF)
            val shizuku = MutableStateFlow(true)
            val manager = makeManager(toggler, btState, shizuku, UnconfinedTestDispatcher(), clock = { fakeTime })

            manager.setDesired(true)
            assertEquals(listOf(true), toggler.calls)

            btState.value = BluetoothAdapter.STATE_TURNING_ON
            manager.setDesired(false)
            btState.value = BluetoothAdapter.STATE_ON

            assertEquals(listOf(true, false), toggler.calls)
        }

    @Test
    fun `when setBluetooth returns false retry is allowed after cooldown`() =
        runTest(UnconfinedTestDispatcher()) {
            var fakeTime = 0L
            val toggler = FailingBluetoothToggler()
            val btState = MutableStateFlow(BluetoothAdapter.STATE_OFF)
            val shizuku = MutableStateFlow(true)
            val manager = makeManager(toggler, btState, shizuku, UnconfinedTestDispatcher(), clock = { fakeTime })

            manager.setDesired(true)
            assertEquals(listOf(true), toggler.calls)

            fakeTime = 10_001L

            btState.value = BluetoothAdapter.STATE_TURNING_ON
            btState.value = BluetoothAdapter.STATE_ON
            btState.value = BluetoothAdapter.STATE_TURNING_OFF
            btState.value = BluetoothAdapter.STATE_OFF

            assertEquals(listOf(true, true), toggler.calls)
        }

    @Test
    fun `handles direction reversal mid-transition`() = runTest(UnconfinedTestDispatcher()) {
        val toggler = FakeBluetoothToggler()
        val btState = MutableStateFlow(BluetoothAdapter.STATE_OFF)
        val shizuku = MutableStateFlow(true)
        val manager = makeManager(toggler, btState, shizuku, UnconfinedTestDispatcher())

        manager.setDesired(true)
        assertEquals(listOf(true), toggler.calls)

        btState.value = BluetoothAdapter.STATE_TURNING_ON
        manager.setDesired(false)
        assertEquals(listOf(true), toggler.calls)

        btState.value = BluetoothAdapter.STATE_ON
        assertEquals(listOf(true, false), toggler.calls)
    }

    @Test
    fun `no call when Shizuku not available`() = runTest(UnconfinedTestDispatcher()) {
        val toggler = FakeBluetoothToggler()
        val btState = MutableStateFlow(BluetoothAdapter.STATE_OFF)
        val shizuku = MutableStateFlow(false)
        val manager = makeManager(toggler, btState, shizuku, UnconfinedTestDispatcher())
        manager.setDesired(true)
        assertTrue(toggler.calls.isEmpty())
    }
}
