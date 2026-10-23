package dev.j3pf.rp.ui.newagent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.j3pf.rp.data.repo.HostRepository
import dev.j3pf.rp.data.repo.HostResult
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.protocol.HerdrTarget
import dev.j3pf.rp.protocol.HostSpawn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Flujo "Nuevo agente": PC → carpeta → lanzador (gentle-shell/pi) → dónde (herdr/tmux/wt) → prompt inicial. */
@HiltViewModel
class NewAgentViewModel
    @Inject
    constructor(
        machines: MachinesRepository,
        private val hosts: HostRepository,
    ) : ViewModel() {
        private val form = MutableStateFlow(NewAgentForm())

        val state: StateFlow<NewAgentUi> =
            combine(machines.machines, hosts.hosts, form) { ms, hs, f ->
                NewAgentUi(ms.filter { it.hostOnline }, f.machineId?.let(hs::get), f)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NewAgentUi())

        init {
            combine(machines.machines, hosts.hosts) { ms, hs ->
                val online = ms.filter { it.hostOnline }
                val curId = form.value.machineId
                if (curId == null && online.isNotEmpty()) {
                    pickMachine(online.first().machineId)
                } else if (curId != null && form.value.path.isEmpty()) {
                    val h = hs[curId]
                    if (h != null && h.roots.isNotEmpty()) {
                        val root = h.roots.first()
                        form.update {
                            it.copy(
                                path = root,
                                launcher = it.launcher ?: h.defaultLauncher ?: h.launchers.firstOrNull(),
                                backend = it.backend ?: h.defaultBackend,
                            )
                        }
                        loadDirs(root)
                    }
                }
            }.launchIn(viewModelScope)
        }

        fun pickMachine(id: String) {
            val host = hosts.hosts.value[id]
            val initialPath = host?.roots?.firstOrNull().orEmpty()
            form.value =
                NewAgentForm(
                    machineId = id,
                    path = initialPath,
                    launcher = host?.defaultLauncher ?: host?.launchers?.firstOrNull(),
                    backend = host?.defaultBackend,
                )
            loadDirs(initialPath)
            if (host?.backends?.herdr != null) loadLayout()
        }

        fun openDir(path: String) {
            form.update { it.copy(path = path) }
            loadDirs(path)
        }

        fun enter(child: String) {
            val cur = form.value.path
            val sep = if (cur.contains('\\') && !cur.contains('/')) "\\" else "/"
            openDir(cur.trimEnd('/', '\\') + sep + child)
        }

        fun up() {
            val cur = form.value.path
            val host = state.value.host
            if (host != null && host.roots.any { it.trimEnd('/', '\\') == cur.trimEnd('/', '\\') }) {
                openDir("")
                return
            }
            val idx = maxOf(cur.trimEnd('/', '\\').lastIndexOf('/'), cur.trimEnd('/', '\\').lastIndexOf('\\'))
            if (idx > 0) openDir(cur.substring(0, idx)) else openDir("")
        }

        val canGoUp: Boolean
            get() {
                val cur = form.value.path.trimEnd('/', '\\')
                return cur.isNotEmpty()
            }

        private fun loadDirs(path: String) {
            val machineId = form.value.machineId ?: return
            form.update { it.copy(loadingDirs = true, error = null) }
            viewModelScope.launch {
                when (val r = hosts.listDirs(machineId, path)) {
                    is HostResult.Ok -> form.update { it.copy(dirs = r.value.dirs.sorted(), loadingDirs = false) }
                    is HostResult.Failed -> form.update { it.copy(dirs = emptyList(), loadingDirs = false, error = r.message) }
                }
            }
        }

        fun loadLayout() {
            val machineId = form.value.machineId ?: return
            form.update { it.copy(loadingLayout = true) }
            viewModelScope.launch {
                when (val r = hosts.herdrLayout(machineId)) {
                    is HostResult.Ok -> form.update { it.copy(layout = r.value, loadingLayout = false) }
                    is HostResult.Failed -> form.update { it.copy(loadingLayout = false, error = r.message) }
                }
            }
        }

        fun setLauncher(l: String) = form.update { it.copy(launcher = l) }

        fun setBackend(b: String) = form.update { it.copy(backend = b) }

        fun setPlacement(p: HerdrPlacement) {
            form.update { it.copy(placement = p) }
            if (p != HerdrPlacement.NEW_WORKSPACE) loadLayout()
        }

        fun setWorkspace(id: String) = form.update { it.copy(workspaceId = id) }

        fun setPane(id: String) = form.update { it.copy(paneId = id) }

        fun setDirection(d: String) = form.update { it.copy(direction = d) }

        fun setName(v: String) = form.update { it.copy(name = v) }

        fun setPrompt(v: String) = form.update { it.copy(initialPrompt = v) }

        fun create() {
            val ui = state.value
            val f = ui.form
            val machineId = f.machineId ?: return
            if (!ui.canCreate) return
            val backend = ui.effectiveBackend
            val target =
                if (backend == "herdr") {
                    when (f.placement) {
                        HerdrPlacement.NEW_WORKSPACE -> HerdrTarget("workspace")
                        HerdrPlacement.TAB -> HerdrTarget("tab", workspaceId = f.workspaceId)
                        HerdrPlacement.SPLIT -> HerdrTarget("split", paneId = f.paneId, direction = f.direction)
                    }
                } else {
                    null
                }
            form.update { it.copy(spawning = true, error = null) }
            viewModelScope.launch {
                val r =
                    hosts.spawn(
                        HostSpawn(
                            machineId = machineId,
                            cwd = f.path,
                            name = f.name.trim().ifEmpty { null },
                            launcher = f.launcher,
                            backend = backend,
                            herdrTarget = target,
                            initialPrompt = f.initialPrompt.trim().ifEmpty { null },
                        ),
                    )
                form.update {
                    when (r) {
                        is HostResult.Ok -> it.copy(spawning = false, created = true)
                        is HostResult.Failed -> it.copy(spawning = false, error = r.message)
                    }
                }
            }
        }
    }
