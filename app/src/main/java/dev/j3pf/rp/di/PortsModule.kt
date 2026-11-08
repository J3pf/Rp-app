package dev.j3pf.rp.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.j3pf.rp.data.ports.AgentsGate
import dev.j3pf.rp.data.ports.AppLifecycleMonitor
import dev.j3pf.rp.data.ports.AttachmentsGate
import dev.j3pf.rp.data.ports.BackgroundScheduler
import dev.j3pf.rp.data.ports.BatteryExemption
import dev.j3pf.rp.data.ports.BiometricAuthorizer
import dev.j3pf.rp.data.ports.ChatSessionGate
import dev.j3pf.rp.data.ports.DedupStore
import dev.j3pf.rp.data.ports.HostGateway
import dev.j3pf.rp.data.ports.NetworkMonitor
import dev.j3pf.rp.data.ports.NotificationHealth
import dev.j3pf.rp.data.ports.OpenPromptGuard
import dev.j3pf.rp.data.ports.PlatformInfo
import dev.j3pf.rp.data.ports.PromptSheetGate
import dev.j3pf.rp.data.ports.PushEnvelopeCrypto
import dev.j3pf.rp.data.ports.SettingsStore
import dev.j3pf.rp.data.ports.VoiceInputHost
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.AttachmentsRepository
import dev.j3pf.rp.data.repo.ChatRepository
import dev.j3pf.rp.data.repo.HostRepository
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.platform.AndroidAppLifecycleMonitor
import dev.j3pf.rp.platform.AndroidBatteryExemption
import dev.j3pf.rp.platform.AndroidNetworkMonitor
import dev.j3pf.rp.platform.AndroidNotificationHealth
import dev.j3pf.rp.platform.AndroidPlatformInfo
import dev.j3pf.rp.platform.AndroidPushEnvelopeCrypto
import dev.j3pf.rp.platform.AndroidVoiceInputHost
import dev.j3pf.rp.platform.BiometricAuthorizerAndroid
import dev.j3pf.rp.platform.DaoDedupStore
import dev.j3pf.rp.platform.DaoOpenPromptGuard
import dev.j3pf.rp.platform.WorkManagerBackgroundScheduler
import javax.inject.Singleton
import dev.j3pf.rp.data.ports.SecretStore as SecretStorePort
import dev.j3pf.rp.data.secure.SecretStore as AndroidSecretStore

@Module
@InstallIn(SingletonComponent::class)
abstract class PortsModule {
    @Binds
    @Singleton
    abstract fun bindBackgroundScheduler(impl: WorkManagerBackgroundScheduler): BackgroundScheduler

    @Binds
    @Singleton
    abstract fun bindDedupStore(impl: DaoDedupStore): DedupStore

    @Binds
    @Singleton
    abstract fun bindOpenPromptGuard(impl: DaoOpenPromptGuard): OpenPromptGuard

    @Binds
    @Singleton
    abstract fun bindHostGateway(impl: HostRepository): HostGateway

    @Binds
    @Singleton
    abstract fun bindSettingsStore(impl: SettingsRepository): SettingsStore

    /** Binds Android Keystore/Tink [AndroidSecretStore] to the shared `:data-core` [SecretStorePort] contract. */
    @Binds
    @Singleton
    abstract fun bindSecretStore(impl: AndroidSecretStore): SecretStorePort

    @Binds
    @Singleton
    abstract fun bindPushEnvelopeCrypto(impl: AndroidPushEnvelopeCrypto): PushEnvelopeCrypto

    @Binds
    @Singleton
    abstract fun bindNotificationHealth(impl: AndroidNotificationHealth): NotificationHealth

    @Binds
    @Singleton
    abstract fun bindBatteryExemption(impl: AndroidBatteryExemption): BatteryExemption

    @Binds
    @Singleton
    abstract fun bindPlatformInfo(impl: AndroidPlatformInfo): PlatformInfo

    @Binds
    @Singleton
    abstract fun bindVoiceInputHost(impl: AndroidVoiceInputHost): VoiceInputHost

    @Binds
    abstract fun bindNetworkMonitor(impl: AndroidNetworkMonitor): NetworkMonitor

    @Binds
    @Singleton
    abstract fun bindAppLifecycleMonitor(impl: AndroidAppLifecycleMonitor): AppLifecycleMonitor

    @Binds
    @Singleton
    abstract fun bindBiometricAuthorizer(impl: BiometricAuthorizerAndroid): BiometricAuthorizer

    @Binds
    @Singleton
    abstract fun bindAgentsGate(impl: AgentsRepository): AgentsGate

    @Binds
    @Singleton
    abstract fun bindPromptSheetGate(impl: PromptsRepository): PromptSheetGate

    @Binds
    @Singleton
    abstract fun bindAttachmentsGate(impl: AttachmentsRepository): AttachmentsGate

    @Binds
    @Singleton
    abstract fun bindChatSessionGate(impl: ChatRepository): ChatSessionGate
}
