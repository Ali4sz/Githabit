package com.githabit.prototype.fixtures

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
) {
    check(optedIn && isEmulator &&
        expectedPackage == "com.githabit.prototype" && actualPackage == expectedPackage &&
        testPackage == "$expectedPackage.test" &&
        expectedAvd.startsWith("GitHabit_Disposable_") && actualAvd == expectedAvd &&
        expectedToken.matches(Regex("[a-zA-Z0-9-]{16,80}")) && actualToken == expectedToken) {
        "Refusing destructive tests: provision a dedicated GitHabit_Disposable_* AVD, " +
            "verify its package and marker, and supply disposableDevice, expectedTargetPackage, " +
            "expectedAvdName and disposableToken. No target workspace was changed."
    }
}
