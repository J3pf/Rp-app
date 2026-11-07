package dev.j3pf.rp.ui.mesh

import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.MeshRepository
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import org.junit.runner.JUnitCore
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.management.ManagementFactory
import java.net.URL
import java.net.URLClassLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.system.exitProcess
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshSubscriptionLockOrderTest {
    @Test
    fun `concurrent subscriptions do not deadlock across mesh and relay locks`() {
        // Each round gets a fresh JVM: a RED intrinsic-monitor cycle cannot wedge this Gradle executor.
        val rounds = (1..2).map { round -> runFork("probe", round) }
        val failures =
            rounds.mapIndexedNotNull { index, result ->
                if (result.timedOut || result.exitCode != 0 || !result.outputReaderTerminated ||
                    result.output.lineSequence().none { it == "CLEAN" } || result.output.lineSequence().any { it.startsWith("CYCLE ") }
                ) {
                    "round ${index + 1}: $result"
                } else {
                    null
                }
            }
        assertTrue(
            failures.isEmpty(),
            "D-2 regression: both forked rounds must exit 0 with CLEAN and no CYCLE line; ${failures.joinToString("; ")}",
        )
    }

    @Test
    fun `fork timeout forcibly terminates a wedged probe`() {
        val result = runFork("hang", round = 0, timeoutSeconds = 2, waitForReady = true)
        assertTrue(result.timedOut, "the synthetic wedged child should hit the bounded wait: $result")
        assertTrue(result.readyObserved, "the child must reach its deliberate wait before timeout: $result")
        assertFalse(result.aliveAfterTimeout, "destroyForcibly must reap the child: $result")
        assertTrue(result.outputReaderTerminated, "the output reader must also terminate: $result")
        assertTrue(result.output.contains("THREAD_DUMP_BEGIN"), "timeout cleanup must capture jcmd Thread.print first: $result")
    }

    private fun runFork(
        mode: String,
        round: Int,
        timeoutSeconds: Long = FORK_TIMEOUT_SECONDS,
        waitForReady: Boolean = false,
    ): ForkResult {
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val accessArgs =
            ManagementFactory.getRuntimeMXBean().inputArguments.filter {
                it.startsWith("--add-opens=") || it.startsWith("--add-exports=")
            }
        val process =
            ProcessBuilder(
                java,
                *accessArgs.toTypedArray(),
                "-cp",
                appTestClasspath(),
                MeshLockOrderFork::class.java.name,
                mode,
                round.toString(),
            ).redirectErrorStream(true).start()
        val output = StringBuilder()
        val outputLock = Any()
        val ready = CountDownLatch(1)
        val reader =
            Thread({
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(outputLock) { output.append(line).append('\n') }
                        if (line == "HANG_READY") ready.countDown()
                    }
                }
            }, "mesh-lock-order-fork-output-$round")
        reader.isDaemon = true
        reader.start()

        val readyObserved = !waitForReady || ready.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val finished = readyObserved && process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            captureThreadDump(processPid(process), output, outputLock)
            process.destroyForcibly()
            process.waitFor(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        reader.join(CLEANUP_TIMEOUT_SECONDS * 1_000)
        val text = synchronized(outputLock) { output.toString() }
        return ForkResult(
            exitCode = if (finished) process.exitValue() else TIMED_OUT_EXIT_CODE,
            output = text,
            timedOut = !finished,
            readyObserved = readyObserved,
            aliveAfterTimeout = process.isAlive,
            outputReaderTerminated = !reader.isAlive,
        )
    }

    private fun processPid(process: Process): Long =
        (Process::class.java.getMethod("pid").invoke(process) as Number).toLong()

    private fun captureThreadDump(
        pid: Long,
        output: StringBuilder,
        outputLock: Any,
    ) {
        synchronized(outputLock) { output.append("THREAD_DUMP_BEGIN pid=").append(pid).append('\n') }
        val jcmd = File(System.getProperty("java.home"), "bin/jcmd").absolutePath
        val diagnostic = ProcessBuilder(jcmd, pid.toString(), "Thread.print").redirectErrorStream(true).start()
        val diagnosticReader =
            Thread({
                diagnostic.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> synchronized(outputLock) { output.append(line).append('\n') } }
                }
            }, "mesh-lock-order-jcmd-$pid")
        diagnosticReader.isDaemon = true
        diagnosticReader.start()
        val finished = diagnostic.waitFor(DIAGNOSTIC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            diagnostic.destroyForcibly()
            diagnostic.waitFor(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        diagnosticReader.join(CLEANUP_TIMEOUT_SECONDS * 1_000)
        synchronized(outputLock) {
            output.append("THREAD_DUMP_END exit=")
                .append(if (finished) diagnostic.exitValue() else TIMED_OUT_EXIT_CODE)
                .append('\n')
        }
    }

    private fun appTestClasspath(): String {
        val entries = linkedSetOf<String>()
        System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .filter(String::isNotBlank)
            .forEach(entries::add)

        fun addLocation(location: URL?) {
            if (location != null && location.protocol == "file") entries.add(File(location.toURI()).absolutePath)
        }

        listOf(
            MeshSubscriptionLockOrderTest::class.java,
            MeshLockOrderFork::class.java,
            MeshLockOrderForkProbe::class.java,
            JUnitCore::class.java,
            RobolectricTestRunner::class.java,
            MockWebServer::class.java,
            RelayConnection::class.java,
            ApplicationProvider::class.java,
        ).forEach { type -> addLocation(type.protectionDomain.codeSource?.location) }

        var loader: ClassLoader? = Thread.currentThread().contextClassLoader
        while (loader != null) {
            val current = loader
            val urls =
                when (current) {
                    is URLClassLoader -> current.urLs.toList()
                    else -> runCatching {
                        val method = current.javaClass.methods.firstOrNull { it.name == "getURLs" && it.parameterCount == 0 }
                        @Suppress("UNCHECKED_CAST")
                        (method?.invoke(current) as? Array<URL>)?.toList().orEmpty()
                    }.getOrDefault(emptyList())
                }
            urls.forEach(::addLocation)
            loader = current.parent
        }
        return entries.joinToString(File.pathSeparator)
    }

    private data class ForkResult(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean,
        val readyObserved: Boolean,
        val aliveAfterTimeout: Boolean,
        val outputReaderTerminated: Boolean,
    )

    private companion object {
        const val FORK_TIMEOUT_SECONDS = 120L
        const val READY_TIMEOUT_SECONDS = 15L
        const val CLEANUP_TIMEOUT_SECONDS = 5L
        const val DIAGNOSTIC_TIMEOUT_SECONDS = 10L
        const val TIMED_OUT_EXIT_CODE = -1
    }
}

/** Child-JVM entrypoint. The hang mode exercises the parent's bounded wait/forcible cleanup path. */
object MeshLockOrderFork {
    @JvmStatic
    fun main(args: Array<String>) {
        when (args.firstOrNull()) {
            "hang" -> {
                println("HANG_READY")
                System.out.flush()
                CountDownLatch(1).await()
            }

            "probe" -> {
                val result = JUnitCore.runClasses(MeshLockOrderForkProbe::class.java)
                if (!result.wasSuccessful()) {
                    result.failures.forEach { failure -> failure.exception.printStackTrace(System.err) }
                    System.err.flush()
                    exitProcess(2)
                }
                exitProcess(0)
            }

            else -> {
                System.err.println("Expected fork mode 'probe' or 'hang'")
                exitProcess(2)
            }
        }
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeshLockOrderForkProbe {
    @Test
    fun runOneRound() {
        val server = MockWebServer().also { it.start() }
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        var connection: RelayConnection? = null

        try {
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            val secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
            val bus = MessageBus()
            val relayConnection = RelayConnection(secrets, bus, testClient(), scope)
            connection = relayConnection
            val repository = MeshRepository(relayConnection, bus, RelayApi(testClient(), secrets), scope)
            runProbeRound(repository, relayConnection)
        } finally {
            connection?.stop()
            scope.cancel()
            server.shutdown()
            dispatcher.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun runProbeRound(
        repository: MeshRepository,
        connection: RelayConnection,
    ) {
        // D-2: use RelayConnection.socket -> ReconnectingSocket.lock -> RelayLock.monitor,
        // the actual monitor shared by send() and the onOpen state collectors.
        val socket = reflectedField(connection, "socket", "RelayConnection.socket")
        val relayLock = reflectedField(socket, "lock", "ReconnectingSocket.lock")
        val monitor = reflectedField(relayLock, "monitor", "RelayLock.monitor")

        val wHoldsMonitor = CountDownLatch(1)
        val observedT1Blocked = CountDownLatch(1)
        val wEnded = CountDownLatch(1)
        val t1Ended = CountDownLatch(1)
        val owner1Returned = AtomicBoolean(false)
        val owner2Returned = AtomicBoolean(false)
        val threadFailure = AtomicReference<Throwable?>(null)
        val w =
            Thread({
                try {
                    synchronized(monitor) {
                        wHoldsMonitor.countDown()
                        check(observedT1Blocked.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                            "Timed out waiting for T1 to block on the RelayLock monitor"
                        }
                        repository.subscribe(Any(), MACHINE_ID)
                        owner2Returned.set(true)
                    }
                } catch (failure: Throwable) {
                    if (failure !is ThreadDeath) threadFailure.compareAndSet(null, failure)
                } finally {
                    wEnded.countDown()
                }
            }, "ws-thread")
        val t1 =
            Thread({
                try {
                    repository.subscribe(Any(), MACHINE_ID)
                    owner1Returned.set(true)
                } catch (failure: Throwable) {
                    if (failure !is ThreadDeath) threadFailure.compareAndSet(null, failure)
                } finally {
                    t1Ended.countDown()
                }
            }, "t1-thread")
        w.isDaemon = true
        t1.isDaemon = true
        val bean = ManagementFactory.getThreadMXBean()

        w.start()
        if (!wHoldsMonitor.await(WAIT_SECONDS, TimeUnit.SECONDS)) terminate(2, "TIMEOUT W did not take RelayLock.monitor", bean)
        t1.start()
        if (!awaitBlockedBy(bean, t1, w.id)) terminate(2, "TIMEOUT T1 did not block on ws-thread", bean)
        observedT1Blocked.countDown()

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (System.nanoTime() < deadline) {
            val deadlocked = bean.findDeadlockedThreads()?.toSet().orEmpty()
            if (deadlocked.contains(t1.id) && deadlocked.contains(w.id)) {
                terminate(3, "CYCLE t1 w", bean)
            }
            if (wEnded.count == 0L && t1Ended.count == 0L) break
            LockSupport.parkNanos(POLL_NANOS)
        }

        if (wEnded.count != 0L || t1Ended.count != 0L) terminate(2, "TIMEOUT subscriptions did not both return", bean)
        if (threadFailure.get() != null) terminate(2, "TIMEOUT worker failed: ${threadFailure.get()}", bean)
        if (!owner1Returned.get() || !owner2Returned.get()) terminate(2, "TIMEOUT a subscribe call did not return", bean)
        w.join(JOIN_TIMEOUT_MILLIS)
        t1.join(JOIN_TIMEOUT_MILLIS)
        if (w.isAlive || t1.isAlive) terminate(2, "TIMEOUT subscription threads did not terminate", bean)
        println("CLEAN")
        System.out.flush()
    }

    private fun awaitBlockedBy(
        bean: java.lang.management.ThreadMXBean,
        thread: Thread,
        ownerId: Long,
    ): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (System.nanoTime() < deadline && thread.isAlive) {
            val info = bean.getThreadInfo(thread.id, 0)
            if (info?.threadState == Thread.State.BLOCKED && info.lockOwnerId == ownerId) return true
            LockSupport.parkNanos(POLL_NANOS)
        }
        return false
    }

    private fun reflectedField(
        instance: Any,
        fieldName: String,
        qualifiedName: String,
    ): Any {
        try {
            val field = instance.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            return field.get(instance)
                ?: throw AssertionError("D-2 test reflection failed: $qualifiedName was null")
        } catch (failure: Exception) {
            throw AssertionError("D-2 test reflection cannot access required field $qualifiedName", failure)
        }
    }

    private fun terminate(
        exitCode: Int,
        message: String,
        bean: java.lang.management.ThreadMXBean,
    ): Nothing {
        System.err.println(message)
        bean.dumpAllThreads(true, true).forEach(System.err::println)
        System.out.flush()
        System.err.flush()
        exitProcess(exitCode)
    }

    private companion object {
        const val MACHINE_ID = "mesh-lock-order-machine"
        const val WAIT_SECONDS = 15L
        const val JOIN_TIMEOUT_MILLIS = 5_000L
        const val POLL_NANOS = 1_000_000L
    }
}
