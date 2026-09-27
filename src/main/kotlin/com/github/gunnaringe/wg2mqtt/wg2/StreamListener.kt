package com.github.gunnaringe.wg2mqtt.wg2

import io.grpc.Context
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs a blocking gRPC stream on its own thread, reconnecting after [reconnectDelayMs]
 * whenever it ends or fails, until closed.
 */
abstract class StreamListener(private val name: String) : Closeable {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newSingleThreadExecutor { Thread(it, name) }
    private val context = Context.current().withCancellation()
    private val reconnectDelayMs = 10_000L

    /** Blocks while consuming the stream. */
    protected abstract fun stream()

    fun start() {
        executor.submit {
            while (!context.isCancelled) {
                try {
                    logger.info("Subscribing to $name")
                    context.run { stream() }
                    logger.warn("Stream $name ended")
                } catch (e: Exception) {
                    if (context.isCancelled) break
                    logger.error("Error in stream $name", e)
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
