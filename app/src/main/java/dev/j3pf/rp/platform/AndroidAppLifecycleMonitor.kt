package dev.j3pf.rp.platform

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.j3pf.rp.data.ports.AppLifecycleMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** ProcessLifecycleOwner adapter for the shared foreground/background lifecycle port. */
class AndroidAppLifecycleMonitor
    @Inject
    constructor() : AppLifecycleMonitor {
        private val foreground =
            MutableStateFlow(
                ProcessLifecycleOwner
                    .get()
                    .lifecycle
                    .currentState
                    .isAtLeast(Lifecycle.State.STARTED),
            )
        private var onForeground: (() -> Unit)? = null
        private var onBackground: (() -> Unit)? = null

        override val isForeground: StateFlow<Boolean> = foreground

        init {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        foreground.value = true
                        onForeground?.invoke()
                    }

                    override fun onStop(owner: LifecycleOwner) {
                        foreground.value = false
                        onBackground?.invoke()
                    }
                },
            )
        }

        override fun observe(
            onForeground: () -> Unit,
            onBackground: () -> Unit,
        ) {
            this.onForeground = onForeground
            this.onBackground = onBackground
        }
    }
