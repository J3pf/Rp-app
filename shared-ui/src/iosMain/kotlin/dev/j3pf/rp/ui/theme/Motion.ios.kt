package dev.j3pf.rp.ui.theme

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun rememberIsReducedMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()
