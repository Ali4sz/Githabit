package com.githabit.prototype.fixtures

internal enum class AuthorizedTestTarget { DISPOSABLE_EMULATOR, PHYSICAL_APP_SANDBOX }

/** Test-only authorization, checked before target-context writes or recovery-capable reads. */
internal fun requireDisposableTarget(
    optedIn: Boolean,
    isEmulator: Boolean,
    expectedPackage: String,
    actualPackage: String,
    testPackage: String,
    expectedAvd: String,
    actualAvd: String,
    expectedToken: String,
    actualToken: String,
    allowPhysicalDeviceReset: Boolean = false,
    expectedDeviceSerial: String = "",
    actualDeviceSerial: String = "",
): AuthorizedTestTarget {
    val emulatorAuthorized = isEmulator &&
        expectedAvd.startsWith("GitHabit_Disposable_") && actualAvd == expectedAvd
    val physicalAuthorized = !isEmulator && allowPhysicalDeviceReset &&
        expectedAvd.isEmpty() && actualAvd.isEmpty() &&
        validPhysicalSerial(expectedDeviceSerial) && actualDeviceSerial == expectedDeviceSerial
    check(optedIn && (emulatorAuthorized || physicalAuthorized) &&
        expectedPackage == "com.githabit.prototype" && actualPackage == expectedPackage &&
        testPackage == "$expectedPackage.test" &&
        expectedToken.matches(Regex("[a-zA-Z0-9-]{16,80}")) && actualToken == expectedToken) {
        "Refusing destructive tests: authorize an exact disposable AVD or explicitly consent " +
            "to GitHabit-only resets on an identity-bound physical phone. " +
            "Exact packages, explicit opt-in and matching session marker are required. No target workspace was changed."
    }
    return if (isEmulator) AuthorizedTestTarget.DISPOSABLE_EMULATOR else AuthorizedTestTarget.PHYSICAL_APP_SANDBOX
}

private fun validPhysicalSerial(value: String): Boolean = value.matches(Regex("[a-zA-Z0-9_-]{8,128}")) &&
    value.any { it != '0' } && value.lowercase() !in setOf("unknown", "null", "none", "0123456789abcdef", "1234567890")
