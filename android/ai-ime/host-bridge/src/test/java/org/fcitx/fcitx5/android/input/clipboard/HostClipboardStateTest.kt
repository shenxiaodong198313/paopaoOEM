package org.fcitx.fcitx5.android.input.clipboard

import org.junit.Assert.*
import org.junit.Test

class HostClipboardStateTest {
    private fun session() = HostClipboardState().apply { startHost("chat.a", true, 100) }

    @Test fun currentHostCopyIsTemporaryAndDismissDoesNotReplay() {
        val state = session()
        state.copied("hello", 101, false, "chat.a")
        assertEquals("hello", state.suggestion?.text)
        state.dismiss()
        state.copied("hello", 101, false, "chat.a")
        assertNull(state.suggestion)
        state.copied("hello", 102, false, "chat.a")
        assertEquals("hello", state.suggestion?.text)
    }

    @Test fun switchingHostsDiscardsOldCopiesEvenAfterReturning() {
        val state = session()
        state.copied("A", 101, false, null)
        state.startHost("chat.b", true, 102)
        assertNull(state.suggestion)
        state.startHost("chat.a", true, 103)
        assertNull(state.suggestion)
        state.copied("delayed B notification", 102, false, null)
        assertNull(state.suggestion)
    }

    @Test fun endingInputCannotAttributeBackgroundCopyToStaleEditor() {
        val state = session()
        state.copied("A", 101, false, null)
        state.finishHost()
        state.copied("B", 102, false, null)
        state.startHost("chat.a", true, 103)
        assertNull(state.suggestion)
    }

    @Test fun sameHostRestartKeepsCopyButKnownOtherForegroundRejectsIt() {
        val state = session()
        state.copied("A", 101, false, null)
        state.startHost("chat.a", true, 102)
        assertEquals("A", state.suggestion?.text)
        state.copied("B", 103, false, "chat.b")
        assertNull(state.suggestion)
    }

    @Test fun privateEditorsSensitiveAndNonTextCopiesClearPreviousSuggestion() {
        val state = session()
        state.copied("A", 101, false, null)
        state.copied("secret", 102, true, null)
        assertNull(state.suggestion)
        state.copied("B", 103, false, null)
        state.copied(null, 104, false, null)
        assertNull(state.suggestion)
        state.startHost("chat.a", false, 105)
        state.copied("password", 106, false, null)
        assertNull(state.suggestion)
    }

    @Test fun hiddenCopyWaitsForFirstDisplayThenNeverReturns() {
        val state = session()
        state.copied("waiting", 101, false, null)
        state.hideKeyboard()
        assertEquals("waiting", state.suggestion?.text)
        state.markShown()
        state.hideKeyboard()
        assertNull(state.suggestion)
        state.startHost("chat.a", true, 102)
        assertNull(state.suggestion)
        state.copied("new copy", 103, false, null)
        assertEquals("new copy", state.suggestion?.text)
    }

    @Test fun typingConsumesTheDisplayedCopy() {
        val state = session()
        state.copied("waiting", 101, false, null)
        state.markShown()
        state.dismiss()
        state.hideKeyboard()
        state.startHost("chat.a", true, 102)
        assertNull(state.suggestion)
    }

    @Test fun neverImportsExistingClipboardOrUnattributedCopy() {
        val state = HostClipboardState()
        state.copied("unknown source", 10, false, null)
        state.startHost("chat.a", true, 100)
        state.copied("old copy delivered later", 99, false, null)
        assertNull(state.suggestion)
    }
}
