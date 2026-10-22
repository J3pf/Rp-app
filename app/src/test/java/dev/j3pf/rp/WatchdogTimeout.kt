package dev.j3pf.rp

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WatchdogTimeout(
    private val timeoutSeconds: Long = 90,
) : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val testThread = Thread.currentThread()
                val scheduler =
                    Executors.newSingleThreadScheduledExecutor { r ->
                        Thread(r, "test-watchdog").apply { isDaemon = true }
                    }
                val timeoutTask =
                    scheduler.schedule({
                        testThread.interrupt()
                    }, timeoutSeconds, TimeUnit.SECONDS)
                try {
                    base.evaluate()
                } finally {
                    timeoutTask.cancel(true)
                    scheduler.shutdownNow()
                    Thread.interrupted()
                }
            }
        }

    companion object {
        fun seconds(seconds: Long = 90): WatchdogTimeout = WatchdogTimeout(seconds)
    }
}
