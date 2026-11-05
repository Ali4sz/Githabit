package com.githabit.prototype.fixtures

import org.junit.Assert.assertThrows
import org.junit.Test

/** Non-destructive checks: these tests never launch the app or open workspace storage. */
class DisposableTargetTest {
    private fun verify(
        optedIn: Boolean = true,
        isEmulator: Boolean = true,
        expectedPackage: String = "com.githabit.prototype",
        actualPackage: String = "com.githabit.prototype",
        testPackage: String = "com.githabit.prototype.test",
        expectedAvd: String = "GitHabit_Disposable_API_36",
        actualAvd: String = "GitHabit_Disposable_API_36",
        expectedToken: String = "test-only-token-123456",
        actualToken: String = "test-only-token-123456",
    ) = requireDisposableTarget(optedIn, isEmulator, expectedPackage, actualPackage, testPackage,
        expectedAvd, actualAvd, expectedToken, actualToken)

    @Test fun exactProvisionedTargetIsAccepted() { verify() }

    @Test fun missingOrWrongIsolationEvidenceIsRejected() {
        assertThrows(IllegalStateException::class.java) { verify(optedIn = false) }
        assertThrows(IllegalStateException::class.java) { verify(isEmulator = false) }
        assertThrows(IllegalStateException::class.java) { verify(expectedPackage = "") }
        assertThrows(IllegalStateException::class.java) { verify(actualPackage = "another.app") }
        assertThrows(IllegalStateException::class.java) { verify(testPackage = "another.app.test") }
        assertThrows(IllegalStateException::class.java) { verify(expectedAvd = "GitHabit_API_36", actualAvd = "GitHabit_API_36") }
        assertThrows(IllegalStateException::class.java) { verify(actualAvd = "GitHabit_Disposable_OTHER") }
        assertThrows(IllegalStateException::class.java) { verify(expectedToken = "", actualToken = "") }
        assertThrows(IllegalStateException::class.java) { verify(actualToken = "stale-marker-123456") }
    }
}
