package org.fcitx.fcitx5.android.input.favorite

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteMaterialSendGenerationTest {
    @Test fun dismissedDrawerRejectsDelayedCloseCallback() {
        val gate = FavoriteMaterialSendGeneration()
        val old = gate.begin()
        gate.invalidate()
        var cancelled = false
        assertFalse(gate.registerCancellation(old) { cancelled = true })
        assertTrue(cancelled)
        assertFalse(gate.isCurrent(old))
    }

    @Test fun reopeningCancelsPendingRestoreOnce() {
        val gate = FavoriteMaterialSendGeneration()
        val old = gate.begin()
        var cancellations = 0
        assertTrue(gate.registerCancellation(old) { cancellations++ })
        val reopened = gate.begin()
        gate.finish(old)
        assertFalse(gate.isCurrent(old))
        assertTrue(gate.isCurrent(reopened))
        assertEquals(1, cancellations)
    }

    @Test fun oldCompletionCannotRemoveNewGroupsCancellation() {
        val gate = FavoriteMaterialSendGeneration()
        val old = gate.begin()
        var oldCancelled = false
        gate.registerCancellation(old) { oldCancelled = true }
        val next = gate.begin()
        var nextCancelled = false
        gate.registerCancellation(next) { nextCancelled = true }
        gate.finish(old)
        gate.invalidate()
        assertTrue(oldCancelled)
        assertTrue(nextCancelled)
    }

    @Test fun completedWorkerIsNotCancelledByLaterDrawerOpen() {
        val gate = FavoriteMaterialSendGeneration()
        val ticket = gate.begin()
        var cancellations = 0
        gate.registerCancellation(ticket) { cancellations++ }
        gate.finish(ticket)
        gate.begin()
        assertEquals(0, cancellations)
    }

    @Test fun duplicateRegistrationCannotReplaceLiveCancellation() {
        val gate = FavoriteMaterialSendGeneration()
        val ticket = gate.begin()
        var firstCancelled = false
        var duplicateCancelled = false
        assertTrue(gate.registerCancellation(ticket) { firstCancelled = true })
        assertFalse(gate.registerCancellation(ticket) { duplicateCancelled = true })
        assertTrue(duplicateCancelled)
        assertFalse(firstCancelled)
        gate.invalidate()
        assertTrue(firstCancelled)
    }

    @Test fun discardedHostRestoreCannotDispatchAfterNewGroup() = runBlocking {
        val gate = FavoriteMaterialSendGeneration()
        val old = gate.begin()
        val restoring = CompletableDeferred<Unit>()
        val restored = CompletableDeferred<Unit>()
        var oldDispatches = 0
        val oldWork = launch(start = CoroutineStart.LAZY) {
            try {
                restoring.complete(Unit)
                restored.await()
                if (gate.isCurrent(old)) oldDispatches++
            } finally {
                gate.finish(old)
            }
        }
        assertTrue(gate.registerCancellation(old) { oldWork.cancel() })
        oldWork.start()
        restoring.await()
        val next = gate.begin()
        var nextCancelled = false
        assertTrue(gate.registerCancellation(next) { nextCancelled = true })
        restored.complete(Unit)
        oldWork.join()
        assertEquals(0, oldDispatches)
        assertTrue(gate.isCurrent(next))
        gate.invalidate()
        assertTrue(nextCancelled)
    }
}
