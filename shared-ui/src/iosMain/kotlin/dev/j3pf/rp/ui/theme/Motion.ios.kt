package dev.j3pf.rp.ui.theme

import androidx.compose.runtime.Composable
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSProcessInfoThermalState
import platform.Foundation.lowPowerModeEnabled
import platform.Foundation.thermalState
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun rememberIsReducedMotion(): Boolean {
    if (UIAccessibilityIsReduceMotionEnabled()) return true
    val processInfo = NSProcessInfo.processInfo
    if (processInfo.lowPowerModeEnabled) return true
    val thermal = processInfo.thermalState
    return thermal == NSProcessInfoThermalState.NSProcessInfoThermalStateSerious ||
        thermal == NSProcessInfoThermalState.NSProcessInfoThermalStateCritical
}
