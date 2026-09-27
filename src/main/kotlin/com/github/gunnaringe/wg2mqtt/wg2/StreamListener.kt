package com.github.gunnaringe.wg2mqtt.wg2

import com.github.gunnaringe.wg2mqtt.Metrics
import io.grpc.Context
import io.micrometer.core.instrument.Tags
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs a blocking gRPC stream on its own thread, reconnecting after [reconnectDelayMs]
 * whenever it ends or fails, until closed.
 */
abstract class StreamListener(private val name: String) : Closeable {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newSingleThreadExecutor { Thread(it, name) }
    private val context = Context.current().withCancellation()
    private val reconnectDelayMs = 10_000L

    private val connected = Metrics.registry.gauge("wg2.stream.connected", Tags.of("stream", name), AtomicInteger(0))!!
    private val errors = Metrics.counter("wg2.stream.errors", "stream", name)

    /** Counts an event received from the stream, by type (or "ignored"). */
    protected fun countEvent(type: String) = Metrics.counter("wg2.events.received", "stream", name, "type", type).increment()

    /** Blocks while consuming the stream. */
    protected abstract fun stream()

    fun start() {
        executor.submit {
            while (!context.isCancelled) {
                try {
                    logger.info("Subscribing to $name")
                    connected.set(1)
                    context.run { stream() }
                    logger.warn("Stream $name ended")
                } catch (e: Exception) {
                    if (context.isCancelled) break
                    logger.error("Error in stream $name", e)
                    errors.increment()
                } finally {
                    connected.set(0)
                }
                try {
                    Thread.sleep(reconnectDelayMs)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    override fun close() {
        context.cancel(null)
        executor.shutdownNow()
        executor.awaitTermination(10, TimeUnit.SECONDS)
    }
}
