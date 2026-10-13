package dev.j3pf.rp.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.j3pf.rp.data.db.RpDatabase
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // WS: sin timeout de lectura; el relay hace ping cada 25 s
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun database(@ApplicationContext ctx: Context): RpDatabase =
        Room.databaseBuilder(ctx, RpDatabase::class.java, "rp.db").build()

    @Provides fun machineDao(db: RpDatabase) = db.machines()
    @Provides fun agentDao(db: RpDatabase) = db.agents()
    @Provides fun promptDao(db: RpDatabase) = db.prompts()
    @Provides fun chatDao(db: RpDatabase) = db.chat()
    @Provides fun outboxDao(db: RpDatabase) = db.outbox()
    @Provides fun dedupDao(db: RpDatabase) = db.dedup()
}
