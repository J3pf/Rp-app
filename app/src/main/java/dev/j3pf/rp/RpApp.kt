package dev.j3pf.rp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.ports.BiometricAuthorizer
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.AttachmentsRepository
import dev.j3pf.rp.data.repo.HostRepository
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.di.AppScope
import dev.j3pf.rp.push.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RpApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var connection: RelayConnection

    @Inject lateinit var machines: MachinesRepository

    @Inject lateinit var agents: AgentsRepository

    @Inject lateinit var prompts: PromptsRepository

    @Inject lateinit var hosts: HostRepository

    @Inject lateinit var attachments: AttachmentsRepository

    @Inject lateinit var channels: NotificationChannels

    @Inject lateinit var biometricAuthorizer: BiometricAuthorizer

    @Inject
    @AppScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        channels.ensureServiceChannel()
        // Los repositorios escuchan el bus de mensajes del relay y mantienen Room al día.
        machines.start()
        agents.start()
        prompts.start()
        hosts.start()
        connection.attach()
        appScope.launch(Dispatchers.IO) {
            attachments.cleanup()
        }
    }
}
