package dev.j3pf.rp.ui.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

@Suppress("FunctionName")
fun MainViewController(): UIViewController =
    ComposeUIViewController {
        RemotePiApp()
    }
