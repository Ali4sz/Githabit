package com.githabit.prototype

import androidx.test.platform.app.InstrumentationRegistry
import com.githabit.prototype.data.*
import com.githabit.prototype.fixtures.LongHistoryFixture
import com.githabit.prototype.fixtures.verifyDisposableDevice
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ends before host launches the normal app process; no fixtures or sampler ship in the APK. */
class StandaloneFixturePreparationTest {
    @Test fun prepareLongHistoryForStandaloneReadinessSampling() {
        verifyDisposableDevice()
        val store = WorkspaceStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.load()
        assertEquals(WriteOutcome.Committed, store.replaceVerified(LongHistoryFixture.create()))
    }
}
