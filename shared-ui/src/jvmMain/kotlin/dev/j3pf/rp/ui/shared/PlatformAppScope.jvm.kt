package dev.j3pf.rp.ui.shared

import kotlinx.coroutines.CoroutineScope

actual fun createPlatformAppController(scope: CoroutineScope): SharedAppController = createInMemorySocketController(scope)
