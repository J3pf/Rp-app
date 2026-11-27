package dev.j3pf.rp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSProcessInfoThermalState
import platform.Foundation.thermalState
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillResignActiveNotification

@Composable
actual fun rememberIsReducedMotion(): Boolean {
    if (UIAccessibilityIsReduceMotionEnabled()) return true
    val thermal = NSProcessInfo.processInfo.thermalState
    return thermal == NSProcessInfoThermalState.NSProcessInfoThermalStateSerious ||
        thermal == NSProcessInfoThermalState.NSProcessInfoThermalStateCritical
}

@Composable
actual fun rememberIsAppForeground(): Boolean {
    var isActive by remember {
        mutableStateOf(UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive)
    }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue
        val activeObs =
            center.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = queue,
            ) { _ ->
                isActive = true
            }
        val resignObs =
            center.addObserverForName(
                name = UIApplicationWillResignActiveNotification,
                `object` = null,
                queue = queue,
            ) { _ ->
                isActive = false
            }
        val bgObs =
            center.addObserverForName(
                name = UIApplicationDidEnterBackgroundNotification,
                `object` = null,
                queue = queue,
            ) { _ ->
                isActive = false
            }
        onDispose {
            center.removeObserver(activeObs)
            center.removeObserver(resignObs)
            center.removeObserver(bgObs)
        }
    }
    return isActive
}
