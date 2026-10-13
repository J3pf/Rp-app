package dev.j3pf.rp

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.push.DirectPushService
import dev.j3pf.rp.ui.MainViewModel
import dev.j3pf.rp.ui.NavTarget
import dev.j3pf.rp.ui.nav.LockScreen
import dev.j3pf.rp.ui.nav.RpRoot
import dev.j3pf.rp.ui.theme.RpTheme
import dev.j3pf.rp.util.AuthResult
import dev.j3pf.rp.util.BiometricGate
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()
    @Inject lateinit var gate: BiometricGate
    @Inject lateinit var secrets: SecretStore
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var connection: RelayConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        // FLAG_SECURE opcional (fase 4): sin capturas ni vista previa en recientes.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                settings.settings.collectLatest { s ->
                    if (s.flagSecure) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            RpTheme {
                val s by vm.settings.collectAsStateWithLifecycle()
                val locked by vm.locked.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                var lockMessage by remember { mutableStateOf<String?>(null) }
                val mustLock = s?.appLock == true && locked && secrets.current() != null

                fun unlock() {
                    scope.launch {
                        when (gate.authenticate(this@MainActivity, "Desbloquear RP", force = true)) {
                            AuthResult.OK -> { lockMessage = null; vm.unlock() }
                            AuthResult.FAILED -> lockMessage = "Desbloqueo cancelado"
                            AuthResult.UNAVAILABLE -> lockMessage = "Configura un bloqueo de pantalla para usar el bloqueo de la app"
                        }
                    }
                }
                LaunchedEffect(mustLock) { if (mustLock) unlock() }

                if (mustLock) LockScreen(onUnlock = ::unlock, message = lockMessage)
                else RpRoot(vm, gate)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onAppStarted()
        // Canal directo: se (re)arranca al abrir la app (el arranque desde segundo plano puede rechazarse).
        lifecycleScope.launch {
            if (secrets.current() != null && settings.current().directPush) DirectPushService.start(this@MainActivity)
        }
        connection.retryNow()
    }

    override fun onStop() {
        vm.onAppStopped()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when {
            intent.action == ACTION_OPEN -> {
                val promptId = intent.getStringExtra(EXTRA_PROMPT_ID)
                val agentId = intent.getStringExtra(EXTRA_AGENT_ID)
                when {
                    promptId != null -> vm.post(NavTarget.Prompt(promptId, agentId))
                    agentId != null -> vm.post(NavTarget.Agent(agentId))
                }
            }
            intent.action == Intent.ACTION_VIEW && intent.data?.scheme == "rp" -> {
                val u = intent.data?.getQueryParameter("u")
                val c = intent.data?.getQueryParameter("c")
                if (u != null && c != null) vm.post(NavTarget.Pair(u, c))
            }
        }
    }

    companion object {
        const val ACTION_OPEN = "dev.j3pf.rp.action.OPEN"
        const val EXTRA_PROMPT_ID = "promptId"
        const val EXTRA_AGENT_ID = "agentId"
    }
}
