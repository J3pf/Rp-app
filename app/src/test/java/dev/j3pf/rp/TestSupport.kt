package dev.j3pf.rp

import android.content.Context
import androidx.room.Room
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.data.secure.Session
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** [SecretStore] en memoria: Tink/Keystore no existen en las pruebas JVM. */
class FakeSecretStore(
    context: Context,
    initialSession: Session?,
) : SecretStore(context) {
    init {
        _session.value = initialSession
    }

    override fun current(): Session? = _session.value

    fun set(s: Session?) {
        _session.value = s
    }
}

fun testSession(
    baseUrl: String,
    relayId: String = "r1",
) = Session(
    relayId = relayId,
    relayUrl = baseUrl.trimEnd('/'),
    relayName = "test",
    deviceId = "01DEVICE",
    deviceToken = "tok-123",
    pushKey = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
)

fun inMemoryDb(context: Context): RpDatabase =
    Room.inMemoryDatabaseBuilder(context, RpDatabase::class.java).allowMainThreadQueries().build()

fun testClient(): OkHttpClient =
    OkHttpClient
        .Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()
