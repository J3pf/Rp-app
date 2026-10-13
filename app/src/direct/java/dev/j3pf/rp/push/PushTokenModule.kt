package dev.j3pf.rp.push

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Sabor `direct`: sin Google, no hay token FCM. */
@Module
@InstallIn(SingletonComponent::class)
object PushTokenModule {
    @Provides
    @Singleton
    fun tokenProvider(): PushTokenProvider = object : PushTokenProvider {
        override suspend fun currentToken(): String? = null
    }
}
