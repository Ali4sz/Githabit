package com.githabit.prototype.fixtures

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/** Read-only device check, shared by destructive instrumentation suites. */
internal fun verifyDisposableDevice(): AuthorizedTestTarget {
    val arguments = InstrumentationRegistry.getArguments()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    fun shellText(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }
    val actualAvd = shellText("getprop ro.boot.qemu.avd_name")
    val isEmulator = actualAvd.isNotEmpty() || shellText("getprop ro.kernel.qemu") == "1" || Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
        Build.PRODUCT.startsWith("sdk_gphone") ||
        Build.PRODUCT.contains("emulator", ignoreCase = true) ||
        Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")
    return requireDisposableTarget(
        optedIn = arguments.getString("disposableDevice") == "true",
        isEmulator = isEmulator,
        expectedPackage = arguments.getString("expectedTargetPackage", ""),
        actualPackage = instrumentation.targetContext.packageName,
        testPackage = instrumentation.context.packageName,
        expectedAvd = arguments.getString("expectedAvdName", ""),
        actualAvd = actualAvd,
        expectedToken = arguments.getString("disposableToken", ""),
        actualToken = shellText("cat /data/local/tmp/githabit-disposable-test-target"),
        allowPhysicalDeviceReset = arguments.getString("allowPhysicalDeviceReset") == "true",
        expectedDeviceSerial = arguments.getString("expectedDeviceSerial", ""),
        actualDeviceSerial = if (isEmulator) "" else shellText("getprop ro.serialno"),
    )
}
