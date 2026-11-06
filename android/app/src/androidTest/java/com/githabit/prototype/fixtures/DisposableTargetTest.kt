package com.githabit.prototype.fixtures

import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
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
        allowPhysicalDeviceReset: Boolean = false,
        expectedDeviceSerial: String = "",
        actualDeviceSerial: String = "",
    ) = requireDisposableTarget(optedIn, isEmulator, expectedPackage, actualPackage, testPackage,
        expectedAvd, actualAvd, expectedToken, actualToken, allowPhysicalDeviceReset, expectedDeviceSerial, actualDeviceSerial)

    @Test fun exactProvisionedTargetIsAccepted() { assertEquals(AuthorizedTestTarget.DISPOSABLE_EMULATOR, verify()) }

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

    private fun physical(
        consent: Boolean = true,
        expectedSerial: String = "PHYSICAL-TEST-0001",
        actualSerial: String = "PHYSICAL-TEST-0001",
        token: String = "test-only-token-123456",
        marker: String = "test-only-token-123456",
        optedIn: Boolean = true,
        actualPackage: String = "com.githabit.prototype",
        testPackage: String = "com.githabit.prototype.test",
    ) = verify(optedIn = optedIn, isEmulator = false, expectedAvd = "", actualAvd = "",
        actualPackage = actualPackage, testPackage = testPackage, expectedToken = token, actualToken = marker,
        allowPhysicalDeviceReset = consent, expectedDeviceSerial = expectedSerial, actualDeviceSerial = actualSerial)

    @Test fun explicitlyAuthorizedPhysicalSandboxIsAcceptedWithoutScreenshotPermission() {
        assertEquals(AuthorizedTestTarget.PHYSICAL_APP_SANDBOX, physical())
    }

    @Test fun missingPhysicalConsentIdentityOrMarkerIsRejected() {
        assertThrows(IllegalStateException::class.java) { physical(consent = false) }
        assertThrows(IllegalStateException::class.java) { physical(optedIn = false) }
        assertThrows(IllegalStateException::class.java) { physical(actualSerial = "DIFFERENT-TEST-0002") }
        assertThrows(IllegalStateException::class.java) { physical(actualSerial = "") }
        listOf("", "unknown", "000000000000", "0123456789abcdef", "1234567890", "bad serial value").forEach { invalid ->
            assertThrows(IllegalStateException::class.java) { physical(expectedSerial = invalid, actualSerial = invalid) }
        }
        assertThrows(IllegalStateException::class.java) { physical(token = "", marker = "") }
        assertThrows(IllegalStateException::class.java) { physical(token = "short", marker = "short") }
        assertThrows(IllegalStateException::class.java) { physical(marker = "stale-marker-123456") }
        assertThrows(IllegalStateException::class.java) { physical(actualPackage = "another.app") }
        assertThrows(IllegalStateException::class.java) { physical(testPackage = "another.app.test") }
    }

    @Test fun physicalConsentCannotBypassFailedEmulatorEvidence() {
        assertThrows(IllegalStateException::class.java) {
            verify(expectedAvd = "", actualAvd = "", allowPhysicalDeviceReset = true,
                expectedDeviceSerial = "PHYSICAL-TEST-0001", actualDeviceSerial = "PHYSICAL-TEST-0001")
        }
        assertThrows(IllegalStateException::class.java) {
            verify(isEmulator = false, allowPhysicalDeviceReset = true,
                expectedDeviceSerial = "PHYSICAL-TEST-0001", actualDeviceSerial = "PHYSICAL-TEST-0001")
        }
    }
}
